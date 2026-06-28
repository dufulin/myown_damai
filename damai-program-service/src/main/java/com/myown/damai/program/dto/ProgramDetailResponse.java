package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Program;
import java.math.BigDecimal;
import java.util.Comparator;
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
        BigDecimal minTicketPrice = ticketCategories.stream()
                .map(TicketCategoryResponse::price)
                .min(Comparator.naturalOrder())
                .orElse(null);
        BigDecimal maxTicketPrice = ticketCategories.stream()
                .map(TicketCategoryResponse::price)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new ProgramDetailResponse(
                ProgramResponse.from(program, minTicketPrice, maxTicketPrice),
                program.detail,
                ProgramNoticeResponse.from(program),
                showTimes,
                ticketCategories
        );
    }
}
