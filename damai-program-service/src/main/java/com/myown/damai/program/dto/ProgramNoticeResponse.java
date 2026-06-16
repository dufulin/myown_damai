package com.myown.damai.program.dto;

import com.myown.damai.program.entity.Program;

/**
 * Exposes ticket purchase notices and entry rules for a program detail page.
 */
public record ProgramNoticeResponse(
        String importantNotice,
        String preSellInstruction,
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
        String chooseSeatExplain
) {

    /**
     * Builds notice data from the program table columns.
     */
    public static ProgramNoticeResponse from(Program program) {
        return new ProgramNoticeResponse(
                program.importantNotice,
                program.preSellInstruction,
                program.refundTicketRule,
                program.deliveryInstruction,
                program.entryRule,
                program.childPurchase,
                program.invoiceSpecification,
                program.realTicketPurchaseRule,
                program.abnormalOrderDescription,
                program.kindReminder,
                program.performanceDuration,
                program.entryTime,
                program.refundExplain,
                program.chooseSeatExplain
        );
    }
}
