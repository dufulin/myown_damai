package com.myown.damai.user.dao;

import com.myown.damai.user.entity.UserSession;
import java.util.Optional;

public interface UserSessionDao {

    Optional<UserSession> findByTokenHash(String tokenHash);

    UserSession save(UserSession session);
}
