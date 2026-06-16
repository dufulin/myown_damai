package com.myown.damai.program.entity;

import java.time.Instant;

/**
 * Represents the core program record stored in d_program.
 */
public class Program {

    public Long id;
    public Long programGroupId;
    public Integer prime;
    public Long areaId;
    public Long programCategoryId;
    public Long parentProgramCategoryId;
    public String title;
    public String actor;
    public String place;
    public String itemPicture;
    public Integer preSell;
    public String preSellInstruction;
    public String importantNotice;
    public String detail;
    public Integer perOrderLimitPurchaseCount;
    public Integer perAccountLimitPurchaseCount;
    public String refundTicketRule;
    public String deliveryInstruction;
    public String entryRule;
    public String childPurchase;
    public String invoiceSpecification;
    public String realTicketPurchaseRule;
    public String abnormalOrderDescription;
    public String kindReminder;
    public String performanceDuration;
    public String entryTime;
    public Integer minPerformanceCount;
    public String mainActor;
    public String minPerformanceDuration;
    public String prohibitedItem;
    public String depositSpecification;
    public Long totalCount;
    public Integer permitRefund;
    public String refundExplain;
    public Integer relNameTicketEntrance;
    public String relNameTicketEntranceExplain;
    public Integer permitChooseSeat;
    public String chooseSeatExplain;
    public Integer electronicDeliveryTicket;
    public String electronicDeliveryTicketExplain;
    public Integer electronicInvoice;
    public String electronicInvoiceExplain;
    public Integer highHeat;
    public Integer programStatus;
    public Instant issueTime;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
