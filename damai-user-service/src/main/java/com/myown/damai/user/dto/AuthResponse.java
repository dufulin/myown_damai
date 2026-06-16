package com.myown.damai.user.dto;

import java.time.Instant;

public record AuthResponse(
        String token,
        String tokenType,
        Instant expiresAt,
        UserProfileResponse user
) {
}
