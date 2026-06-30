package com.myown.damai.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exposes order ownership, snapshot, amount, and lifecycle fields to operators.
 */
public record AdminOrderResponse(
        Long id,
        Long orderNumber,
        Long userId,
        Long programId,
        String programTitle,
        BigDecimal orderPrice,
        Integer orderStatus,
        Instant expireTime,
        Instant createdAt,
        Instant paidAt
) {
}
