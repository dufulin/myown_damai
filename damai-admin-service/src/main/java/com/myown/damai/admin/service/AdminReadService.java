package com.myown.damai.admin.service;

import com.myown.damai.admin.dto.AdminDashboardResponse;
import com.myown.damai.admin.dto.AdminOrderResponse;
import com.myown.damai.admin.dto.AdminPageResponse;
import com.myown.damai.admin.dto.AdminProgramResponse;
import com.myown.damai.admin.dto.AdminUserResponse;
import com.myown.damai.admin.mapper.AdminReadMapper;
import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Builds bounded management read models from the shared single-database projection.
 */
@Service
public class AdminReadService {

    private static final int DEFAULT_RECENT_ORDER_LIMIT = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_NUMBER = 100;

    private final AdminReadMapper adminReadMapper;

    /**
     * Creates the management read service.
     */
    public AdminReadService(AdminReadMapper adminReadMapper) {
        this.adminReadMapper = adminReadMapper;
    }

    /**
     * Loads overview counters and the ten most recent orders.
     */
    public AdminDashboardResponse getDashboard() {
        List<AdminOrderResponse> recentOrders = adminReadMapper.listOrders(
                null,
                null,
                null,
                null,
                0,
                DEFAULT_RECENT_ORDER_LIMIT
        );
        return new AdminDashboardResponse(
                adminReadMapper.countUsers(),
                adminReadMapper.countActivePrograms(),
                adminReadMapper.countOrdersByStatus(1),
                adminReadMapper.countOrdersByStatus(3),
                adminReadMapper.countOrdersByStatus(5),
                adminReadMapper.sumPaidAmount(),
                recentOrders
        );
    }

    /**
     * Lists user accounts with optional keyword and human-role filters.
     */
    public AdminPageResponse<AdminUserResponse> listUsers(
            String keyword,
            String role,
            int pageNumber,
            int pageSize
    ) {
        PageWindow window = pageWindow(pageNumber, pageSize);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedRole = normalizeHumanRole(role);
        List<AdminUserResponse> users = adminReadMapper.listUsers(
                normalizedKeyword,
                normalizedRole,
                window.offset(),
                window.pageSize()
        );
        long total = adminReadMapper.countFilteredUsers(normalizedKeyword, normalizedRole);
        return AdminPageResponse.of(users, total, window.pageNumber(), window.pageSize());
    }

    /**
     * Lists programs with optional title and on-sale status filters.
     */
    public AdminPageResponse<AdminProgramResponse> listPrograms(
            String keyword,
            Integer programStatus,
            int pageNumber,
            int pageSize
    ) {
        validateBinaryStatus(programStatus, "programStatus");
        PageWindow window = pageWindow(pageNumber, pageSize);
        String normalizedKeyword = trimToNull(keyword);
        List<AdminProgramResponse> programs = adminReadMapper.listPrograms(
                normalizedKeyword,
                programStatus,
                window.offset(),
                window.pageSize()
        );
        long total = adminReadMapper.countFilteredPrograms(normalizedKeyword, programStatus);
        return AdminPageResponse.of(programs, total, window.pageNumber(), window.pageSize());
    }

    /**
     * Lists orders with optional identity and lifecycle filters.
     */
    public AdminPageResponse<AdminOrderResponse> listOrders(
            Long orderNumber,
            Long userId,
            Long programId,
            Integer orderStatus,
            int pageNumber,
            int pageSize
    ) {
        validateOrderStatus(orderStatus);
        PageWindow window = pageWindow(pageNumber, pageSize);
        List<AdminOrderResponse> orders = adminReadMapper.listOrders(
                orderNumber,
                userId,
                programId,
                orderStatus,
                window.offset(),
                window.pageSize()
        );
        long total = adminReadMapper.countFilteredOrders(orderNumber, userId, programId, orderStatus);
        return AdminPageResponse.of(orders, total, window.pageNumber(), window.pageSize());
    }

    /**
     * Validates and calculates an offset pagination window.
     */
    private PageWindow pageWindow(int pageNumber, int pageSize) {
        if (pageNumber < 1
                || pageNumber > MAX_PAGE_NUMBER
                || pageSize < 1
                || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(
                    "ADMIN_PAGE_INVALID",
                    "pageNumber and pageSize must be between 1 and 100",
                    HttpStatus.BAD_REQUEST
            );
        }
        return new PageWindow(pageNumber, pageSize, (pageNumber - 1) * pageSize);
    }

    /**
     * Normalizes and validates an optional human account role.
     */
    private String normalizeHumanRole(String role) {
        String normalizedRole = trimToNull(role);
        if (normalizedRole == null) {
            return null;
        }
        try {
            UserRole parsedRole = UserRole.valueOf(normalizedRole.toUpperCase(Locale.ROOT));
            if (UserRole.SYSTEM.equals(parsedRole)) {
                throw new IllegalArgumentException("SYSTEM is not a human role");
            }
            return parsedRole.name();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "ADMIN_ROLE_INVALID",
                    "role must be USER, OPERATOR, or ADMIN",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Validates an optional binary program status.
     */
    private void validateBinaryStatus(Integer value, String fieldName) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException(
                    "ADMIN_FILTER_INVALID",
                    fieldName + " must be 0 or 1",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Validates an optional order lifecycle status.
     */
    private void validateOrderStatus(Integer orderStatus) {
        if (orderStatus != null && (orderStatus < 0 || orderStatus > 5)) {
            throw new BusinessException(
                    "ADMIN_ORDER_STATUS_INVALID",
                    "orderStatus must be between 0 and 5",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    /**
     * Trims optional text and converts blank input to null.
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Holds a validated offset pagination window.
     */
    private record PageWindow(int pageNumber, int pageSize, int offset) {
    }
}
