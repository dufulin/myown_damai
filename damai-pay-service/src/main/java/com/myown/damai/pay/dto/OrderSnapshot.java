package com.myown.damai.pay.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Holds the order fields needed by the payment service.
 */
public record OrderSnapshot(
        Long orderNumber,
        Long userId,
        String programTitle,
        BigDecimal orderPrice,
        Integer orderStatus,
        Instant payOrderTime
) {
}
