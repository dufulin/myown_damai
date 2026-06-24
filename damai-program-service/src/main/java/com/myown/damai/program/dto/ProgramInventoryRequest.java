package com.myown.damai.program.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Carries order inventory operation data for ticket category stock and seats.
 */
public record ProgramInventoryRequest(
        @NotNull Long orderNumber,
        @NotEmpty List<@Valid ProgramInventoryItemRequest> items
) {
}
