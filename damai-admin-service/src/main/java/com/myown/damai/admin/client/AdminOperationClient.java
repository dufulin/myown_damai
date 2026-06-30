package com.myown.damai.admin.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.myown.damai.admin.dto.AdminRoleUpdateRequest;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * Delegates privileged mutations to the domain service that owns each state transition.
 */
@Component
public class AdminOperationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminOperationClient.class);
    private static final ParameterizedTypeReference<ApiResponse<JsonNode>> JSON_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final String userServiceUrl;
    private final String programServiceUrl;
    private final String orderServiceUrl;
    private final String payServiceUrl;
    private final Duration requestTimeout;

    /**
     * Creates the operation client with domain service addresses and timeout protection.
     */
    public AdminOperationClient(
            WebClient.Builder webClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${damai.admin.user-service-url:http://damai-user-service}") String userServiceUrl,
            @Value("${damai.admin.program-service-url:http://damai-program-service}") String programServiceUrl,
            @Value("${damai.admin.order-service-url:http://damai-order-service}") String orderServiceUrl,
            @Value("${damai.admin.pay-service-url:http://damai-pay-service}") String payServiceUrl,
            @Value("${damai.admin.client.timeout-millis:3500}") long requestTimeoutMillis
    ) {
        this.webClient = webClientBuilder.build();
        this.circuitBreaker = circuitBreakerFactory.create("adminOperationClient");
        this.userServiceUrl = userServiceUrl;
        this.programServiceUrl = programServiceUrl;
        this.orderServiceUrl = orderServiceUrl;
        this.payServiceUrl = payServiceUrl;
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
    }

    /**
     * Delegates a human-account role update to the user service.
     */
    public JsonNode updateUserRole(
            Long userId,
            AdminRoleUpdateRequest request,
            String operatorUserId,
            String operatorRole
    ) {
        Mono<ApiResponse<JsonNode>> requestMono = webClient.put()
                .uri(userServiceUrl + "/api/users/{userId}/role", userId)
                .header(AuthenticatedUserHeader.USER_ID, operatorUserId)
                .header(AuthenticatedUserHeader.USER_ROLE, operatorRole)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JSON_RESPONSE_TYPE);
        return execute("updateUserRole", requestMono);
    }

    /**
     * Delegates a program-offline transition to the program service.
     */
    public JsonNode offlineProgram(Long programId, String operatorUserId, String operatorRole) {
        Mono<ApiResponse<JsonNode>> requestMono = webClient.post()
                .uri(programServiceUrl + "/api/programs/{programId}/offline", programId)
                .header(AuthenticatedUserHeader.USER_ID, operatorUserId)
                .header(AuthenticatedUserHeader.USER_ROLE, operatorRole)
                .retrieve()
                .bodyToMono(JSON_RESPONSE_TYPE);
        return execute("offlineProgram", requestMono);
    }

    /**
     * Delegates one timeout cancellation scan to the order service.
     */
    public JsonNode cancelTimeoutOrders(String operatorUserId, String operatorRole) {
        Mono<ApiResponse<JsonNode>> requestMono = webClient.post()
                .uri(orderServiceUrl + "/api/orders/timeout-cancel")
                .header(AuthenticatedUserHeader.USER_ID, operatorUserId)
                .header(AuthenticatedUserHeader.USER_ROLE, operatorRole)
                .retrieve()
                .bodyToMono(JSON_RESPONSE_TYPE);
        return execute("cancelTimeoutOrders", requestMono);
    }

    /**
     * Delegates one due payment-event compensation scan to the payment service.
     */
    public JsonNode compensatePayEvents(String operatorUserId, String operatorRole) {
        Mono<ApiResponse<JsonNode>> requestMono = webClient.post()
                .uri(payServiceUrl + "/api/pay/events/compensate")
                .header(AuthenticatedUserHeader.USER_ID, operatorUserId)
                .header(AuthenticatedUserHeader.USER_ROLE, operatorRole)
                .retrieve()
                .bodyToMono(JSON_RESPONSE_TYPE);
        return execute("compensatePayEvents", requestMono);
    }

    /**
     * Executes one non-retried privileged mutation under timeout and circuit-breaker protection.
     */
    private JsonNode execute(String action, Mono<ApiResponse<JsonNode>> requestMono) {
        return circuitBreaker.run(
                () -> validateResponse(action, requestMono.timeout(requestTimeout).block()),
                exception -> handleFailure(action, exception)
        );
    }

    /**
     * Validates the stable downstream API envelope before returning its data.
     */
    private JsonNode validateResponse(String action, ApiResponse<JsonNode> response) {
        if (response == null || !"SUCCESS".equals(response.code())) {
            throw new BusinessException(
                    "ADMIN_OPERATION_FAILED",
                    action + " failed",
                    HttpStatus.BAD_GATEWAY
            );
        }
        return response.data();
    }

    /**
     * Converts protected downstream failures to a stable management API error.
     */
    private JsonNode handleFailure(String action, Throwable exception) {
        Throwable cause = unwrap(exception);
        if (cause instanceof BusinessException businessException) {
            throw businessException;
        }
        if (cause instanceof WebClientResponseException responseException) {
            LOGGER.warn(
                    "admin downstream operation rejected, action={}, status={}",
                    action,
                    responseException.getStatusCode().value(),
                    responseException
            );
            HttpStatus status = HttpStatus.resolve(responseException.getStatusCode().value());
            throw new BusinessException(
                    "ADMIN_OPERATION_REJECTED",
                    action + " was rejected by domain service",
                    status == null ? HttpStatus.BAD_GATEWAY : status
            );
        }
        if (cause instanceof TimeoutException || cause instanceof WebClientRequestException) {
            LOGGER.warn("admin downstream operation unavailable, action={}", action, cause);
            throw new BusinessException(
                    "ADMIN_OPERATION_UNAVAILABLE",
                    action + " service is unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        LOGGER.warn("admin downstream operation failed, action={}", action, cause);
        throw new BusinessException(
                "ADMIN_OPERATION_FAILED",
                action + " failed",
                HttpStatus.BAD_GATEWAY
        );
    }

    /**
     * Unwraps common asynchronous wrapper exceptions for stable error classification.
     */
    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)) {
            current = current.getCause();
        }
        return current;
    }
}
