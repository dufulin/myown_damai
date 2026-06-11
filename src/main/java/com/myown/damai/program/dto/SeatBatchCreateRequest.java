package com.myown.damai.program.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Carries seat grid parameters for batch seat initialization.
 */
public record SeatBatchCreateRequest(
        @NotNull Long ticketCategoryId,
        @NotNull @Min(1) Integer startRow,
        @NotNull @Min(1) Integer endRow,
        @NotNull @Min(1) Integer startCol,
        @NotNull @Min(1) Integer endCol,
        @NotNull Integer seatType
) {
}
