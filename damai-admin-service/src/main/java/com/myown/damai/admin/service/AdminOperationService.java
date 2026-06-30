package com.myown.damai.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.myown.damai.admin.client.AdminOperationClient;
import com.myown.damai.admin.dto.AdminActionResponse;
import com.myown.damai.admin.dto.AdminRoleUpdateRequest;
import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Coordinates privileged operations while preserving domain-service ownership of mutations.
 */
@Service
public class AdminOperationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminOperationService.class);

    private final AdminOperationClient operationClient;

    /**
     * Creates the management operation service.
     */
    public AdminOperationService(AdminOperationClient operationClient) {
        this.operationClient = operationClient;
    }

    /**
     * Updates one human account role through the user service.
     */
    public AdminActionResponse updateUserRole(
            Long userId,
            AdminRoleUpdateRequest request,
            Long operatorUserId,
            String operatorRole
    ) {
        if (UserRole.SYSTEM.equals(request.role())) {
            throw new BusinessException(
                    "ADMIN_ROLE_INVALID",
                    "SYSTEM role cannot be assigned to a user account",
                    HttpStatus.BAD_REQUEST
            );
        }
        JsonNode result = operationClient.updateUserRole(
                userId,
                request,
                String.valueOf(operatorUserId),
                operatorRole
        );
        LOGGER.info(
                "admin user role operation completed, operatorUserId={}, targetUserId={}, role={}",
                operatorUserId,
                userId,
                request.role()
        );
        return new AdminActionResponse("UPDATE_USER_ROLE", result);
    }

    /**
     * Takes one program offline through the program service.
     */
    public AdminActionResponse offlineProgram(Long programId, Long operatorUserId, String operatorRole) {
        JsonNode result = operationClient.offlineProgram(
                programId,
                String.valueOf(operatorUserId),
                operatorRole
        );
        LOGGER.info(
                "admin program offline operation completed, operatorUserId={}, programId={}",
                operatorUserId,
                programId
        );
        return new AdminActionResponse("OFFLINE_PROGRAM", result);
    }

    /**
     * Triggers the protected timeout-order cancellation workflow.
     */
    public AdminActionResponse cancelTimeoutOrders(Long operatorUserId, String operatorRole) {
        JsonNode result = operationClient.cancelTimeoutOrders(String.valueOf(operatorUserId), operatorRole);
        LOGGER.info("admin timeout cancellation operation completed, operatorUserId={}", operatorUserId);
        return new AdminActionResponse("CANCEL_TIMEOUT_ORDERS", result);
    }

    /**
     * Triggers the protected payment-event compensation workflow.
     */
    public AdminActionResponse compensatePayEvents(Long operatorUserId, String operatorRole) {
        JsonNode result = operationClient.compensatePayEvents(String.valueOf(operatorUserId), operatorRole);
        LOGGER.info("admin payment compensation operation completed, operatorUserId={}", operatorUserId);
        return new AdminActionResponse("COMPENSATE_PAY_EVENTS", result);
    }
}
