package com.myown.damai.program.dto;

import com.myown.damai.program.entity.ProgramCategory;

/**
 * Exposes one program category.
 */
public record ProgramCategoryResponse(
        Long id,
        Long parentId,
        String name,
        Integer type
) {

    /**
     * Builds a response from a category entity.
     */
    public static ProgramCategoryResponse from(ProgramCategory category) {
        return new ProgramCategoryResponse(category.id, category.parentId, category.name, category.type);
    }
}
