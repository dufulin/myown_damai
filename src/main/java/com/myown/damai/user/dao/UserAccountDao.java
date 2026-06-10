package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserAccount;
import java.util.Optional;

/**
 * Provides user account persistence operations without exposing MyBatis details to services.
 */
public interface UserAccountDao {

    /**
     * Checks whether a normal user exists for the given mobile.
     */
    boolean existsByMobile(String mobile);

    /**
     * Checks whether a normal user exists for the given email.
     */
    boolean existsByEmail(String email);

    /**
     * Finds a user through the mobile login index.
     */
    Optional<UserAccount> findByMobile(String mobile);

    /**
     * Finds a user through the email login index.
     */
    Optional<UserAccount> findByEmail(String email);

    /**
     * Saves a user and its login indexes.
     */
    UserAccount save(UserAccount user);
}
