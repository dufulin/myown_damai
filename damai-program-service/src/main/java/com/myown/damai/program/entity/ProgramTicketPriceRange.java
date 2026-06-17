package com.myown.damai.program.entity;

import java.math.BigDecimal;

/**
 * Represents the minimum and maximum ticket price for one program.
 */
public class ProgramTicketPriceRange {

    public Long programId;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;
}
