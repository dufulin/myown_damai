package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserRefreshToken;
import java.time.Instant;
import java.util.Optional;

/**
 * Defines persistence operations for refresh-token lifecycle management.
 */
public interface UserRefreshTokenDao {

    /**
     * Finds a refresh token by the hash of its plaintext value.
     */
    Optional<UserRefreshToken> findByTokenHash(String tokenHash);

    /**
     * Saves a new or modified refresh token.
     */
    UserRefreshToken save(UserRefreshToken refreshToken);

    /**
     * Atomically consumes an active refresh token so concurrent replay cannot succeed.
     */
    boolean revokeIfActive(Long id, Instant revokedAt);

    /**
     * Deletes inactive refresh tokens older than the supplied boundary.
     */
    int deleteInactiveBefore(Instant deleteBefore);
}
