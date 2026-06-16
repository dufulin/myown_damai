package com.myown.damai.order.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Carries one ticket-user item when creating an order.
 */
public record OrderTicketUserRequest(
        @NotNull Long ticketUserId,
        Long seatId,
        String seatInfo,
        Long ticketCategoryId,
        @NotNull @DecimalMin("0") BigDecimal orderPrice
) {
}
