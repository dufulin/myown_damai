package com.myown.damai.order.entity;

import java.time.Instant;

/**
 * Represents the reliable message ledger row for asynchronous order creation.
 */
public class OrderAsyncMessage {

    public Long id;
    public String messageKey;
    public Long orderNumber;
    public Long userId;
    public Long programId;
    public String topic;
    public Integer retryCount;
    public Integer maxRetryCount;
    public Integer messageStatus;
    public String payload;
    public String lastError;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
