package com.myown.damai.order.client;

/**
 * Carries one ticket category and optional seat inventory operation item for the program service.
 */
public record ProgramInventoryItemRequest(
        Long ticketCategoryId,
        Long seatId
) {
}
