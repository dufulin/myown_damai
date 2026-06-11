package com.myown.damai.program.dto;

import com.myown.damai.program.entity.TicketCategory;
import java.math.BigDecimal;

/**
 * Exposes one ticket category.
 */
public record TicketCategoryResponse(
        Long id,
        String introduce,
        BigDecimal price,
        Long totalNumber,
        Long remainNumber
) {

    /**
     * Builds a response from a ticket category entity.
     */
    public static TicketCategoryResponse from(TicketCategory ticketCategory) {
        return new TicketCategoryResponse(
                ticketCategory.id,
                ticketCategory.introduce,
                ticketCategory.price,
                ticketCategory.totalNumber,
                ticketCategory.remainNumber
        );
    }
}
