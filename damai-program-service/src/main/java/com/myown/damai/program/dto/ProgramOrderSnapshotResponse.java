package com.myown.damai.program.dto;

import java.time.Instant;
import java.util.List;

/**
 * Exposes trusted program display data, show time, and prices for order snapshots.
 */
public record ProgramOrderSnapshotResponse(
        Long programId,
        Long showTimeId,
        String title,
        String place,
        String itemPicture,
        Instant showTime,
        Integer permitChooseSeat,
        List<ProgramOrderTicketPriceResponse> ticketPrices
) {
}
