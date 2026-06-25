package com.myown.damai.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Carries data required to create one unpaid order.
 */
public record OrderCreateRequest(
        @NotNull Long programId,
        String programItemPicture,
        Long userId,
        @Size(max = 512) String programTitle,
        @Size(max = 100) String programPlace,
        Instant programShowTime,
        Integer programPermitChooseSeat,
        @Size(max = 256) String distributionMode,
        @Size(max = 256) String takeTicketMode,
        Integer payOrderType,
        @NotEmpty List<@Valid OrderTicketUserRequest> ticketUsers
) {

    /**
     * Returns a copy of the request with the authenticated user id supplied by the gateway.
     */
    public OrderCreateRequest withUserId(Long authenticatedUserId) {
        return new OrderCreateRequest(
                programId,
                programItemPicture,
                authenticatedUserId,
                programTitle,
                programPlace,
                programShowTime,
                programPermitChooseSeat,
                distributionMode,
                takeTicketMode,
                payOrderType,
                ticketUsers
        );
    }
}
