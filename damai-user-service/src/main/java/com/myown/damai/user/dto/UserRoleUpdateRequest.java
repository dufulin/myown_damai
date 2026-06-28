package com.myown.damai.user.dto;

import com.myown.damai.common.auth.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Carries an administrator's requested human-account role change.
 */
public record UserRoleUpdateRequest(
        @NotNull UserRole role
) {
}
