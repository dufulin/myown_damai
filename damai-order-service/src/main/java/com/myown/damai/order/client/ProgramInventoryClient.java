package com.myown.damai.order.client;

import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
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

    /**
     * Creates the client with a configurable program service base URL.
     */
    public ProgramInventoryClient(
            RestClient.Builder restClientBuilder,
            @Value("${damai.program-service.base-url:http://localhost:8082}") String programServiceBaseUrl
    ) {
        this.restClient = restClientBuilder.baseUrl(programServiceBaseUrl).build();
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
        try {
            ApiResponse<?> response = restClient.post()
                    .uri("/api/programs/{programId}/inventory/{action}", programId, action)
                    .body(request)
                    .retrieve()
                    .body(ApiResponse.class);
            if (response == null || !"SUCCESS".equals(response.code())) {
                throw new BusinessException("PROGRAM_INVENTORY_ACTION_FAILED", "program inventory action failed", HttpStatus.CONFLICT);
            }
        } catch (RestClientResponseException exception) {
            HttpStatus status = resolveStatus(exception.getStatusCode());
            LOGGER.warn(
                    "program inventory action rejected, programId={}, action={}, orderNumber={}, status={}",
                    programId,
                    action,
                    request.orderNumber(),
                    exception.getStatusCode().value()
            );
            throw new BusinessException("PROGRAM_INVENTORY_ACTION_FAILED", "program inventory action failed", status);
        } catch (RestClientException exception) {
            LOGGER.warn(
                    "program inventory action transport failed, programId={}, action={}, orderNumber={}",
                    programId,
                    action,
                    request.orderNumber(),
                    exception
            );
            throw new BusinessException("PROGRAM_INVENTORY_UNAVAILABLE", "program inventory service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Converts a generic HTTP status code to a Spring HttpStatus value.
     */
    private HttpStatus resolveStatus(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        return status == null ? HttpStatus.BAD_GATEWAY : status;
    }
}
