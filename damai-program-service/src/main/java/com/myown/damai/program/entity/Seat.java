package com.myown.damai.program.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Represents a sellable seat under a ticket category.
 */
public class Seat {

    public Long id;
    public Long programId;
    public Long ticketCategoryId;
    public Integer rowCode;
    public Integer colCode;
    public Integer seatType;
    public BigDecimal price;
    public Integer sellStatus;
    public Instant createdAt;
    public Instant updatedAt;
    public Integer status;
}
