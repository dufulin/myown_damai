package com.myown.damai.user.entity;

import java.time.Instant;

/**
 * Represents a long-lived refresh token whose plaintext value is never persisted.
 */
public class UserRefreshToken {

    private Long id;
    private String tokenHash;
    private UserAccount user;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant revokedAt;

    /**
     * Creates an empty refresh token for MyBatis mapping.
     */
    public UserRefreshToken() {
    }

    /**
     * Creates a new refresh token for one authenticated user.
     */
    public UserRefreshToken(String tokenHash, UserAccount user, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.user = user;
        this.expiresAt = expiresAt;
    }

    /**
     * Checks whether the refresh token can still issue a new access token.
     */
    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Revokes the refresh token at the supplied time.
     */
    public void revoke(Instant now) {
        this.revokedAt = now;
    }

    /**
     * Returns the database id.
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the database id.
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the hashed token value.
     */
    public String getTokenHash() {
        return tokenHash;
    }

    /**
     * Sets the hashed token value.
     */
    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    /**
     * Returns the refresh token owner.
     */
    public UserAccount getUser() {
        return user;
    }

    /**
     * Sets the refresh token owner.
     */
    public void setUser(UserAccount user) {
        this.user = user;
    }

    /**
     * Returns the creation time.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the creation time.
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the expiration time.
     */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /**
     * Sets the expiration time.
     */
    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the revocation time.
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * Sets the revocation time.
     */
    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
