package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Program;
import com.myown.damai.program.entity.ProgramTicketPriceRange;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exposes aggregated summary program data for list, search, and detail views.
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
        Instant issueTime,
        BigDecimal minTicketPrice,
        BigDecimal maxTicketPrice
) {

    /**
     * Builds a response from a program entity.
     */
    public static ProgramResponse from(Program program) {
        return from(program, null, null);
    }

    /**
     * Builds a response from a program entity and a persisted ticket price range.
     */
    public static ProgramResponse from(Program program, ProgramTicketPriceRange priceRange) {
        BigDecimal minTicketPrice = priceRange == null ? null : priceRange.minPrice;
        BigDecimal maxTicketPrice = priceRange == null ? null : priceRange.maxPrice;
        return from(program, minTicketPrice, maxTicketPrice);
    }

    /**
     * Builds a response from a program entity and explicit ticket price range values.
     */
    public static ProgramResponse from(Program program, BigDecimal minTicketPrice, BigDecimal maxTicketPrice) {
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
                program.issueTime,
                minTicketPrice,
                maxTicketPrice
        );
    }

    /**
     * Returns a copy with refreshed ticket price range values.
     */
    public ProgramResponse withTicketPriceRange(BigDecimal minTicketPrice, BigDecimal maxTicketPrice) {
        return new ProgramResponse(
                id,
                title,
                actor,
                place,
                itemPicture,
                areaId,
                programCategoryId,
                parentProgramCategoryId,
                permitChooseSeat,
                highHeat,
                issueTime,
                minTicketPrice,
                maxTicketPrice
        );
    }
}
