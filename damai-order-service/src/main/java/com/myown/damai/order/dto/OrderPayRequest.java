package com.myown.damai.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Carries payment data used to mark an unpaid order as paid.
 */
public record OrderPayRequest(
        @NotBlank String tradeNumber,
        @NotNull BigDecimal payAmount,
        Instant payTime
) {
}
