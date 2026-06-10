package com.myown.damai.user.dto;

import com.myown.damai.user.entity.UserAccount;

/**
 * Exposes non-sensitive user profile data to frontend callers.
 */
public record UserProfileResponse(
        Long id,
        String username,
        String name,
        String nickname,
        String mobile,
        String phone,
        String email
) {

    /**
     * Builds a profile response from the user entity while preserving legacy field names.
     */
    public static UserProfileResponse from(UserAccount user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                user.getNickname(),
                user.getMobile(),
                user.getPhone(),
                user.getEmail()
        );
    }
}
