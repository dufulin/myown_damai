package com.myown.damai.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Carries data required to create one real-name ticket buyer.
 */
public record TicketUserCreateRequest(
        @NotBlank @Size(max = 256) String relName,
        @NotNull Integer idType,
        @NotBlank @Size(max = 512) String idNumber
) {
}
