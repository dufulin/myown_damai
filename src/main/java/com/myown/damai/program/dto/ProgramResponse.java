package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Program;
import java.time.Instant;

/**
 * Exposes summary program data.
 */
public record ProgramResponse(
        Long id,
        String title,
        String actor,
        String place,
        String itemPicture,
        Long areaId,
        Long programCategoryId,
        Long parentProgramCategoryId,
        Integer permitChooseSeat,
        Integer highHeat,
        Instant issueTime
) {

    /**
     * Builds a response from a program entity.
     */
    public static ProgramResponse from(Program program) {
        return new ProgramResponse(
                program.id,
                program.title,
                program.actor,
                program.place,
                program.itemPicture,
                program.areaId,
                program.programCategoryId,
                program.parentProgramCategoryId,
                program.permitChooseSeat,
                program.highHeat,
                program.issueTime
        );
    }
}
