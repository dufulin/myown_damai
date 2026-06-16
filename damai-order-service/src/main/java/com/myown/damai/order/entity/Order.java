package com.myown.damai.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents the single-table order master record stored in d_order.
 */
public class Order {

    public Long id;
    public Long orderNumber;
    public Long programId;
    public String programItemPicture;
    public Long userId;
    public String programTitle;
    public String programPlace;
    public Instant programShowTime;
    public Integer programPermitChooseSeat;
    public String distributionMode;
    public String takeTicketMode;
    public BigDecimal orderPrice;
    public Integer payOrderType;
    public Integer orderStatus;
    public Instant expireTime;
    public Instant createOrderTime;
    public Instant cancelOrderTime;
    public Instant payOrderTime;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
