package com.myown.damai.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.cache.DamaiCacheKey;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.client.ProgramInventoryClient;
import com.myown.damai.order.client.ProgramInventoryItemRequest;
import com.myown.damai.order.client.ProgramInventoryRequest;
import com.myown.damai.order.client.ProgramOrderSnapshot;
import com.myown.damai.order.client.ProgramTicketPriceSnapshot;
import com.myown.damai.order.client.TicketUserClient;
import com.myown.damai.order.dao.OrderAsyncMessageDao;
import com.myown.damai.order.dao.OrderDao;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import com.myown.damai.order.dto.OrderAsyncMessageResponse;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderCursorPageResponse;
import com.myown.damai.order.dto.OrderPayRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.dto.OrderTicketUserResponse;
import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import com.myown.damai.order.entity.OrderStatus;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.lock.OrderLockExecutor;
import com.myown.damai.order.messaging.OrderAsyncProducer;
import com.myown.damai.order.state.OrderStateMachine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Handles order creation, query, cancellation, and timeout cancellation workflows.
 */
@Service
public class OrderService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_OFFSET_PAGE_NUMBER = 100;
    private static final int EXPIRED_SCAN_LIMIT = 200;
    private static final String ASYNC_MESSAGE_KEY_PREFIX = "order-create:";
    private static final String DEFAULT_DISTRIBUTION_MODE = "ELECTRONIC_TICKET";
    private static final String DEFAULT_TAKE_TICKET_MODE = "ONLINE";
    private static final int DEFAULT_PAY_ORDER_TYPE = 1;
    private static final List<Integer> IDEMPOTENT_ORDER_STATUSES = Arrays.asList(
            OrderStatus.PENDING_PAYMENT.code(),
            OrderStatus.PAID.code()
    );
    private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    private final OrderDao orderDao;
    private final OrderAsyncMessageDao orderAsyncMessageDao;
    private final OrderLockExecutor orderLockExecutor;
    private final OrderStateMachine orderStateMachine;
    private final TransactionTemplate transactionTemplate;
    private final ProgramInventoryClient programInventoryClient;
    private final TicketUserClient ticketUserClient;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<OrderAsyncProducer> orderAsyncProducerProvider;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final Map<String, Long> localPendingOrders = new ConcurrentHashMap<>();
    private final AtomicBoolean localTimeoutScanRunning = new AtomicBoolean(false);
    private final Duration orderTimeout;
    private final Duration timeoutScanLockWaitTime;
    private final Duration timeoutScanLockLeaseTime;
    private final boolean asyncEnabled;
    private final boolean inventoryEnabled;
    private final boolean timeoutScanEnabled;
    private final int asyncMaxRetryCount;
    private final String asyncCreateTopic;

    /**
     * Creates the order service with persistence and timeout settings.
     */
    public OrderService(
            OrderDao orderDao,
            OrderAsyncMessageDao orderAsyncMessageDao,
            OrderLockExecutor orderLockExecutor,
            OrderStateMachine orderStateMachine,
            TransactionTemplate transactionTemplate,
            ProgramInventoryClient programInventoryClient,
            TicketUserClient ticketUserClient,
            ObjectMapper objectMapper,
            ObjectProvider<OrderAsyncProducer> orderAsyncProducerProvider,
            ObjectProvider<RedissonClient> redissonClientProvider,
            @Value("${damai.order.timeout-minutes:15}") long timeoutMinutes,
            @Value("${damai.order.timeout-scan-lock.wait-seconds:1}") long timeoutScanLockWaitSeconds,
            @Value("${damai.order.timeout-scan-lock.lease-seconds:120}") long timeoutScanLockLeaseSeconds,
            @Value("${damai.order.async.enabled:false}") boolean asyncEnabled,
            @Value("${damai.order.inventory.enabled:true}") boolean inventoryEnabled,
            @Value("${damai.order.timeout-scan-enabled:true}") boolean timeoutScanEnabled,
            @Value("${damai.order.async.max-retry-count:3}") int asyncMaxRetryCount,
            @Value("${damai.order.async.create-topic:damai-order-create}") String asyncCreateTopic
    ) {
        this.orderDao = orderDao;
        this.orderAsyncMessageDao = orderAsyncMessageDao;
        this.orderLockExecutor = orderLockExecutor;
        this.orderStateMachine = orderStateMachine;
        this.transactionTemplate = transactionTemplate;
        this.programInventoryClient = programInventoryClient;
        this.ticketUserClient = ticketUserClient;
        this.objectMapper = objectMapper;
        this.orderAsyncProducerProvider = orderAsyncProducerProvider;
        this.redissonClientProvider = redissonClientProvider;
        this.orderTimeout = Duration.ofMinutes(timeoutMinutes);
        this.timeoutScanLockWaitTime = Duration.ofSeconds(timeoutScanLockWaitSeconds);
        this.timeoutScanLockLeaseTime = Duration.ofSeconds(timeoutScanLockLeaseSeconds);
        this.asyncEnabled = asyncEnabled;
        this.inventoryEnabled = inventoryEnabled;
        this.timeoutScanEnabled = timeoutScanEnabled;
        this.asyncMaxRetryCount = asyncMaxRetryCount;
        this.asyncCreateTopic = asyncCreateTopic;
    }

    /**
     * Creates one unpaid order with idempotency and a program-level distributed lock.
     */
    public OrderResponse createOrder(OrderCreateRequest request) {
        validateAuthenticatedUserId(request.userId());
        Optional<Order> existingOrder = findExistingOrder(request.userId(), request.programId());
        if (existingOrder.isPresent()) {
            LOGGER.info(
                    "order create idempotent hit before lock, userId={}, programId={}, orderNumber={}",
                    request.userId(),
                    request.programId(),
                    existingOrder.get().orderNumber
            );
            return buildOrderResponse(existingOrder.get());
        }

        if (asyncEnabled) {
            return submitOrderAsync(request);
        }

        return orderLockExecutor.executeWithProgramLock(request.programId(), () -> {
            // Start the database transaction only after the program lock is held, so the lock-protected idempotency check sees latest committed data.
            OrderResponse response = transactionTemplate.execute(status -> createOrderWithinLock(request));
            return Objects.requireNonNull(response, "created order response must not be null");
        });
    }

    /**
     * Creates one order from a Kafka asynchronous order creation message.
     */
    public OrderResponse createOrderFromAsyncMessage(OrderAsyncCreateMessage message) {
        validateAuthenticatedUserId(message.request().userId());
        return orderLockExecutor.executeWithProgramLock(message.request().programId(), () -> {
            OrderResponse response = transactionTemplate.execute(status -> createOrderWithinLock(
                    message.request(),
                    message.orderNumber(),
                    message.programSnapshot()
            ));
            removePendingOrder(message.request().userId(), message.request().programId(), message.orderNumber());
            return Objects.requireNonNull(response, "created async order response must not be null");
        });
    }

    /**
     * Clears the pending marker for one exhausted asynchronous order creation message.
     */
    public void clearAsyncPendingOrder(OrderAsyncCreateMessage message) {
        removePendingOrder(message.request().userId(), message.request().programId(), message.orderNumber());
        LOGGER.info("order async pending marker cleared, messageKey={}, orderNumber={}", message.messageKey(), message.orderNumber());
    }

    /**
     * Submits one order creation request to Kafka and returns its reserved order number.
     */
    private OrderResponse submitOrderAsync(OrderCreateRequest request) {
        Optional<Order> existingOrder = findExistingOrder(request.userId(), request.programId());
        if (existingOrder.isPresent()) {
            LOGGER.info(
                    "order async create idempotent hit before submit, userId={}, programId={}, orderNumber={}",
                    request.userId(),
                    request.programId(),
                    existingOrder.get().orderNumber
            );
            return buildOrderResponse(existingOrder.get());
        }
        ResolvedOrderDraft draft = resolveOrderDraft(request, null);
        Optional<Long> pendingOrderNumber = findPendingOrderNumber(request.userId(), request.programId());
        if (pendingOrderNumber.isPresent()) {
            LOGGER.info(
                    "order async create pending hit before submit, userId={}, programId={}, orderNumber={}",
                    request.userId(),
                    request.programId(),
                    pendingOrderNumber.get()
            );
            return buildSubmittedOrderResponse(draft, pendingOrderNumber.get());
        }

        Long orderNumber = nextOrderNumber();
        Optional<Long> existingPendingOrderNumber = putPendingOrderIfAbsent(request.userId(), request.programId(), orderNumber);
        if (existingPendingOrderNumber.isPresent()) {
            LOGGER.info(
                    "order async create pending hit during submit, userId={}, programId={}, orderNumber={}",
                    request.userId(),
                    request.programId(),
                    existingPendingOrderNumber.get()
            );
            return buildSubmittedOrderResponse(draft, existingPendingOrderNumber.get());
        }

        String messageKey = asyncMessageKey(orderNumber);
        OrderAsyncCreateMessage message = new OrderAsyncCreateMessage(
                messageKey,
                orderNumber,
                request,
                draft.programSnapshot()
        );
        OrderAsyncMessage asyncMessage = buildAsyncMessage(message);
        try {
            orderAsyncMessageDao.saveMessage(asyncMessage);
            orderAsyncProducerProvider.getObject().sendCreateOrderMessage(message);
            orderAsyncMessageDao.markSent(messageKey, asyncMessage.topic);
            LOGGER.info("order async create submitted, messageKey={}, orderNumber={}, userId={}, programId={}", messageKey, orderNumber, request.userId(), request.programId());
            return buildSubmittedOrderResponse(draft, orderNumber);
        } catch (RuntimeException exception) {
            markAsyncSendFailedQuietly(messageKey, exception);
            removePendingOrder(request.userId(), request.programId(), orderNumber);
            throw exception;
        }
    }

    /**
     * Creates one order after the distributed lock has been acquired.
     */
    private OrderResponse createOrderWithinLock(OrderCreateRequest request) {
        return createOrderWithinLock(request, null, null);
    }

    /**
     * Creates one order after the distributed lock has been acquired with an optional reserved order number.
     */
    private OrderResponse createOrderWithinLock(OrderCreateRequest request, Long reservedOrderNumber) {
        return createOrderWithinLock(request, reservedOrderNumber, null);
    }

    /**
     * Creates one order from identifiers and a trusted program snapshot under the program lock.
     */
    private OrderResponse createOrderWithinLock(
            OrderCreateRequest request,
            Long reservedOrderNumber,
            ProgramOrderSnapshot trustedSnapshot
    ) {
        Optional<Order> existingOrder = findExistingOrder(request.userId(), request.programId());
        if (existingOrder.isPresent()) {
            LOGGER.info(
                    "order create idempotent hit inside lock, userId={}, programId={}, orderNumber={}",
                    request.userId(),
                    request.programId(),
                    existingOrder.get().orderNumber
            );
            return buildOrderResponse(existingOrder.get());
        }

        Long orderNumber = reservedOrderNumber == null ? nextOrderNumber() : reservedOrderNumber;
        ResolvedOrderDraft draft = resolveOrderDraft(request, trustedSnapshot);
        reserveInventory(request, orderNumber);
        try {
            // TODO: Match seats more intelligently when the user does not choose explicit seats.
            Instant now = Instant.now();
            Order order = buildOrder(draft, orderNumber, now);
            Order savedOrder = orderDao.saveOrder(order);
            List<OrderTicketUser> ticketUsers = request.ticketUserIds()
                    .stream()
                    .map(ticketUserId -> buildTicketUser(draft, ticketUserId, orderNumber, now))
                    .toList();
            orderDao.saveTicketUsers(ticketUsers);
            LOGGER.info(
                    "order created from trusted program snapshot, orderNumber={}, userId={}, orderPrice={}",
                    orderNumber,
                    request.userId(),
                    draft.orderPrice()
            );
            return getOrder(savedOrder.orderNumber);
        } catch (RuntimeException exception) {
            releaseInventory(request, orderNumber);
            throw exception;
        }
    }

    /**
     * Finds an existing user-program order that should make order creation idempotent.
     */
    private Optional<Order> findExistingOrder(Long userId, Long programId) {
        return orderDao.findExistingUserProgramOrder(userId, programId, IDEMPOTENT_ORDER_STATUSES);
    }

    /**
     * Gets asynchronous order creation message tracking data by order number.
     */
    @Transactional(readOnly = true)
    public OrderAsyncMessageResponse getAsyncMessage(Long orderNumber) {
        OrderAsyncMessage message = orderAsyncMessageDao.findLatestByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException("ORDER_ASYNC_MESSAGE_NOT_FOUND", "order async message not found", HttpStatus.NOT_FOUND));
        return OrderAsyncMessageResponse.from(message);
    }

    /**
     * Gets asynchronous order creation message tracking data after verifying order ownership.
     */
    @Transactional(readOnly = true)
    public OrderAsyncMessageResponse getAsyncMessageForUser(Long orderNumber, Long userId) {
        OrderAsyncMessage message = orderAsyncMessageDao.findLatestByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException("ORDER_ASYNC_MESSAGE_NOT_FOUND", "order async message not found", HttpStatus.NOT_FOUND));
        verifyUserOwnsResource(message.userId, userId);
        return OrderAsyncMessageResponse.from(message);
    }

    /**
     * Builds the unique asynchronous message key for one order number.
     */
    private String asyncMessageKey(Long orderNumber) {
        return ASYNC_MESSAGE_KEY_PREFIX + orderNumber;
    }

    /**
     * Builds the asynchronous message ledger row for a submitted order creation request.
     */
    private OrderAsyncMessage buildAsyncMessage(OrderAsyncCreateMessage message) {
        Instant now = Instant.now();
        OrderAsyncMessage asyncMessage = new OrderAsyncMessage();
        asyncMessage.messageKey = message.messageKey();
        asyncMessage.orderNumber = message.orderNumber();
        asyncMessage.userId = message.request().userId();
        asyncMessage.programId = message.request().programId();
        asyncMessage.topic = asyncCreateTopic;
        asyncMessage.retryCount = 0;
        asyncMessage.maxRetryCount = asyncMaxRetryCount;
        asyncMessage.messageStatus = OrderAsyncMessageStatus.INIT.code();
        asyncMessage.payload = serializeAsyncMessage(message);
        asyncMessage.createdAt = now;
        asyncMessage.updatedAt = now;
        asyncMessage.status = 1;
        return asyncMessage;
    }

    /**
     * Serializes one asynchronous order creation message for tracking.
     */
    private String serializeAsyncMessage(OrderAsyncCreateMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ORDER_ASYNC_MESSAGE_INVALID", "order async message serialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Reserves inventory for a new order when inventory integration is enabled.
     */
    private void reserveInventory(OrderCreateRequest request, Long orderNumber) {
        if (!inventoryEnabled) {
            return;
        }
        ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromCreateItems(orderNumber, request);
        programInventoryClient.lockInventory(request.programId(), inventoryRequest);
    }

    /**
     * Releases inventory for a request that failed after inventory was reserved.
     */
    private void releaseInventory(OrderCreateRequest request, Long orderNumber) {
        if (!inventoryEnabled) {
            return;
        }
        try {
            ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromCreateItems(orderNumber, request);
            programInventoryClient.releaseInventory(request.programId(), inventoryRequest);
        } catch (RuntimeException exception) {
            LOGGER.warn("order inventory rollback failed, orderNumber={}, programId={}", orderNumber, request.programId(), exception);
        }
    }

    /**
     * Releases locked inventory for a persisted order.
     */
    private void releaseLockedInventory(Order order, List<OrderTicketUser> ticketUsers) {
        if (!inventoryEnabled) {
            return;
        }
        ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromPersistedItems(order.orderNumber, ticketUsers);
        programInventoryClient.releaseInventory(order.programId, inventoryRequest);
    }

    /**
     * Marks locked inventory as sold for a persisted paid order.
     */
    private void markLockedInventorySold(Order order, List<OrderTicketUser> ticketUsers) {
        if (!inventoryEnabled) {
            return;
        }
        ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromPersistedItems(order.orderNumber, ticketUsers);
        programInventoryClient.markInventorySold(order.programId, inventoryRequest);
    }

    /**
     * Builds an inventory request from order creation ticket-user rows.
     */
    private ProgramInventoryRequest buildInventoryRequestFromCreateItems(
            Long orderNumber,
            OrderCreateRequest request
    ) {
        List<ProgramInventoryItemRequest> items = request.ticketUserIds().stream()
                .map(ticketUserId -> new ProgramInventoryItemRequest(request.ticketCategoryId(), null))
                .toList();
        return new ProgramInventoryRequest(orderNumber, items);
    }

    /**
     * Builds an inventory request from persisted order ticket-user rows.
     */
    private ProgramInventoryRequest buildInventoryRequestFromPersistedItems(Long orderNumber, List<OrderTicketUser> ticketUsers) {
        List<ProgramInventoryItemRequest> items = ticketUsers.stream()
                .map(item -> new ProgramInventoryItemRequest(item.ticketCategoryId, item.seatId))
                .toList();
        return new ProgramInventoryRequest(orderNumber, items);
    }

    /**
     * Finds an asynchronous pending order number for one user and program.
     */
    private Optional<Long> findPendingOrderNumber(Long userId, Long programId) {
        String key = pendingOrderKey(userId, programId);
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            return Optional.ofNullable(redissonClient.<Long>getBucket(key).get());
        }
        return Optional.ofNullable(localPendingOrders.get(key));
    }

    /**
     * Stores an asynchronous pending order marker and returns an existing marker when one wins the race.
     */
    private Optional<Long> putPendingOrderIfAbsent(Long userId, Long programId, Long orderNumber) {
        String key = pendingOrderKey(userId, programId);
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            RBucket<Long> bucket = redissonClient.getBucket(key);
            boolean stored = bucket.trySet(orderNumber, orderTimeout.plusMinutes(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!stored) {
                return Optional.ofNullable(bucket.get());
            }
            return Optional.empty();
        }
        Long existingOrderNumber = localPendingOrders.putIfAbsent(key, orderNumber);
        return Optional.ofNullable(existingOrderNumber);
    }

    /**
     * Removes an asynchronous pending order marker when the marker matches the order number.
     */
    private void removePendingOrder(Long userId, Long programId, Long orderNumber) {
        String key = pendingOrderKey(userId, programId);
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            RBucket<Long> bucket = redissonClient.getBucket(key);
            Long currentOrderNumber = bucket.get();
            if (Objects.equals(currentOrderNumber, orderNumber)) {
                bucket.delete();
            }
            return;
        }
        localPendingOrders.remove(key, orderNumber);
    }

    /**
     * Builds the pending marker key for one user and program pair.
     */
    private String pendingOrderKey(Long userId, Long programId) {
        return DamaiCacheKey.of("order", "async-pending", "user", userId, "program", programId);
    }

    /**
     * Marks send failure without hiding the original Kafka submission error.
     */
    private void markAsyncSendFailedQuietly(String messageKey, RuntimeException exception) {
        try {
            orderAsyncMessageDao.markSendFailed(messageKey, exception.getMessage());
        } catch (RuntimeException markException) {
            LOGGER.warn("order async send-failed status update failed, messageKey={}", messageKey, markException);
        }
    }

    /**
     * Builds the immediate response returned after an asynchronous order submission.
     */
    private OrderResponse buildSubmittedOrderResponse(ResolvedOrderDraft draft, Long orderNumber) {
        Instant now = Instant.now();
        Order order = buildOrder(draft, orderNumber, now);
        order.orderStatus = OrderStatus.PENDING_CREATE.code();
        List<OrderTicketUserResponse> ticketUsers = draft.request().ticketUserIds()
                .stream()
                .map(ticketUserId -> {
                    OrderTicketUser ticketUser = buildTicketUser(draft, ticketUserId, orderNumber, now);
                    ticketUser.orderStatus = OrderStatus.PENDING_CREATE.code();
                    return OrderTicketUserResponse.from(ticketUser);
                })
                .toList();
        return OrderResponse.of(order, ticketUsers);
    }

    /**
     * Gets one order detail by order number.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(Long orderNumber) {
        Order order = findOrderOrThrow(orderNumber);
        return buildOrderResponse(order);
    }

    /**
     * Gets one order detail after verifying it belongs to the authenticated user.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrderForUser(Long orderNumber, Long userId) {
        Order order = findOrderOrThrow(orderNumber);
        verifyUserOwnsResource(order.userId, userId);
        return buildOrderResponse(order);
    }

    /**
     * Lists orders for one user with pagination.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(Long userId, int pageNumber, int pageSize) {
        int normalizedPageNumber = Math.max(pageNumber, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        if (normalizedPageNumber > MAX_OFFSET_PAGE_NUMBER) {
            throw new BusinessException("ORDER_DEEP_PAGE_NOT_ALLOWED", "order offset page is too deep, use cursor pagination", HttpStatus.BAD_REQUEST);
        }
        int offset = (normalizedPageNumber - 1) * normalizedPageSize;
        return orderDao.listOrdersByUserId(userId, normalizedPageSize, offset)
                .stream()
                .map(this::buildOrderResponse)
                .toList();
    }

    /**
     * Lists orders for one user with create-time and id cursor pagination.
     */
    @Transactional(readOnly = true)
    public OrderCursorPageResponse listOrdersByCursor(Long userId, Instant cursorCreateTime, Long cursorId, int pageSize) {
        validateCursorPair(cursorCreateTime, cursorId);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        List<Order> fetchedOrders = orderDao.listOrdersByUserIdWithCursor(userId, cursorCreateTime, cursorId, normalizedPageSize + 1);
        boolean hasMore = fetchedOrders.size() > normalizedPageSize;
        List<Order> currentPageOrders = hasMore ? fetchedOrders.subList(0, normalizedPageSize) : fetchedOrders;
        List<OrderResponse> responses = currentPageOrders.stream()
                .map(this::buildOrderResponse)
                .toList();
        Order nextCursorOrder = hasMore ? currentPageOrders.get(currentPageOrders.size() - 1) : null;
        return OrderCursorPageResponse.of(
                responses,
                nextCursorOrder == null ? null : nextCursorOrder.createOrderTime,
                nextCursorOrder == null ? null : nextCursorOrder.id,
                hasMore
        );
    }

    /**
     * Validates that cursor fields are supplied together.
     */
    private void validateCursorPair(Instant cursorCreateTime, Long cursorId) {
        if ((cursorCreateTime == null) != (cursorId == null)) {
            throw new BusinessException("ORDER_CURSOR_INVALID", "cursorCreateTime and cursorId must be provided together", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Cancels one unpaid order.
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderNumber) {
        Order order = findOrderOrThrow(orderNumber);
        List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
        Instant now = Instant.now();
        orderStateMachine.cancel(order, now);
        releaseLockedInventory(order, ticketUsers);
        LOGGER.info("order canceled, orderNumber={}", orderNumber);
        return getOrder(orderNumber);
    }

    /**
     * Cancels one unpaid order after verifying it belongs to the authenticated user.
     */
    @Transactional
    public OrderResponse cancelOrderForUser(Long orderNumber, Long userId) {
        Order order = findOrderOrThrow(orderNumber);
        verifyUserOwnsResource(order.userId, userId);
        return cancelOrder(orderNumber);
    }

    /**
     * Marks one unpaid order as paid after payment provider confirmation.
     */
    @Transactional
    public OrderResponse markOrderPaid(Long orderNumber, OrderPayRequest request) {
        Order order = findOrderOrThrow(orderNumber);
        OrderStatus currentStatus = OrderStatus.fromCode(order.orderStatus);
        if (orderStateMachine.alreadyInStatus(order, OrderStatus.PAID)) {
            LOGGER.info("order pay confirmation ignored because order already paid, orderNumber={}", orderNumber);
            return getOrder(orderNumber);
        }
        if (!orderStateMachine.canTransit(currentStatus, OrderStatus.PAID)) {
            LOGGER.warn("order pay confirmation rejected because order is not unpaid, orderNumber={}, status={}", orderNumber, order.orderStatus);
            throw new BusinessException("ORDER_STATUS_NOT_PAYABLE", "only unpaid orders can be paid", HttpStatus.CONFLICT);
        }
        if (request.payAmount().compareTo(order.orderPrice) != 0) {
            LOGGER.warn("order pay confirmation rejected because amount mismatch, orderNumber={}, orderPrice={}, payAmount={}", orderNumber, order.orderPrice, request.payAmount());
            throw new BusinessException("ORDER_PAY_AMOUNT_MISMATCH", "pay amount does not match order amount", HttpStatus.CONFLICT);
        }
        List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
        Instant payTime = request.payTime() == null ? Instant.now() : request.payTime();
        orderStateMachine.pay(order, payTime);
        markLockedInventorySold(order, ticketUsers);
        LOGGER.info("order marked paid, orderNumber={}, tradeNumber={}", orderNumber, request.tradeNumber());
        return getOrder(orderNumber);
    }

    /**
     * Periodically cancels unpaid orders whose expire time has passed.
     */
    @Scheduled(fixedDelayString = "${damai.order.timeout-scan-delay-millis:60000}")
    public void scheduledCancelTimeoutOrders() {
        if (!timeoutScanEnabled) {
            return;
        }
        int canceledCount = cancelTimeoutOrders();
        if (canceledCount > 0) {
            LOGGER.info("timeout order scan canceled orders, count={}", canceledCount);
        }
    }

    /**
     * Cancels expired unpaid orders and returns the number of canceled orders.
     */
    public int cancelTimeoutOrders() {
        return cancelTimeoutOrdersWithExecutionGuard();
    }

    /**
     * Runs timeout cancellation after acquiring a Redisson distributed lock or a local fallback guard.
     */
    private int cancelTimeoutOrdersWithExecutionGuard() {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            return cancelTimeoutOrdersWithLocalGuard();
        }
        String lockKey = DamaiCacheKey.lock("order", "timeout-scan");
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(timeoutScanLockWaitTime.toMillis(), timeoutScanLockLeaseTime.toMillis(), TimeUnit.MILLISECONDS);
            if (!locked) {
                LOGGER.info("timeout order scan skipped because another instance is running, lockKey={}", lockKey);
                return 0;
            }
            LOGGER.info("timeout order scan lock acquired, lockKey={}", lockKey);
            return executeTimeoutCancelTransaction();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("timeout order scan lock interrupted, lockKey={}", lockKey, exception);
            return 0;
        } catch (RuntimeException exception) {
            LOGGER.warn("timeout order scan skipped because distributed lock failed, lockKey={}", lockKey, exception);
            return 0;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                LOGGER.info("timeout order scan lock released, lockKey={}", lockKey);
            }
        }
    }

    /**
     * Runs timeout cancellation with a local guard when Redisson is disabled or unavailable.
     */
    private int cancelTimeoutOrdersWithLocalGuard() {
        if (!localTimeoutScanRunning.compareAndSet(false, true)) {
            LOGGER.info("timeout order scan skipped because this instance is already running");
            return 0;
        }
        try {
            return executeTimeoutCancelTransaction();
        } finally {
            localTimeoutScanRunning.set(false);
        }
    }

    /**
     * Executes timeout cancellation in a database transaction while the scan guard is still held.
     */
    private int executeTimeoutCancelTransaction() {
        Integer canceledCount = transactionTemplate.execute(status -> cancelExpiredUnpaidOrders());
        return canceledCount == null ? 0 : canceledCount;
    }

    /**
     * Cancels expired unpaid orders inside the caller-managed transaction.
     */
    private int cancelExpiredUnpaidOrders() {
        Instant now = Instant.now();
        List<Long> expiredOrderNumbers = orderDao.listExpiredUnpaidOrderNumbers(now, OrderStatus.PENDING_PAYMENT.code(), EXPIRED_SCAN_LIMIT);
        int canceledCount = 0;
        for (Long orderNumber : expiredOrderNumbers) {
            Optional<Order> foundOrder = orderDao.findOrderByOrderNumber(orderNumber);
            if (foundOrder.isPresent()) {
                Order order = foundOrder.get();
                List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
                boolean timeout = orderStateMachine.timeout(order, now);
                if (timeout) {
                    releaseLockedInventory(order, ticketUsers);
                    canceledCount++;
                }
            }
        }
        return canceledCount;
    }

    /**
     * Builds a master order entity from a database-authoritative program snapshot.
     */
    private Order buildOrder(ResolvedOrderDraft draft, Long orderNumber, Instant now) {
        OrderCreateRequest request = draft.request();
        ProgramOrderSnapshot programSnapshot = draft.programSnapshot();
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.programId = request.programId();
        order.programItemPicture = trimToNull(programSnapshot.itemPicture());
        order.userId = request.userId();
        order.programTitle = trimToNull(programSnapshot.title());
        order.programPlace = trimToNull(programSnapshot.place());
        order.programShowTime = programSnapshot.showTime();
        order.programPermitChooseSeat = defaultInt(programSnapshot.permitChooseSeat(), 0);
        order.distributionMode = DEFAULT_DISTRIBUTION_MODE;
        order.takeTicketMode = DEFAULT_TAKE_TICKET_MODE;
        order.orderPrice = draft.orderPrice();
        order.payOrderType = DEFAULT_PAY_ORDER_TYPE;
        order.orderStatus = OrderStatus.PENDING_PAYMENT.code();
        order.expireTime = now.plus(orderTimeout);
        order.createOrderTime = now;
        order.createdAt = now;
        order.updatedAt = now;
        order.status = 1;
        return order;
    }

    /**
     * Builds one ticket-user detail entity with the trusted ticket category price.
     */
    private OrderTicketUser buildTicketUser(
            ResolvedOrderDraft draft,
            Long ticketUserId,
            Long orderNumber,
            Instant now
    ) {
        OrderCreateRequest request = draft.request();
        OrderTicketUser ticketUser = new OrderTicketUser();
        ticketUser.orderNumber = orderNumber;
        ticketUser.programId = request.programId();
        ticketUser.userId = request.userId();
        ticketUser.ticketUserId = ticketUserId;
        ticketUser.seatId = null;
        ticketUser.seatInfo = null;
        ticketUser.ticketCategoryId = request.ticketCategoryId();
        ticketUser.orderPrice = draft.ticketPrice();
        ticketUser.payOrderType = DEFAULT_PAY_ORDER_TYPE;
        ticketUser.orderStatus = OrderStatus.PENDING_PAYMENT.code();
        ticketUser.createOrderTime = now;
        ticketUser.createdAt = now;
        ticketUser.updatedAt = now;
        ticketUser.status = 1;
        return ticketUser;
    }

    /**
     * Builds an order response and loads its detail rows.
     */
    private OrderResponse buildOrderResponse(Order order) {
        List<OrderTicketUserResponse> ticketUsers = orderDao.listTicketUsers(order.orderNumber)
                .stream()
                .map(OrderTicketUserResponse::from)
                .toList();
        return OrderResponse.of(order, ticketUsers);
    }

    /**
     * Finds an order or raises a stable API error.
     */
    private Order findOrderOrThrow(Long orderNumber) {
        return orderDao.findOrderByOrderNumber(orderNumber)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "order not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Verifies a persisted resource belongs to the authenticated user.
     */
    private void verifyUserOwnsResource(Long resourceUserId, Long authenticatedUserId) {
        if (!Objects.equals(resourceUserId, authenticatedUserId)) {
            throw new BusinessException("ORDER_FORBIDDEN", "order does not belong to current user", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Verifies the order request carries the gateway-authenticated user id.
     */
    private void validateAuthenticatedUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException("UNAUTHORIZED", "missing authenticated user id", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Resolves and validates a trusted program snapshot before calculating the order price.
     */
    private ResolvedOrderDraft resolveOrderDraft(
            OrderCreateRequest request,
            ProgramOrderSnapshot trustedSnapshot
    ) {
        validateDistinctTicketUsers(request.ticketUserIds());
        ticketUserClient.validateOwnership(request.userId(), request.ticketUserIds());
        ProgramOrderSnapshot snapshot = trustedSnapshot == null
                ? programInventoryClient.getOrderSnapshot(
                        request.programId(),
                        request.showTimeId(),
                        List.of(request.ticketCategoryId())
                )
                : trustedSnapshot;
        if (!Objects.equals(snapshot.programId(), request.programId())
                || !Objects.equals(snapshot.showTimeId(), request.showTimeId())) {
            throw new BusinessException(
                    "PROGRAM_ORDER_SNAPSHOT_INVALID",
                    "program order snapshot does not match program or show time",
                    HttpStatus.CONFLICT
            );
        }
        if (snapshot.ticketPrices() == null) {
            throw new BusinessException(
                    "PROGRAM_ORDER_SNAPSHOT_INVALID",
                    "program order snapshot has no ticket prices",
                    HttpStatus.CONFLICT
            );
        }
        BigDecimal ticketPrice = snapshot.ticketPrices()
                .stream()
                .filter(item -> Objects.equals(item.ticketCategoryId(), request.ticketCategoryId()))
                .map(ProgramTicketPriceSnapshot::price)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "TICKET_CATEGORY_PRICE_MISSING",
                        "ticket category price is missing",
                        HttpStatus.CONFLICT
                ));
        if (ticketPrice == null || ticketPrice.signum() < 0) {
            throw new BusinessException(
                    "TICKET_CATEGORY_PRICE_INVALID",
                    "ticket category price is invalid",
                    HttpStatus.CONFLICT
            );
        }
        BigDecimal orderPrice = ticketPrice.multiply(BigDecimal.valueOf(request.ticketUserIds().size()));
        return new ResolvedOrderDraft(request, snapshot, ticketPrice, orderPrice);
    }

    /**
     * Rejects duplicate ticket buyers so one request cannot create duplicate attendee rows.
     */
    private void validateDistinctTicketUsers(List<Long> ticketUserIds) {
        if (ticketUserIds.stream().distinct().count() != ticketUserIds.size()) {
            throw new BusinessException(
                    "DUPLICATE_TICKET_USER",
                    "ticket users must be unique",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Generates a time-sortable numeric order number.
     */
    private Long nextOrderNumber() {
        int sequence = ORDER_SEQUENCE.updateAndGet(value -> value >= 999 ? 0 : value + 1);
        return Instant.now().toEpochMilli() * 1000 + sequence;
    }

    /**
     * Returns a default integer when the value is null.
     */
    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * Holds trusted data used to assemble one master order and its attendee rows.
     */
    private record ResolvedOrderDraft(
            OrderCreateRequest request,
            ProgramOrderSnapshot programSnapshot,
            BigDecimal ticketPrice,
            BigDecimal orderPrice
    ) {
    }

    /**
     * Trims text and converts blank text to null.
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
