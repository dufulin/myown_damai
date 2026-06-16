package com.myown.damai.program.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a ticket price tier for a program.
 */
public class TicketCategory {

    public Long id;
    public Long programId;
    public String introduce;
    public BigDecimal price;
    public Long totalNumber;
    public Long remainNumber;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
