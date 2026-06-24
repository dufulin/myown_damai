package com.myown.damai.order.service;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.client.ProgramInventoryClient;
import com.myown.damai.order.client.ProgramInventoryItemRequest;
import com.myown.damai.order.client.ProgramInventoryRequest;
import com.myown.damai.order.dao.OrderDao;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderPayRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.dto.OrderTicketUserRequest;
import com.myown.damai.order.dto.OrderTicketUserResponse;
import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderStatus;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.lock.OrderLockExecutor;
import com.myown.damai.order.messaging.OrderAsyncProducer;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.redisson.api.RBucket;
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
    private static final int EXPIRED_SCAN_LIMIT = 200;
    private static final String ASYNC_PENDING_KEY_PREFIX = "damai:order:async:pending:user:";
    private static final List<Integer> IDEMPOTENT_ORDER_STATUSES = Arrays.asList(
            OrderStatus.UNPAID.code(),
            OrderStatus.PAID.code()
    );
    private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    private final OrderDao orderDao;
    private final OrderLockExecutor orderLockExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ProgramInventoryClient programInventoryClient;
    private final ObjectProvider<OrderAsyncProducer> orderAsyncProducerProvider;
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final Map<String, Long> localPendingOrders = new ConcurrentHashMap<>();
    private final Duration orderTimeout;
    private final boolean asyncEnabled;
    private final boolean inventoryEnabled;
    private final boolean timeoutScanEnabled;

    /**
     * Creates the order service with persistence and timeout settings.
     */
    public OrderService(
            OrderDao orderDao,
            OrderLockExecutor orderLockExecutor,
            TransactionTemplate transactionTemplate,
            ProgramInventoryClient programInventoryClient,
            ObjectProvider<OrderAsyncProducer> orderAsyncProducerProvider,
            ObjectProvider<RedissonClient> redissonClientProvider,
            @Value("${damai.order.timeout-minutes:15}") long timeoutMinutes,
            @Value("${damai.order.async.enabled:false}") boolean asyncEnabled,
            @Value("${damai.order.inventory.enabled:true}") boolean inventoryEnabled,
            @Value("${damai.order.timeout-scan-enabled:true}") boolean timeoutScanEnabled
    ) {
        this.orderDao = orderDao;
        this.orderLockExecutor = orderLockExecutor;
        this.transactionTemplate = transactionTemplate;
        this.programInventoryClient = programInventoryClient;
        this.orderAsyncProducerProvider = orderAsyncProducerProvider;
        this.redissonClientProvider = redissonClientProvider;
        this.orderTimeout = Duration.ofMinutes(timeoutMinutes);
        this.asyncEnabled = asyncEnabled;
        this.inventoryEnabled = inventoryEnabled;
        this.timeoutScanEnabled = timeoutScanEnabled;
    }

    /**
     * Creates one unpaid order with idempotency and a program-level distributed lock.
     */
    public OrderResponse createOrder(OrderCreateRequest request) {
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
        return orderLockExecutor.executeWithProgramLock(message.request().programId(), () -> {
            OrderResponse response = transactionTemplate.execute(status -> createOrderWithinLock(message.request(), message.orderNumber()));
            removePendingOrder(message.request().userId(), message.request().programId(), message.orderNumber());
            return Objects.requireNonNull(response, "created async order response must not be null");
        });
    }

    /**
     * Submits one order creation request to Kafka and returns its reserved order number.
     */
    private OrderResponse submitOrderAsync(OrderCreateRequest request) {
        return orderLockExecutor.executeWithProgramLock(request.programId(), () -> {
            Optional<Order> existingOrder = findExistingOrder(request.userId(), request.programId());
            if (existingOrder.isPresent()) {
                LOGGER.info(
                        "order async create idempotent hit inside lock, userId={}, programId={}, orderNumber={}",
                        request.userId(),
                        request.programId(),
                        existingOrder.get().orderNumber
                );
                return buildOrderResponse(existingOrder.get());
            }
            Optional<Long> pendingOrderNumber = findPendingOrderNumber(request.userId(), request.programId());
            if (pendingOrderNumber.isPresent()) {
                LOGGER.info(
                        "order async create pending hit inside lock, userId={}, programId={}, orderNumber={}",
                        request.userId(),
                        request.programId(),
                        pendingOrderNumber.get()
                );
                return buildSubmittedOrderResponse(request, pendingOrderNumber.get());
            }

            Long orderNumber = nextOrderNumber();
            putPendingOrder(request.userId(), request.programId(), orderNumber);
            try {
                orderAsyncProducerProvider.getObject()
                        .sendCreateOrderMessage(new OrderAsyncCreateMessage(orderNumber, request));
                LOGGER.info("order async create submitted, orderNumber={}, userId={}", orderNumber, request.userId());
                return buildSubmittedOrderResponse(request, orderNumber);
            } catch (RuntimeException exception) {
                removePendingOrder(request.userId(), request.programId(), orderNumber);
                throw exception;
            }
        });
    }

    /**
     * Creates one order after the distributed lock has been acquired.
     */
    private OrderResponse createOrderWithinLock(OrderCreateRequest request) {
        return createOrderWithinLock(request, null);
    }

    /**
     * Creates one order after the distributed lock has been acquired with an optional reserved order number.
     */
    private OrderResponse createOrderWithinLock(OrderCreateRequest request, Long reservedOrderNumber) {
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
        reserveInventory(request, orderNumber);
        try {
            // TODO: Match seats more intelligently when the user does not choose explicit seats.
            Instant now = Instant.now();
            BigDecimal orderPrice = calculateOrderPrice(request.ticketUsers());
            Order order = buildOrder(request, orderNumber, orderPrice, now);
            Order savedOrder = orderDao.saveOrder(order);
            List<OrderTicketUser> ticketUsers = request.ticketUsers()
                    .stream()
                    .map(item -> buildTicketUser(request, item, orderNumber, now))
                    .toList();
            orderDao.saveTicketUsers(ticketUsers);
            LOGGER.info("order created, orderNumber={}, userId={}, orderPrice={}", orderNumber, request.userId(), orderPrice);
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
     * Reserves inventory for a new order when inventory integration is enabled.
     */
    private void reserveInventory(OrderCreateRequest request, Long orderNumber) {
        if (!inventoryEnabled) {
            return;
        }
        ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromCreateItems(orderNumber, request.ticketUsers());
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
            ProgramInventoryRequest inventoryRequest = buildInventoryRequestFromCreateItems(orderNumber, request.ticketUsers());
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
    private ProgramInventoryRequest buildInventoryRequestFromCreateItems(Long orderNumber, List<OrderTicketUserRequest> ticketUsers) {
        List<ProgramInventoryItemRequest> items = ticketUsers.stream()
                .map(item -> new ProgramInventoryItemRequest(item.ticketCategoryId(), item.seatId()))
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
     * Stores an asynchronous pending order marker.
     */
    private void putPendingOrder(Long userId, Long programId, Long orderNumber) {
        String key = pendingOrderKey(userId, programId);
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient != null) {
            RBucket<Long> bucket = redissonClient.getBucket(key);
            boolean stored = bucket.trySet(orderNumber, orderTimeout.plusMinutes(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!stored) {
                throw new BusinessException("ORDER_ASYNC_PENDING_EXISTS", "order async pending marker already exists", HttpStatus.CONFLICT);
            }
            return;
        }
        Long existingOrderNumber = localPendingOrders.putIfAbsent(key, orderNumber);
        if (existingOrderNumber != null) {
            throw new BusinessException("ORDER_ASYNC_PENDING_EXISTS", "order async pending marker already exists", HttpStatus.CONFLICT);
        }
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
        return ASYNC_PENDING_KEY_PREFIX + userId + ":program:" + programId;
    }

    /**
     * Builds the immediate response returned after an asynchronous order submission.
     */
    private OrderResponse buildSubmittedOrderResponse(OrderCreateRequest request, Long orderNumber) {
        Instant now = Instant.now();
        BigDecimal orderPrice = calculateOrderPrice(request.ticketUsers());
        Order order = buildOrder(request, orderNumber, orderPrice, now);
        List<OrderTicketUserResponse> ticketUsers = request.ticketUsers()
                .stream()
                .map(item -> OrderTicketUserResponse.from(buildTicketUser(request, item, orderNumber, now)))
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
     * Lists orders for one user with pagination.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(Long userId, int pageNumber, int pageSize) {
        int normalizedPageNumber = Math.max(pageNumber, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPageNumber - 1) * normalizedPageSize;
        return orderDao.listOrdersByUserId(userId, normalizedPageSize, offset)
                .stream()
                .map(this::buildOrderResponse)
                .toList();
    }

    /**
     * Cancels one unpaid order.
     */
    @Transactional
    public OrderResponse cancelOrder(Long orderNumber) {
        Order order = findOrderOrThrow(orderNumber);
        if (!OrderStatus.UNPAID.equals(OrderStatus.fromCode(order.orderStatus))) {
            LOGGER.warn("order cancel rejected because status is not unpaid, orderNumber={}, status={}", orderNumber, order.orderStatus);
            throw new BusinessException("ORDER_STATUS_NOT_CANCELABLE", "only unpaid orders can be canceled", HttpStatus.CONFLICT);
        }
        List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
        Instant now = Instant.now();
        boolean canceled = orderDao.cancelUnpaidOrder(
                orderNumber,
                now,
                OrderStatus.UNPAID.code(),
                OrderStatus.CANCELED.code()
        );
        if (!canceled) {
            throw new BusinessException("ORDER_STATUS_NOT_CANCELABLE", "only unpaid orders can be canceled", HttpStatus.CONFLICT);
        }
        orderDao.cancelTicketUsers(orderNumber, now, OrderStatus.CANCELED.code());
        releaseLockedInventory(order, ticketUsers);
        LOGGER.info("order canceled, orderNumber={}", orderNumber);
        return getOrder(orderNumber);
    }

    /**
     * Marks one unpaid order as paid after payment provider confirmation.
     */
    @Transactional
    public OrderResponse markOrderPaid(Long orderNumber, OrderPayRequest request) {
        Order order = findOrderOrThrow(orderNumber);
        OrderStatus currentStatus = OrderStatus.fromCode(order.orderStatus);
        if (OrderStatus.PAID.equals(currentStatus)) {
            LOGGER.info("order pay confirmation ignored because order already paid, orderNumber={}", orderNumber);
            return getOrder(orderNumber);
        }
        if (!OrderStatus.UNPAID.equals(currentStatus)) {
            LOGGER.warn("order pay confirmation rejected because order is not unpaid, orderNumber={}, status={}", orderNumber, order.orderStatus);
            throw new BusinessException("ORDER_STATUS_NOT_PAYABLE", "only unpaid orders can be paid", HttpStatus.CONFLICT);
        }
        if (request.payAmount().compareTo(order.orderPrice) != 0) {
            LOGGER.warn("order pay confirmation rejected because amount mismatch, orderNumber={}, orderPrice={}, payAmount={}", orderNumber, order.orderPrice, request.payAmount());
            throw new BusinessException("ORDER_PAY_AMOUNT_MISMATCH", "pay amount does not match order amount", HttpStatus.CONFLICT);
        }
        List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
        Instant payTime = request.payTime() == null ? Instant.now() : request.payTime();
        boolean paid = orderDao.payUnpaidOrder(orderNumber, payTime, OrderStatus.UNPAID.code(), OrderStatus.PAID.code());
        if (!paid) {
            throw new BusinessException("ORDER_STATUS_NOT_PAYABLE", "only unpaid orders can be paid", HttpStatus.CONFLICT);
        }
        orderDao.payTicketUsers(orderNumber, payTime, OrderStatus.PAID.code());
        markLockedInventorySold(order, ticketUsers);
        LOGGER.info("order marked paid, orderNumber={}, tradeNumber={}", orderNumber, request.tradeNumber());
        return getOrder(orderNumber);
    }

    /**
     * Periodically cancels unpaid orders whose expire time has passed.
     */
    @Scheduled(fixedDelayString = "${damai.order.timeout-scan-delay-millis:60000}")
    @Transactional
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
    @Transactional
    public int cancelTimeoutOrders() {
        Instant now = Instant.now();
        List<Long> expiredOrderNumbers = orderDao.listExpiredUnpaidOrderNumbers(now, OrderStatus.UNPAID.code(), EXPIRED_SCAN_LIMIT);
        int canceledCount = 0;
        for (Long orderNumber : expiredOrderNumbers) {
            Optional<Order> foundOrder = orderDao.findOrderByOrderNumber(orderNumber);
            List<OrderTicketUser> ticketUsers = orderDao.listTicketUsers(orderNumber);
            boolean canceled = orderDao.cancelUnpaidOrder(
                    orderNumber,
                    now,
                    OrderStatus.UNPAID.code(),
                    OrderStatus.CANCELED.code()
            );
            if (canceled) {
                orderDao.cancelTicketUsers(orderNumber, now, OrderStatus.CANCELED.code());
                foundOrder.ifPresent(order -> releaseLockedInventory(order, ticketUsers));
                canceledCount++;
            }
        }
        return canceledCount;
    }

    /**
     * Builds a master order entity from request data.
     */
    private Order buildOrder(OrderCreateRequest request, Long orderNumber, BigDecimal orderPrice, Instant now) {
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.programId = request.programId();
        order.programItemPicture = trimToNull(request.programItemPicture());
        order.userId = request.userId();
        order.programTitle = trimToNull(request.programTitle());
        order.programPlace = trimToNull(request.programPlace());
        order.programShowTime = request.programShowTime();
        order.programPermitChooseSeat = defaultInt(request.programPermitChooseSeat(), 0);
        order.distributionMode = trimToNull(request.distributionMode());
        order.takeTicketMode = trimToNull(request.takeTicketMode());
        order.orderPrice = orderPrice;
        order.payOrderType = request.payOrderType();
        order.orderStatus = OrderStatus.UNPAID.code();
        order.expireTime = now.plus(orderTimeout);
        order.createOrderTime = now;
        order.createdAt = now;
        order.updatedAt = now;
        order.status = 1;
        return order;
    }

    /**
     * Builds one ticket-user detail entity from request data.
     */
    private OrderTicketUser buildTicketUser(
            OrderCreateRequest request,
            OrderTicketUserRequest item,
            Long orderNumber,
            Instant now
    ) {
        OrderTicketUser ticketUser = new OrderTicketUser();
        ticketUser.orderNumber = orderNumber;
        ticketUser.programId = request.programId();
        ticketUser.userId = request.userId();
        ticketUser.ticketUserId = item.ticketUserId();
        ticketUser.seatId = item.seatId();
        ticketUser.seatInfo = trimToNull(item.seatInfo());
        ticketUser.ticketCategoryId = item.ticketCategoryId();
        ticketUser.orderPrice = item.orderPrice();
        ticketUser.payOrderType = request.payOrderType();
        ticketUser.orderStatus = OrderStatus.UNPAID.code();
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
     * Calculates the total order price from ticket-user detail rows.
     */
    private BigDecimal calculateOrderPrice(List<OrderTicketUserRequest> ticketUsers) {
        return ticketUsers.stream()
                .map(OrderTicketUserRequest::orderPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
     * Trims text and converts blank text to null.
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
