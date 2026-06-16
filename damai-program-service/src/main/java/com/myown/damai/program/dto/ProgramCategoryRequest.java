package com.myown.damai.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Carries data for creating a program category.
 */
public record ProgramCategoryRequest(
        @NotNull Long parentId,
        @NotBlank @Size(max = 120) String name,
        @NotNull Integer type
) {
}
