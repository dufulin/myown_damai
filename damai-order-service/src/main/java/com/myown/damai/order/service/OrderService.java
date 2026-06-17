package com.myown.damai.order.service;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.dao.OrderDao;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderPayRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.dto.OrderTicketUserRequest;
import com.myown.damai.order.dto.OrderTicketUserResponse;
import com.myown.damai.order.entity.Order;
import com.myown.damai.order.entity.OrderStatus;
import com.myown.damai.order.entity.OrderTicketUser;
import com.myown.damai.order.lock.OrderLockExecutor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final List<Integer> IDEMPOTENT_ORDER_STATUSES = Arrays.asList(
            OrderStatus.UNPAID.code(),
            OrderStatus.PAID.code()
    );
    private static final AtomicInteger ORDER_SEQUENCE = new AtomicInteger(ThreadLocalRandom.current().nextInt(1000));

    private final OrderDao orderDao;
    private final OrderLockExecutor orderLockExecutor;
    private final TransactionTemplate transactionTemplate;
    private final Duration orderTimeout;
    private final boolean timeoutScanEnabled;

    /**
     * Creates the order service with persistence and timeout settings.
     */
    public OrderService(
            OrderDao orderDao,
            OrderLockExecutor orderLockExecutor,
            TransactionTemplate transactionTemplate,
            @Value("${damai.order.timeout-minutes:15}") long timeoutMinutes,
            @Value("${damai.order.timeout-scan-enabled:true}") boolean timeoutScanEnabled
    ) {
        this.orderDao = orderDao;
        this.orderLockExecutor = orderLockExecutor;
        this.transactionTemplate = transactionTemplate;
        this.orderTimeout = Duration.ofMinutes(timeoutMinutes);
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

        return orderLockExecutor.executeWithProgramLock(request.programId(), () -> {
            // Start the database transaction only after the program lock is held, so the lock-protected idempotency check sees latest committed data.
            OrderResponse response = transactionTemplate.execute(status -> createOrderWithinLock(request));
            return Objects.requireNonNull(response, "created order response must not be null");
        });
    }

    /**
     * Creates one order after the distributed lock has been acquired.
     */
    private OrderResponse createOrderWithinLock(OrderCreateRequest request) {
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

        // TODO: 匹配座位，后续需要根据节目、票档和购票人数量锁定可用座位。
        Instant now = Instant.now();
        Long orderNumber = nextOrderNumber();
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
    }

    /**
     * Finds an existing user-program order that should make order creation idempotent.
     */
    private Optional<Order> findExistingOrder(Long userId, Long programId) {
        return orderDao.findExistingUserProgramOrder(userId, programId, IDEMPOTENT_ORDER_STATUSES);
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
        Instant payTime = request.payTime() == null ? Instant.now() : request.payTime();
        boolean paid = orderDao.payUnpaidOrder(orderNumber, payTime, OrderStatus.UNPAID.code(), OrderStatus.PAID.code());
        if (!paid) {
            throw new BusinessException("ORDER_STATUS_NOT_PAYABLE", "only unpaid orders can be paid", HttpStatus.CONFLICT);
        }
        orderDao.payTicketUsers(orderNumber, payTime, OrderStatus.PAID.code());
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
            boolean canceled = orderDao.cancelUnpaidOrder(
                    orderNumber,
                    now,
                    OrderStatus.UNPAID.code(),
                    OrderStatus.CANCELED.code()
            );
            if (canceled) {
                orderDao.cancelTicketUsers(orderNumber, now, OrderStatus.CANCELED.code());
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
