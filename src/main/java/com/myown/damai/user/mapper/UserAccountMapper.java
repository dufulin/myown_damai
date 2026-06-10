package com.myown.damai.user.mapper;

import com.myown.damai.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

    UserAccount selectById(@Param("id") Long id);

    UserAccount selectByUsername(@Param("username") String username);

    int insert(UserAccount user);
}
