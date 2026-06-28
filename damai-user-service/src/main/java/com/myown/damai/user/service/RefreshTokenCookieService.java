package com.myown.damai.user.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

/**
 * Builds secure refresh-token cookies and their logout deletion counterpart.
 */
@Service
public class RefreshTokenCookieService {

    private final String cookieName;
    private final String cookiePath;
    private final boolean secure;
    private final String sameSite;

    /**
     * Creates the cookie service from environment-aware authentication settings.
     */
    public RefreshTokenCookieService(
            @Value("${damai.auth.refresh-cookie.name:damai_refresh_token}") String cookieName,
            @Value("${damai.auth.refresh-cookie.path:/api/users}") String cookiePath,
            @Value("${damai.auth.refresh-cookie.secure:false}") boolean secure,
            @Value("${damai.auth.refresh-cookie.same-site:Lax}") String sameSite
    ) {
        this.cookieName = cookieName;
        this.cookiePath = cookiePath;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    /**
     * Returns the configured refresh-token cookie name for request binding.
     */
    public String cookieName() {
        return cookieName;
    }

    /**
     * Builds an HttpOnly refresh-token cookie that expires with the database token.
     */
    public ResponseCookie create(String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return baseCookie(refreshToken)
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge)
                .build();
    }

    /**
     * Builds an immediately expired cookie for logout.
     */
    public ResponseCookie clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build();
    }

    /**
     * Creates the common security attributes shared by issued and cleared cookies.
     */
    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath);
    }
}
