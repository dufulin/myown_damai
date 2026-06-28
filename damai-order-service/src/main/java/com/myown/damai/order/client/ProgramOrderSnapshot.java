package com.myown.damai.order.client;

import java.time.Instant;
import java.util.List;

/**
 * Carries trusted program display fields, show time, and ticket prices into order creation.
 */
public record ProgramOrderSnapshot(
        Long programId,
        Long showTimeId,
        String title,
        String place,
        String itemPicture,
        Instant showTime,
        Integer permitChooseSeat,
        List<ProgramTicketPriceSnapshot> ticketPrices
) {
}
