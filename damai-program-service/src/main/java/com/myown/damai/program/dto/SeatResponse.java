package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Seat;
import java.math.BigDecimal;

/**
 * Exposes one seat.
 */
public record SeatResponse(
        Long id,
        Long ticketCategoryId,
        Integer rowCode,
        Integer colCode,
        Integer seatType,
        BigDecimal price,
        Integer sellStatus
) {

    /**
     * Builds a response from a seat entity.
     */
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.id,
                seat.ticketCategoryId,
                seat.rowCode,
                seat.colCode,
                seat.seatType,
                seat.price,
                seat.sellStatus
        );
    }
}
