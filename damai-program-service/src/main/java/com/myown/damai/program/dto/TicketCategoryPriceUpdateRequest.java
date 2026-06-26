package com.myown.damai.program.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Carries a ticket category price update.
 */
public record TicketCategoryPriceUpdateRequest(
        @NotNull @Min(0) BigDecimal price
) {
}
