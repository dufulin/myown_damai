package com.myown.damai.pay.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries payment confirmation data to the order service.
 */
public record OrderPayRequest(
        String tradeNumber,
        BigDecimal payAmount,
        Instant payTime
) {
}
