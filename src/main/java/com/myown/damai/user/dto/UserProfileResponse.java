package com.myown.damai.user.dto;

import com.myown.damai.user.entity.UserAccount;

public record UserProfileResponse(
        Long id,
        String username,
        String nickname,
        String phone
) {

    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getPhone()
        );
    }
}
