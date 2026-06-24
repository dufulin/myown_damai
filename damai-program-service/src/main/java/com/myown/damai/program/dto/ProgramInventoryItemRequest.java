package com.myown.damai.program.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Carries one ticket category and optional seat inventory operation item.
 */
public record ProgramInventoryItemRequest(
        @NotNull Long ticketCategoryId,
        Long seatId
) {
}
