package com.myown.damai.user.dto;

import java.time.Instant;

/**
 * Carries the public access-token response and the private refresh-token cookie value.
 */
public record AuthenticationResult(
        AuthResponse response,
        String refreshToken,
        Instant refreshExpiresAt
) {
}
