package com.myown.damai.program.dto;

import com.myown.damai.program.entity.ProgramShowTime;
import java.time.Instant;

/**
 * Exposes one program show time.
 */
public record ProgramShowTimeResponse(
        Long id,
        Instant showTime,
        Instant showDayTime,
        String showWeekTime,
        Long areaId
) {

    /**
     * Builds a response from a show time entity.
     */
    public static ProgramShowTimeResponse from(ProgramShowTime showTime) {
        return new ProgramShowTimeResponse(
                showTime.id,
                showTime.showTime,
                showTime.showDayTime,
                showTime.showWeekTime,
                showTime.areaId
        );
    }
}
