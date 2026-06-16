package com.myown.damai.order.dto;

/**
 * Carries optional data for canceling one order.
 */
public record OrderCancelRequest(
        String reason
) {
}
