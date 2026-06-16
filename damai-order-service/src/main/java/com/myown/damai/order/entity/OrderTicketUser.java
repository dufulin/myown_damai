package com.myown.damai.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents one ticket-user detail row stored in d_order_ticket_user.
 */
public class OrderTicketUser {

    public Long id;
    public Long orderNumber;
    public Long programId;
    public Long userId;
    public Long ticketUserId;
    public Long seatId;
    public String seatInfo;
    public Long ticketCategoryId;
    public BigDecimal orderPrice;
    public BigDecimal payOrderPrice;
    public Integer payOrderType;
    public Integer orderStatus;
    public Instant createOrderTime;
    public Instant cancelOrderTime;
    public Instant payOrderTime;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
