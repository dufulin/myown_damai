package com.myown.damai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries login credentials where login can be a mobile number or an email address.
 */
public record LoginRequest(
        @Size(max = 191)
        String login,

        @Size(max = 191)
        String username,

        @NotBlank
        @Size(max = 64)
        String password
) {
}
