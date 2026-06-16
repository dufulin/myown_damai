package com.myown.damai.program.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * Carries data for creating a program with show times and ticket categories.
 */
public record ProgramCreateRequest(
        @NotNull Long areaId,
        @NotNull Long programCategoryId,
        @NotNull Long parentProgramCategoryId,
        @NotBlank @Size(max = 512) String title,
        @Size(max = 256) String actor,
        @Size(max = 100) String place,
        String itemPicture,
        @NotBlank String detail,
        Instant issueTime,
        Integer preSell,
        String preSellInstruction,
        String importantNotice,
        String refundTicketRule,
        String deliveryInstruction,
        String entryRule,
        String childPurchase,
        String invoiceSpecification,
        String realTicketPurchaseRule,
        String abnormalOrderDescription,
        String kindReminder,
        String performanceDuration,
        String entryTime,
        String refundExplain,
        String chooseSeatExplain,
        Integer permitChooseSeat,
        Integer highHeat,
        @NotEmpty List<@Valid ProgramShowTimeRequest> showTimes,
        @NotEmpty List<@Valid TicketCategoryRequest> ticketCategories
) {
}
