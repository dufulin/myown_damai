package com.myown.damai.order.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Carries data required to create one unpaid order.
 */
public record OrderCreateRequest(
        @NotNull Long programId,
        @NotNull Long showTimeId,
        @NotNull Long ticketCategoryId,
        Long userId,
        @NotEmpty List<@NotNull Long> ticketUserIds
) {

    /**
     * Returns a copy of the request with the authenticated user id supplied by the gateway.
     */
    public OrderCreateRequest withUserId(Long authenticatedUserId) {
        return new OrderCreateRequest(
                programId,
                showTimeId,
                ticketCategoryId,
                authenticatedUserId,
                ticketUserIds
        );
    }
}
