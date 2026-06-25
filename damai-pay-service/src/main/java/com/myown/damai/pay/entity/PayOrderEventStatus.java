package com.myown.damai.pay.entity;

/**
 * Defines local payment event statuses for order notification compensation.
 */
public enum PayOrderEventStatus {

    INIT(1),
    PROCESSING(2),
    SUCCEEDED(3),
    RETRYING(4),
    DEAD(5);

    public final int code;

    /**
     * Creates one event status with its database code.
     */
    PayOrderEventStatus(int code) {
        this.code = code;
    }

    /**
     * Returns the status name for one database code.
     */
    public static String nameOf(Integer code) {
        if (code == null) {
            return "UNKNOWN";
        }
        for (PayOrderEventStatus status : values()) {
            if (status.code == code) {
                return status.name();
            }
        }
        return "UNKNOWN";
    }
}
