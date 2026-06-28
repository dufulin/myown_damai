package com.myown.damai.order.client;

import java.util.List;

/**
 * Carries ticket buyer ids to the user service for ownership validation.
 */
public record TicketUserValidationRequest(
        Long userId,
        List<Long> ticketUserIds
) {
}
