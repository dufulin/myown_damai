package com.myown.damai.order.controller;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.order.dto.OrderCancelRequest;
import com.myown.damai.order.dto.OrderCreateRequest;
import com.myown.damai.order.dto.OrderResponse;
import com.myown.damai.order.dto.OrderTimeoutCancelResponse;
import com.myown.damai.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        LOGGER.info("order create request received, userId={}, programId={}", request.userId(), request.programId());
        OrderResponse response = orderService.createOrder(request);
        LOGGER.info("order create request succeeded, orderNumber={}", response.orderNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * Gets one order detail by order number.
     */
    @GetMapping("/{orderNumber}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable Long orderNumber) {
        LOGGER.info("order detail request received, orderNumber={}", orderNumber);
        OrderResponse response = orderService.getOrder(orderNumber);
        LOGGER.info("order detail request succeeded, orderNumber={}", orderNumber);
        return ApiResponse.success(response);
    }

    /**
     * Lists orders for one user.
     */
    @GetMapping
    public ApiResponse<List<OrderResponse>> listOrders(
            @RequestParam("userId") Long userId,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        LOGGER.info("order list request received, userId={}", userId);
        List<OrderResponse> orders = orderService.listOrders(userId, pageNumber, pageSize);
        LOGGER.info("order list request succeeded, userId={}, count={}", userId, orders.size());
        return ApiResponse.success(orders);
    }

    /**
     * Cancels one unpaid order.
     */
    @PostMapping("/{orderNumber}/cancel")
    public ApiResponse<OrderResponse> cancelOrder(
            @PathVariable Long orderNumber,
            @RequestBody(required = false) OrderCancelRequest request
    ) {
        LOGGER.info("order cancel request received, orderNumber={}, hasReason={}", orderNumber, request != null && request.reason() != null);
        OrderResponse response = orderService.cancelOrder(orderNumber);
        LOGGER.info("order cancel request succeeded, orderNumber={}", orderNumber);
        return ApiResponse.success(response);
    }

    /**
     * Triggers timeout cancellation manually for operations and tests.
     */
    @PostMapping("/timeout-cancel")
    public ApiResponse<OrderTimeoutCancelResponse> timeoutCancelOrders() {
        LOGGER.info("order timeout cancel request received");
        int canceledCount = orderService.cancelTimeoutOrders();
        LOGGER.info("order timeout cancel request succeeded, canceledCount={}", canceledCount);
        return ApiResponse.success(new OrderTimeoutCancelResponse(canceledCount));
    }
}
