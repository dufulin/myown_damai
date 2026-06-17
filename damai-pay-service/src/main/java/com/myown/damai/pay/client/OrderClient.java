package com.myown.damai.pay.client;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.pay.dto.OrderPayRequest;
import com.myown.damai.pay.dto.OrderSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Calls the order service for payment-related order reads and writes.
 */
@Component
public class OrderClient {

    private static final ParameterizedTypeReference<ApiResponse<OrderSnapshot>> ORDER_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final String orderServiceUrl;

    /**
     * Creates the client with a load-balanced WebClient.
     */
    public OrderClient(
            WebClient.Builder webClientBuilder,
            @Value("${damai.pay.order-service-url:http://damai-order-service}") String orderServiceUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.orderServiceUrl = orderServiceUrl;
    }

    /**
     * Loads one order by order number.
     */
    public OrderSnapshot getOrder(Long orderNumber) {
        ApiResponse<OrderSnapshot> response = webClient.get()
                .uri(orderServiceUrl + "/api/orders/{orderNumber}", orderNumber)
                .retrieve()
                .bodyToMono(ORDER_RESPONSE_TYPE)
                .block();
        if (response == null || response.data() == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "order not found", HttpStatus.NOT_FOUND);
        }
        return response.data();
    }

    /**
     * Marks one order as paid through the order service.
     */
    public OrderSnapshot markOrderPaid(Long orderNumber, OrderPayRequest request) {
        ApiResponse<OrderSnapshot> response = webClient.post()
                .uri(orderServiceUrl + "/api/orders/{orderNumber}/paid", orderNumber)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ORDER_RESPONSE_TYPE)
                .block();
        if (response == null || response.data() == null) {
            throw new BusinessException("ORDER_PAY_UPDATE_FAILED", "failed to update order payment status", HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }
}
