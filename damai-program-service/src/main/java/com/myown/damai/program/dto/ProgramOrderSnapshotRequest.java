package com.myown.damai.program.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Carries identifiers required to resolve an authoritative program order snapshot.
 */
public record ProgramOrderSnapshotRequest(
        @NotNull Long showTimeId,
        @NotEmpty List<@NotNull Long> ticketCategoryIds
) {
}
