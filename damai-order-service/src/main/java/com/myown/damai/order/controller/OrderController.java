package com.myown.damai.order.controller;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.order.dto.OrderAsyncMessageResponse;
import com.myown.damai.order.dto.OrderCancelRequest;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderCursorPageResponse;
import com.myown.damai.order.dto.OrderPayRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.dto.OrderTimeoutCancelResponse;
import com.myown.damai.order.service.OrderService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides order creation, query, cancellation, and timeout cancellation APIs.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    /**
     * Creates the controller with order business operations.
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates one unpaid order.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("order create request received, userId={}, programId={}", authenticatedUserId, request.programId());
        OrderResponse response = orderService.createOrder(request.withUserId(authenticatedUserId));
        LOGGER.info("order create request succeeded, orderNumber={}", response.orderNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Gets one order detail by order number.
     */
    @GetMapping("/{orderNumber}")
    public ApiResponse<OrderResponse> getOrder(
            @PathVariable Long orderNumber,
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("order detail request received, orderNumber={}, userId={}", orderNumber, authenticatedUserId);
        OrderResponse response = orderService.getOrderForUser(orderNumber, authenticatedUserId);
        LOGGER.info("order detail request succeeded, orderNumber={}", orderNumber);
        return ApiResponse.success(response);
    }

    /**
     * Gets asynchronous order creation message status by order number.
     */
    @GetMapping("/{orderNumber}/async-message")
    public ApiResponse<OrderAsyncMessageResponse> getAsyncMessage(
            @PathVariable Long orderNumber,
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("order async message status request received, orderNumber={}, userId={}", orderNumber, authenticatedUserId);
        OrderAsyncMessageResponse response = orderService.getAsyncMessageForUser(orderNumber, authenticatedUserId);
        LOGGER.info("order async message status request succeeded, orderNumber={}, status={}", orderNumber, response.messageStatusName());
        return ApiResponse.success(response);
    }

    /**
     * Lists orders for one user.
     */
    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("order list request received, userId={}", authenticatedUserId);
        List<OrderResponse> orders = orderService.listOrders(authenticatedUserId, pageNumber, pageSize);
        LOGGER.info("order list request succeeded, userId={}, count={}", authenticatedUserId, orders.size());
        return ApiResponse.success(orders);
    }

    /**
     * Lists orders for one user with create-time and id cursor pagination.
     */
    @GetMapping("/cursor")
    public ApiResponse<OrderCursorPageResponse> listOrdersByCursor(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestParam(value = "cursorCreateTime", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursorCreateTime,
            @RequestParam(value = "cursorId", required = false) Long cursorId,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info(
                "order cursor list request received, userId={}, cursorCreateTime={}, cursorId={}",
                authenticatedUserId,
                cursorCreateTime,
                cursorId
        );
        OrderCursorPageResponse response = orderService.listOrdersByCursor(authenticatedUserId, cursorCreateTime, cursorId, pageSize);
        LOGGER.info("order cursor list request succeeded, userId={}, count={}, hasMore={}", authenticatedUserId, response.orders().size(), response.hasMore());
        return ApiResponse.success(response);
    }

    /**
     * Cancels one unpaid order.
     */
    @PostMapping("/{orderNumber}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable Long orderNumber,
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestBody(required = false) OrderCancelRequest request
    ) {
        Long authenticatedUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        LOGGER.info("order cancel request received, orderNumber={}, userId={}, hasReason={}", orderNumber, authenticatedUserId, request != null && request.reason() != null);
        OrderResponse response = orderService.cancelOrderForUser(orderNumber, authenticatedUserId);
        LOGGER.info("order cancel request succeeded, orderNumber={}", orderNumber);
        return ApiResponse.success(response);
    }

    /**
     * Marks one order as paid after payment confirmation.
     */
    @PostMapping("/{orderNumber}/paid")
    public ApiResponse<OrderResponse> markOrderPaid(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader,
            @PathVariable Long orderNumber,
            @Valid @RequestBody OrderPayRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.SYSTEM);
        LOGGER.info("order paid request received, orderNumber={}, tradeNumber={}", orderNumber, request.tradeNumber());
        OrderResponse response = orderService.markOrderPaid(orderNumber, request);
        LOGGER.info("order paid request succeeded, orderNumber={}", orderNumber);
        return ApiResponse.success(response);
    }

    /**
     * Triggers timeout cancellation manually for operations and tests.
     */
    @PostMapping("/timeout-cancel")
    public ApiResponse<OrderTimeoutCancelResponse> timeoutCancelOrders(
            @RequestHeader(value = AuthenticatedUserHeader.USER_ROLE, required = false) String roleHeader
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        LOGGER.info("order timeout cancel request received");
        int canceledCount = orderService.cancelTimeoutOrders();
        LOGGER.info("order timeout cancel request succeeded, canceledCount={}", canceledCount);
        return ApiResponse.success(new OrderTimeoutCancelResponse(canceledCount));
    }

}
