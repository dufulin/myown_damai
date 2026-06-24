package com.myown.damai.program.entity;

/**
 * Defines the seat selling status values stored in d_seat.sell_status.
 */
public enum SeatSellStatus {
    AVAILABLE(1),
    LOCKED(2),
    SOLD(3);

    private final int code;

    /**
     * Creates one seat selling status with its database code.
     */
    SeatSellStatus(int code) {
        this.code = code;
    }

    /**
     * Returns the database code for this seat selling status.
     */
    public int code() {
        return code;
    }
}
