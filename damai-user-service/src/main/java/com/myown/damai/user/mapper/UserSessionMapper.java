package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.UserSession;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Maps access-token session persistence operations to MyBatis SQL statements.
 */
@Mapper
public interface UserSessionMapper {

    /**
     * Finds one access-token session by id.
     */
    UserSession selectById(@Param("id") Long id);

    /**
     * Finds one access-token session by token hash.
     */
    UserSession selectByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * Inserts a newly issued access-token session.
     */
    int insert(UserSession session);

    /**
     * Updates access-token expiration and revocation state.
     */
    int update(UserSession session);

    /**
     * Deletes access-token sessions inactive before the supplied boundary.
     */
    int deleteInactiveBefore(@Param("deleteBefore") Instant deleteBefore);
}
