package com.myown.damai.pay.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents one local payment event used to notify the order service asynchronously.
 */
public class PayOrderEvent {

    public Long id;
    public String eventKey;
    public String outOrderNo;
    public Long orderNumber;
    public String tradeNumber;
    public BigDecimal payAmount;
    public Instant payTime;
    public String eventType;
    public Integer eventStatus;
    public Integer retryCount;
    public Integer maxRetryCount;
    public Instant nextRetryTime;
    public String lastError;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
