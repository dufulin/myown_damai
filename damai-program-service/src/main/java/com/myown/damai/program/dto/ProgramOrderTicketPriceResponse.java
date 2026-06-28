package com.myown.damai.program.dto;

import java.math.BigDecimal;

/**
 * Exposes one database-authoritative ticket category price for order creation.
 */
public record ProgramOrderTicketPriceResponse(
        Long ticketCategoryId,
        BigDecimal price
) {
}
