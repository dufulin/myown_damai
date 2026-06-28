package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserSession;
import java.time.Instant;
import java.util.Optional;

/**
 * Defines persistence operations for short-lived access-token sessions.
 */
public interface UserSessionDao {

    /**
     * Finds an access-token session by token hash.
     */
    Optional<UserSession> findByTokenHash(String tokenHash);

    /**
     * Saves a new or modified access-token session.
     */
    UserSession save(UserSession session);

    /**
     * Deletes access-token sessions inactive before the supplied boundary.
     */
    int deleteInactiveBefore(Instant deleteBefore);
}
