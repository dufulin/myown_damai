package com.myown.damai.program.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Carries one program show time.
 */
public record ProgramShowTimeRequest(
        @NotNull Instant showTime,
        Long areaId
) {
}
