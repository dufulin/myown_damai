package com.myown.damai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String username,

        @NotBlank
        @Size(min = 6, max = 64)
        String password,

        @Size(max = 50)
        String nickname,

        @Size(max = 30)
        String phone
) {
}
