package com.myown.damai.pay.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents one payment bill stored in the d_pay_bill table.
 */
public class PayBill {

    public Long id;
    public String payNumber;
    public String outOrderNo;
    public String payChannel;
    public String payScene;
    public String subject;
    public String tradeNumber;
    public BigDecimal payAmount;
    public Integer payBillType;
    public Integer payBillStatus;
    public Instant payTime;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
