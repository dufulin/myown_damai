package com.myown.damai.program.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Carries one ticket price tier for a program.
 */
public record TicketCategoryRequest(
        @NotBlank @Size(max = 256) String introduce,
        @NotNull @Min(0) BigDecimal price,
        @NotNull @Min(0) Long totalNumber
) {
}
