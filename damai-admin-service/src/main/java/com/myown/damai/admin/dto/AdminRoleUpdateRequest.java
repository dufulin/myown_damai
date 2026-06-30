package com.myown.damai.admin.dto;

import com.myown.damai.common.auth.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Carries an administrator role change for a human account.
 */
public record AdminRoleUpdateRequest(
        @NotNull UserRole role
) {
}
