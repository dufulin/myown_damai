package com.myown.damai.common.web;

import com.myown.damai.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * Defines and parses the gateway-authenticated user identity header.
 */
public final class AuthenticatedUserHeader {

    public static final String USER_ID = "X-Damai-User-Id";

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
}
