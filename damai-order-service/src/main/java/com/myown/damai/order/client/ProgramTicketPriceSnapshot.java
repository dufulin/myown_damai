package com.myown.damai.order.client;

import java.math.BigDecimal;

/**
 * Carries one trusted ticket category price returned by the program service.
 */
public record ProgramTicketPriceSnapshot(
        Long ticketCategoryId,
        BigDecimal price
) {
}
