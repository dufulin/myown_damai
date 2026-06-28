package com.myown.damai.common.web;

import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * Defines and parses the gateway-authenticated user identity header.
 */
public final class AuthenticatedUserHeader {

    public static final String USER_ID = "X-Damai-User-Id";
    public static final String USER_ROLE = "X-Damai-User-Role";

    /**
     * Prevents utility class instantiation.
     */
    private AuthenticatedUserHeader() {
    }

    /**
     * Resolves the authenticated user id from a required gateway header value.
     */
    public static Long resolveRequired(String userIdHeader) {
        if (!StringUtils.hasText(userIdHeader)) {
            throw new BusinessException("UNAUTHORIZED", "missing authenticated user header", HttpStatus.UNAUTHORIZED);
        }
        try {
            return Long.valueOf(userIdHeader.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", "invalid authenticated user header", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Resolves the authenticated user role from the trusted gateway header.
     */
    public static UserRole resolveRequiredRole(String userRoleHeader) {
        if (!StringUtils.hasText(userRoleHeader)) {
            throw new BusinessException("UNAUTHORIZED", "missing authenticated user role", HttpStatus.UNAUTHORIZED);
        }
        try {
            return UserRole.fromNullable(userRoleHeader);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("UNAUTHORIZED", "invalid authenticated user role", HttpStatus.UNAUTHORIZED);
        }
    }

    /**
     * Requires the trusted gateway or service role to match at least one allowed role.
     */
    public static UserRole requireAnyRole(String userRoleHeader, UserRole... allowedRoles) {
        UserRole actualRole = resolveRequiredRole(userRoleHeader);
        for (UserRole allowedRole : allowedRoles) {
            if (actualRole == allowedRole) {
                return actualRole;
            }
        }
        throw new BusinessException("FORBIDDEN", "insufficient permissions", HttpStatus.FORBIDDEN);
    }
}
