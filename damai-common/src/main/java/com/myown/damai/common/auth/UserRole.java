package com.myown.damai.common.auth;

import java.util.Locale;

/**
 * Defines human account roles and the internal service identity used by authorization policies.
 */
public enum UserRole {
    USER,
    OPERATOR,
    ADMIN,
    SYSTEM;

    /**
     * Parses a role name and falls back to USER for missing legacy account data.
     */
    public static UserRole fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return USER;
        }
        return UserRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
