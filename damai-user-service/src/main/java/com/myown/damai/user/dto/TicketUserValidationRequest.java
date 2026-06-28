package com.myown.damai.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Carries one user's ticket buyer ids for an internal ownership validation.
 */
public record TicketUserValidationRequest(
        @NotNull Long userId,
        @NotEmpty List<@NotNull Long> ticketUserIds
) {
}
