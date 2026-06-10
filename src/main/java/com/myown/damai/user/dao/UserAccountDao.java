package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserAccount;
import java.util.Optional;

public interface UserAccountDao {

    boolean existsByUsername(String username);

    Optional<UserAccount> findByUsername(String username);

    UserAccount save(UserAccount user);
}
