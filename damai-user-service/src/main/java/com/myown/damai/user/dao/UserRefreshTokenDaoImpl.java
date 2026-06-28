package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserRefreshToken;
import com.myown.damai.user.mapper.UserRefreshTokenMapper;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Persists refresh tokens in MySQL without caching their security-sensitive lookup state.
 */
@Repository
public class UserRefreshTokenDaoImpl implements UserRefreshTokenDao {

    private final UserRefreshTokenMapper userRefreshTokenMapper;

    /**
     * Creates the DAO with its MyBatis mapper.
     */
    public UserRefreshTokenDaoImpl(UserRefreshTokenMapper userRefreshTokenMapper) {
        this.userRefreshTokenMapper = userRefreshTokenMapper;
    }

    /**
     * Finds a refresh token by hash.
     */
    @Override
    public Optional<UserRefreshToken> findByTokenHash(String tokenHash) {
        return Optional.ofNullable(userRefreshTokenMapper.selectByTokenHash(tokenHash));
    }

    /**
     * Saves a new or modified refresh token.
     */
    @Override
    public UserRefreshToken save(UserRefreshToken refreshToken) {
        if (refreshToken.getId() == null) {
            refreshToken.setCreatedAt(Instant.now());
            userRefreshTokenMapper.insert(refreshToken);
            Objects.requireNonNull(refreshToken.getId(), "generated refresh token id must not be null");
        } else {
            userRefreshTokenMapper.update(refreshToken);
        }
        return refreshToken;
    }

    /**
     * Atomically consumes an active refresh token.
     */
    @Override
    public boolean revokeIfActive(Long id, Instant revokedAt) {
        return userRefreshTokenMapper.revokeIfActive(id, revokedAt) == 1;
    }

    /**
     * Deletes inactive refresh tokens older than the supplied boundary.
     */
    @Override
    public int deleteInactiveBefore(Instant deleteBefore) {
        return userRefreshTokenMapper.deleteInactiveBefore(deleteBefore);
    }
}
