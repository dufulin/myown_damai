package com.myown.damai.order.client;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.common.observability.TraceContext;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls the user service to validate that order ticket buyers belong to the authenticated user.
 */
@Component
public class TicketUserClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TicketUserClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final int maxRetryCount;
    private final Duration retryBackoff;

    /**
     * Creates the ticket buyer client with bounded network timeouts and retries.
     */
    public TicketUserClient(
            RestClient.Builder restClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${damai.user-service.base-url:http://localhost:8081}") String userServiceBaseUrl,
            @Value("${damai.user-service.client.connect-timeout-millis:1000}") int connectTimeoutMillis,
            @Value("${damai.user-service.client.read-timeout-millis:2500}") int readTimeoutMillis,
            @Value("${damai.user-service.client.retry-count:1}") int maxRetryCount,
            @Value("${damai.user-service.client.retry-backoff-millis:150}") long retryBackoffMillis
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(userServiceBaseUrl)
                .requestInterceptor((request, body, execution) -> {
                    TraceContext.writeTo(request.getHeaders());
                    return execution.execute(request, body);
                })
                .build();
        this.circuitBreaker = circuitBreakerFactory.create("orderTicketUserClient");
        this.maxRetryCount = maxRetryCount;
        this.retryBackoff = Duration.ofMillis(retryBackoffMillis);
    }

    /**
     * Verifies all supplied ticket buyers are active and owned by the authenticated user.
     */
    public void validateOwnership(Long userId, List<Long> ticketUserIds) {
        circuitBreaker.run(
                () -> {
                    executeWithRetry(userId, ticketUserIds);
                    return null;
                },
                exception -> handleFallback(userId, exception)
        );
    }

    /**
     * Executes ownership validation with bounded retries for transient transport failures.
     */
    private void executeWithRetry(Long userId, List<Long> ticketUserIds) {
        int attempt = 0;
        while (true) {
            try {
                executeValidation(userId, ticketUserIds);
                return;
            } catch (RuntimeException exception) {
                if (attempt >= maxRetryCount || !isRetryable(exception)) {
                    throw exception;
                }
                attempt++;
                LOGGER.warn(
                        "ticket user ownership validation retrying, userId={}, attempt={}",
                        userId,
                        attempt,
                        exception
                );
                sleepBeforeRetry();
            }
        }
    }

    /**
     * Sends one ownership validation request to the user service.
     */
    private void executeValidation(Long userId, List<Long> ticketUserIds) {
        ApiResponse<?> response = restClient.post()
                .uri("/api/users/ticket-users/validate")
                .header(AuthenticatedUserHeader.USER_ROLE, UserRole.SYSTEM.name())
                .body(new TicketUserValidationRequest(userId, ticketUserIds))
                .retrieve()
                .body(ApiResponse.class);
        if (response == null || !"SUCCESS".equals(response.code())) {
            throw new BusinessException(
                    "TICKET_USER_VALIDATION_FAILED",
                    "ticket user validation failed",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Checks whether a failed validation call is transient enough to retry.
     */
    private boolean isRetryable(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return exception instanceof RestClientException;
    }

    /**
     * Waits briefly before retrying a transient validation failure.
     */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    "TICKET_USER_VALIDATION_INTERRUPTED",
                    "ticket user validation retry interrupted",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
    }

    /**
     * Converts protected ownership validation failures into stable business errors.
     */
    private Void handleFallback(Long userId, Throwable exception) {
        if (exception instanceof BusinessException businessException) {
            throw businessException;
        }
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatus status = resolveStatus(responseException.getStatusCode());
            LOGGER.warn(
                    "ticket user ownership validation rejected, userId={}, status={}",
                    userId,
                    responseException.getStatusCode().value()
            );
            throw new BusinessException(
                    "TICKET_USER_VALIDATION_REJECTED",
                    "ticket user is unavailable or does not belong to current user",
                    status
            );
        }
        LOGGER.warn("ticket user ownership validation unavailable, userId={}", userId, exception);
        throw new BusinessException(
                "TICKET_USER_SERVICE_UNAVAILABLE",
                "user service unavailable",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    /**
     * Converts a generic HTTP status code to a Spring HttpStatus value.
     */
    private HttpStatus resolveStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }
}
