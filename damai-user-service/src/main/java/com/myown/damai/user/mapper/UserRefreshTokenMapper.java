package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.UserRefreshToken;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Maps refresh-token persistence operations to MyBatis SQL statements.
 */
@Mapper
public interface UserRefreshTokenMapper {

    /**
     * Finds a refresh token and its owning user by token hash.
     */
    UserRefreshToken selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Inserts a newly issued refresh token.
     */
    int insert(UserRefreshToken refreshToken);

    /**
     * Updates the revocation and expiration state of a refresh token.
     */
    int update(UserRefreshToken refreshToken);

    /**
     * Atomically revokes an active refresh token and returns the affected row count.
     */
    int revokeIfActive(
            @Param("id") Long id,
            @Param("revokedAt") Instant revokedAt
    );

    /**
     * Deletes refresh tokens that have been expired or revoked past the retention boundary.
     */
    int deleteInactiveBefore(@Param("deleteBefore") Instant deleteBefore);
}
