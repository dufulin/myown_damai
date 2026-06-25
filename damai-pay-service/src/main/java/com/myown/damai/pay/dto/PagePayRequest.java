package com.myown.damai.pay.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Carries data required to create an Alipay page payment.
 */
public record PagePayRequest(
        @NotNull Long orderNumber
) {
}
