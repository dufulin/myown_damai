package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.UserSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {

    UserSession selectById(@Param("id") Long id);

    UserSession selectByTokenHash(@Param("tokenHash") String tokenHash);

    int insert(UserSession session);

    int update(UserSession session);
}
