package com.myown.damai.pay.client;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.pay.dto.OrderPayRequest;
import com.myown.damai.pay.dto.OrderSnapshot;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Calls the order service for payment-related order reads and writes.
 */
@Component
public class OrderClient {

    private static final ParameterizedTypeReference<ApiResponse<OrderSnapshot>> ORDER_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderClient.class);

    private final WebClient webClient;
    private final String orderServiceUrl;
    private final CircuitBreaker orderCircuitBreaker;
    private final Duration requestTimeout;
    private final int retryCount;
    private final Duration retryBackoff;

    /**
     * Creates the client with a load-balanced WebClient.
     */
    public OrderClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${damai.pay.order-service-url:http://damai-order-service}") String orderServiceUrl,
            @Value("${damai.pay.order-client.timeout-millis:2500}") long requestTimeoutMillis,
            @Value("${damai.pay.order-client.retry-count:1}") int retryCount,
            @Value("${damai.pay.order-client.retry-backoff-millis:150}") long retryBackoffMillis
    ) {
        this.webClient = webClientBuilder.build();
        this.orderServiceUrl = orderServiceUrl;
        this.orderCircuitBreaker = circuitBreakerFactory.create("payOrderClient");
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.retryCount = retryCount;
        this.retryBackoff = Duration.ofMillis(retryBackoffMillis);
    }

    /**
     * Loads one order by order number.
     */
    public OrderSnapshot getOrder(Long orderNumber, Long authenticatedUserId) {
        return orderCircuitBreaker.run(
                () -> executeGetOrder(orderNumber, authenticatedUserId),
                exception -> handleOrderClientFallback("getOrder", orderNumber, exception)
        );
    }

    /**
     * Marks one order as paid through the order service.
     */
    public OrderSnapshot markOrderPaid(Long orderNumber, OrderPayRequest request) {
        return orderCircuitBreaker.run(
                () -> executeMarkOrderPaid(orderNumber, request),
                exception -> handleOrderClientFallback("markOrderPaid", orderNumber, exception)
        );
    }

    /**
     * Executes the order query request with timeout and retry protection.
     */
    private OrderSnapshot executeGetOrder(Long orderNumber, Long authenticatedUserId) {
        ApiResponse<OrderSnapshot> response = applyClientPolicies(webClient.get()
                .uri(orderServiceUrl + "/api/orders/{orderNumber}", orderNumber)
                .header(AuthenticatedUserHeader.USER_ID, String.valueOf(authenticatedUserId))
                .retrieve()
                .bodyToMono(ORDER_RESPONSE_TYPE))
                .block();
        if (response == null || response.data() == null) {
            throw new BusinessException("ORDER_NOT_FOUND", "order not found", HttpStatus.NOT_FOUND);
        }
        return response.data();
    }

    /**
     * Executes the paid-status update request with timeout and retry protection.
     */
    private OrderSnapshot executeMarkOrderPaid(Long orderNumber, OrderPayRequest request) {
        ApiResponse<OrderSnapshot> response = applyClientPolicies(webClient.post()
                .uri(orderServiceUrl + "/api/orders/{orderNumber}/paid", orderNumber)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ORDER_RESPONSE_TYPE))
                .block();
        if (response == null || response.data() == null) {
            throw new BusinessException("ORDER_PAY_UPDATE_FAILED", "failed to update order payment status", HttpStatus.BAD_GATEWAY);
        }
        return response.data();
    }

    /**
     * Applies timeout and bounded retry to one reactive order-service call.
     */
    private <T> Mono<T> applyClientPolicies(Mono<T> requestMono) {
        Mono<T> guardedMono = requestMono.timeout(requestTimeout);
        if (retryCount <= 0) {
            return guardedMono;
        }
        return guardedMono.retryWhen(Retry.backoff(retryCount, retryBackoff)
                .filter(this::isRetryableOrderClientFailure)
                .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> retrySignal.failure()));
    }

    /**
     * Checks whether an order-service failure is transient enough to retry.
     */
    private boolean isRetryableOrderClientFailure(Throwable exception) {
        if (exception instanceof TimeoutException || exception instanceof WebClientRequestException) {
            return true;
        }
        if (exception instanceof WebClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return false;
    }

    /**
     * Converts protected order-service failures into stable business errors.
     */
    private OrderSnapshot handleOrderClientFallback(String action, Long orderNumber, Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            throw businessException;
        }
        if (exception instanceof WebClientResponseException responseException) {
            HttpStatusCode statusCode = responseException.getStatusCode();
            LOGGER.warn(
                    "order service call rejected, action={}, orderNumber={}, status={}",
                    action,
                    orderNumber,
                    statusCode.value()
            );
            if (HttpStatus.NOT_FOUND.value() == statusCode.value()) {
                throw new BusinessException("ORDER_NOT_FOUND", "order not found", HttpStatus.NOT_FOUND);
            }
            throw new BusinessException("ORDER_SERVICE_REJECTED", "order service rejected request", resolveStatus(statusCode));
        }
        LOGGER.warn("order service call fallback, action={}, orderNumber={}", action, orderNumber, exception);
        throw new BusinessException("ORDER_SERVICE_UNAVAILABLE", "order service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Converts a generic HTTP status code to a Spring HttpStatus value.
     */
    private HttpStatus resolveStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }
}
