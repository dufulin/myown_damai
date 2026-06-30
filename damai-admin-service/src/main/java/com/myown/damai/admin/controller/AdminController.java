package com.myown.damai.admin.controller;

import com.myown.damai.admin.dto.AdminActionResponse;
import com.myown.damai.admin.dto.AdminDashboardResponse;
import com.myown.damai.admin.dto.AdminOrderResponse;
import com.myown.damai.admin.dto.AdminPageResponse;
import com.myown.damai.admin.dto.AdminProgramResponse;
import com.myown.damai.admin.dto.AdminRoleUpdateRequest;
import com.myown.damai.admin.dto.AdminUserResponse;
import com.myown.damai.admin.service.AdminOperationService;
import com.myown.damai.admin.service.AdminReadService;
import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.dto.ApiResponse;
import com.myown.damai.common.observability.TraceContext;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provides authenticated operations and administrator management APIs.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminController.class);

    private final AdminReadService adminReadService;
    private final AdminOperationService adminOperationService;

    /**
     * Creates the management controller.
     */
    public AdminController(
            AdminReadService adminReadService,
            AdminOperationService adminOperationService
    ) {
        this.adminReadService = adminReadService;
        this.adminOperationService = adminOperationService;
    }

    /**
     * Gets management overview counters and recent orders.
     */
    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardResponse> getDashboard(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info("admin dashboard request received, operatorUserId={}", operatorUserId);
        AdminDashboardResponse response = adminReadService.getDashboard();
        LOGGER.info("admin dashboard request succeeded, operatorUserId={}", operatorUserId);
        return ApiResponse.success(response);
    }

    /**
     * Lists managed user accounts.
     */
    @GetMapping("/users")
    public ApiResponse<AdminPageResponse<AdminUserResponse>> listUsers(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info(
                "admin user list request received, operatorUserId={}, keyword={}, role={}",
                operatorUserId,
                keyword,
                role
        );
        AdminPageResponse<AdminUserResponse> response =
                adminReadService.listUsers(keyword, role, pageNumber, pageSize);
        LOGGER.info(
                "admin user list request succeeded, operatorUserId={}, count={}, total={}",
                operatorUserId,
                response.items().size(),
                response.total()
        );
        return ApiResponse.success(response);
    }

    /**
     * Lists managed programs with inventory totals.
     */
    @GetMapping("/programs")
    public ApiResponse<AdminPageResponse<AdminProgramResponse>> listPrograms(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "programStatus", required = false) Integer programStatus,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info(
                "admin program list request received, operatorUserId={}, keyword={}, programStatus={}",
                operatorUserId,
                keyword,
                programStatus
        );
        AdminPageResponse<AdminProgramResponse> response =
                adminReadService.listPrograms(keyword, programStatus, pageNumber, pageSize);
        LOGGER.info(
                "admin program list request succeeded, operatorUserId={}, count={}, total={}",
                operatorUserId,
                response.items().size(),
                response.total()
        );
        return ApiResponse.success(response);
    }

    /**
     * Lists managed orders under optional identity and lifecycle filters.
     */
    @GetMapping("/orders")
    public ApiResponse<AdminPageResponse<AdminOrderResponse>> listOrders(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader,
            @RequestParam(value = "orderNumber", required = false) Long orderNumber,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "programId", required = false) Long programId,
            @RequestParam(value = "orderStatus", required = false) Integer orderStatus,
            @RequestParam(value = "pageNumber", defaultValue = "1") int pageNumber,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info(
                "admin order list request received, operatorUserId={}, orderNumber={}, userId={}, programId={}, orderStatus={}",
                operatorUserId,
                orderNumber,
                userId,
                programId,
                orderStatus
        );
        AdminPageResponse<AdminOrderResponse> response = adminReadService.listOrders(
                orderNumber,
                userId,
                programId,
                orderStatus,
                pageNumber,
                pageSize
        );
        LOGGER.info(
                "admin order list request succeeded, operatorUserId={}, count={}, total={}",
                operatorUserId,
                response.items().size(),
                response.total()
        );
        return ApiResponse.success(response);
    }

    /**
     * Updates a human account role through the user service.
     */
    @PutMapping("/users/{userId}/role")
    public ApiResponse<AdminActionResponse> updateUserRole(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader,
            @PathVariable Long userId,
            @Valid @RequestBody AdminRoleUpdateRequest request
    ) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.ADMIN);
        Long operatorUserId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        TraceContext.putUserId(operatorUserId);
        LOGGER.info(
                "admin role update request received, operatorUserId={}, targetUserId={}, role={}",
                operatorUserId,
                userId,
                request.role()
        );
        AdminActionResponse response =
                adminOperationService.updateUserRole(userId, request, operatorUserId, roleHeader);
        LOGGER.info(
                "admin role update request succeeded, operatorUserId={}, targetUserId={}",
                operatorUserId,
                userId
        );
        return ApiResponse.success(response);
    }

    /**
     * Takes one program offline through the program service.
     */
    @PostMapping("/programs/{programId}/offline")
    public ApiResponse<AdminActionResponse> offlineProgram(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader,
            @PathVariable Long programId
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        TraceContext.putProgramId(programId);
        LOGGER.info(
                "admin program offline request received, operatorUserId={}, programId={}",
                operatorUserId,
                programId
        );
        AdminActionResponse response =
                adminOperationService.offlineProgram(programId, operatorUserId, roleHeader);
        LOGGER.info(
                "admin program offline request succeeded, operatorUserId={}, programId={}",
                operatorUserId,
                programId
        );
        return ApiResponse.success(response);
    }

    /**
     * Triggers timeout cancellation through the order service.
     */
    @PostMapping("/orders/timeout-cancel")
    public ApiResponse<AdminActionResponse> cancelTimeoutOrders(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info("admin timeout cancel request received, operatorUserId={}", operatorUserId);
        AdminActionResponse response =
                adminOperationService.cancelTimeoutOrders(operatorUserId, roleHeader);
        LOGGER.info("admin timeout cancel request succeeded, operatorUserId={}", operatorUserId);
        return ApiResponse.success(response);
    }

    /**
     * Triggers payment-event compensation through the payment service.
     */
    @PostMapping("/pay/events/compensate")
    public ApiResponse<AdminActionResponse> compensatePayEvents(
            @RequestHeader(AuthenticatedUserHeader.USER_ID) String userIdHeader,
            @RequestHeader(AuthenticatedUserHeader.USER_ROLE) String roleHeader
    ) {
        Long operatorUserId = requireOperationsUser(userIdHeader, roleHeader);
        LOGGER.info("admin pay compensation request received, operatorUserId={}", operatorUserId);
        AdminActionResponse response =
                adminOperationService.compensatePayEvents(operatorUserId, roleHeader);
        LOGGER.info("admin pay compensation request succeeded, operatorUserId={}", operatorUserId);
        return ApiResponse.success(response);
    }

    /**
     * Validates an operator or administrator and returns the trusted user id.
     */
    private Long requireOperationsUser(String userIdHeader, String roleHeader) {
        AuthenticatedUserHeader.requireAnyRole(roleHeader, UserRole.OPERATOR, UserRole.ADMIN);
        Long userId = AuthenticatedUserHeader.resolveRequired(userIdHeader);
        TraceContext.putUserId(userId);
        return userId;
    }
}
