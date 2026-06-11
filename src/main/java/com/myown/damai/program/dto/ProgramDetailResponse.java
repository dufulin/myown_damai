package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Program;
import java.util.List;

/**
 * Exposes program detail with show times and ticket categories.
 */
public record ProgramDetailResponse(
        ProgramResponse program,
        String detail,
        ProgramNoticeResponse notices,
        List<ProgramShowTimeResponse> showTimes,
        List<TicketCategoryResponse> ticketCategories
) {

    /**
     * Builds a detail response from entity data.
     */
    public static ProgramDetailResponse of(
            Program program,
            List<ProgramShowTimeResponse> showTimes,
            List<TicketCategoryResponse> ticketCategories
    ) {
        return new ProgramDetailResponse(
                ProgramResponse.from(program),
                program.detail,
                ProgramNoticeResponse.from(program),
                showTimes,
                ticketCategories
        );
    }
}
