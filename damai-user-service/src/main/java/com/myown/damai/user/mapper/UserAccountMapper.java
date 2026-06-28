package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.UserAccount;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Maps user account, login index, and authorization role operations to MyBatis SQL.
 */
@Mapper
public interface UserAccountMapper {

    /**
     * Selects a user by primary key from d_user.
     */
    UserAccount selectById(@Param("id") Long id);

    /**
     * Selects a user through the mobile index table.
     */
    UserAccount selectByMobile(@Param("mobile") String mobile);

    /**
     * Selects a user through the email index table.
     */
    UserAccount selectByEmail(@Param("email") String email);

    /**
     * Inserts the d_user record.
     */
    int insert(UserAccount user);

    /**
     * Inserts the d_user_mobile relation for mobile login.
     */
    int insertMobileIndex(
            @Param("userId") Long userId,
            @Param("mobile") String mobile,
            @Param("now") Instant now
    );

    /**
     * Inserts the d_user_email relation for email login.
     */
    int insertEmailIndex(
            @Param("userId") Long userId,
            @Param("email") String email,
            @Param("now") Instant now
    );

    /**
     * Inserts the default authorization role for a newly registered user.
     */
    int insertRole(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("now") Instant now
    );

    /**
     * Updates an existing user role record.
     */
    int updateRole(
            @Param("userId") Long userId,
            @Param("role") String role,
            @Param("now") Instant now
    );
}
