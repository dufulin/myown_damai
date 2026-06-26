package com.myown.damai.order.client;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import java.time.Duration;
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
 * Calls the program service inventory APIs used by order state transitions.
 */
@Component
public class ProgramInventoryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramInventoryClient.class);

    private final RestClient restClient;
    private final CircuitBreaker inventoryCircuitBreaker;
    private final int maxRetryCount;
    private final Duration retryBackoff;

    /**
     * Creates the client with a configurable program service base URL.
     */
    public ProgramInventoryClient(
            RestClient.Builder restClientBuilder,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory,
            @Value("${damai.program-service.base-url:http://localhost:8082}") String programServiceBaseUrl,
            @Value("${damai.program-service.client.connect-timeout-millis:1000}") int connectTimeoutMillis,
            @Value("${damai.program-service.client.read-timeout-millis:2500}") int readTimeoutMillis,
            @Value("${damai.program-service.client.retry-count:1}") int maxRetryCount,
            @Value("${damai.program-service.client.retry-backoff-millis:150}") long retryBackoffMillis
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMillis);
        requestFactory.setReadTimeout(readTimeoutMillis);
        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .baseUrl(programServiceBaseUrl)
                .build();
        this.inventoryCircuitBreaker = circuitBreakerFactory.create("orderProgramInventoryClient");
        this.maxRetryCount = maxRetryCount;
        this.retryBackoff = Duration.ofMillis(retryBackoffMillis);
    }

    /**
     * Locks ticket stock and optional seats for one order.
     */
    public void lockInventory(Long programId, ProgramInventoryRequest request) {
        postInventoryAction(programId, "lock", request);
    }

    /**
     * Releases ticket stock and locked seats for one canceled or timed-out order.
     */
    public void releaseInventory(Long programId, ProgramInventoryRequest request) {
        postInventoryAction(programId, "release", request);
    }

    /**
     * Marks locked seats as sold after payment succeeds.
     */
    public void markInventorySold(Long programId, ProgramInventoryRequest request) {
        postInventoryAction(programId, "sold", request);
    }

    /**
     * Sends one inventory action request and normalizes transport failures.
     */
    private void postInventoryAction(Long programId, String action, ProgramInventoryRequest request) {
        inventoryCircuitBreaker.run(
                () -> {
                    executeInventoryActionWithRetry(programId, action, request);
                    return null;
                },
                exception -> handleInventoryFallback(programId, action, request, exception)
        );
    }

    /**
     * Executes one inventory action with bounded retry for transient failures.
     */
    private void executeInventoryActionWithRetry(Long programId, String action, ProgramInventoryRequest request) {
        int attempt = 0;
        while (true) {
            try {
                executeSingleInventoryAction(programId, action, request);
                return;
            } catch (RuntimeException exception) {
                if (attempt >= maxRetryCount || !isRetryableInventoryFailure(exception)) {
                    throw exception;
                }
                attempt++;
                LOGGER.warn(
                        "program inventory action retrying, programId={}, action={}, orderNumber={}, attempt={}",
                        programId,
                        action,
                        request.orderNumber(),
                        attempt,
                        exception
                );
                sleepBeforeRetry();
            }
        }
    }

    /**
     * Sends one inventory action request to the program service.
     */
    private void executeSingleInventoryAction(Long programId, String action, ProgramInventoryRequest request) {
        ApiResponse<?> response = restClient.post()
                .uri("/api/programs/{programId}/inventory/{action}", programId, action)
                .body(request)
                .retrieve()
                .body(ApiResponse.class);
        if (response == null || !"SUCCESS".equals(response.code())) {
            throw new BusinessException("PROGRAM_INVENTORY_ACTION_FAILED", "program inventory action failed", HttpStatus.CONFLICT);
        }
    }

    /**
     * Checks whether a program inventory failure is transient enough to retry.
     */
    private boolean isRetryableInventoryFailure(RuntimeException exception) {
        if (exception instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError();
        }
        return exception instanceof RestClientException;
    }

    /**
     * Waits briefly before retrying a transient inventory failure.
     */
    private void sleepBeforeRetry() {
        try {
            Thread.sleep(retryBackoff.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("PROGRAM_INVENTORY_RETRY_INTERRUPTED", "program inventory retry interrupted", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Converts protected inventory failures into stable business errors.
     */
    private Void handleInventoryFallback(
            Long programId,
            String action,
            ProgramInventoryRequest request,
            Throwable exception
    ) {
        if (exception instanceof BusinessException businessException) {
            throw businessException;
        }
        if (exception instanceof RestClientResponseException responseException) {
            HttpStatus status = resolveStatus(responseException.getStatusCode());
            LOGGER.warn(
                    "program inventory action rejected, programId={}, action={}, orderNumber={}, status={}",
                    programId,
                    action,
                    request.orderNumber(),
                    responseException.getStatusCode().value()
            );
            throw new BusinessException("PROGRAM_INVENTORY_ACTION_FAILED", "program inventory action failed", status);
        }
        LOGGER.warn(
                "program inventory action fallback, programId={}, action={}, orderNumber={}",
                programId,
                action,
                request.orderNumber(),
                exception
        );
        throw new BusinessException("PROGRAM_INVENTORY_UNAVAILABLE", "program inventory service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Converts a generic HTTP status code to a Spring HttpStatus value.
     */
    private HttpStatus resolveStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }
}
