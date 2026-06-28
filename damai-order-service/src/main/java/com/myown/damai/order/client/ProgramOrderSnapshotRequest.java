package com.myown.damai.order.client;

import java.util.List;

/**
 * Carries order identifiers to the program service snapshot endpoint.
 */
public record ProgramOrderSnapshotRequest(
        Long showTimeId,
        List<Long> ticketCategoryIds
) {
}
