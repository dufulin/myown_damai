package com.myown.damai.admin.dto;

import java.time.Instant;

/**
 * Exposes account and role fields needed by management user lists.
 */
public record AdminUserResponse(
        Long id,
        String name,
        String mobile,
        String email,
        String role,
        Integer status,
        Instant createdAt
) {
}
