package com.myown.damai.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries registration data based on the d_user, d_user_mobile, and d_user_email tables.
 */
public record RegisterRequest(
        @Size(max = 256)
        String name,

        @Size(max = 256)
        String username,

        @NotBlank
        @Size(min = 6, max = 64)
        String password,

        @Size(max = 191)
        String mobile,

        @Size(max = 191)
        String phone,

        @Email
        @Size(max = 191)
        String email
) {
}
