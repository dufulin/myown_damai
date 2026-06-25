package com.myown.damai.order.entity;

/**
 * Defines database status values for asynchronous order creation messages.
 */
public enum OrderAsyncMessageStatus {
    INIT(1),
    SENT(2),
    CONSUMING(3),
    SUCCEEDED(4),
    RETRYING(5),
    DEAD(6),
    SEND_FAILED(7);

    private final int code;

    /**
     * Creates one asynchronous message status with its database code.
     */
    OrderAsyncMessageStatus(int code) {
        this.code = code;
    }

    /**
     * Returns the database code for this status.
     */
    public int code() {
        return code;
    }

    /**
     * Resolves a database code to a status name.
     */
    public static String nameOf(Integer code) {
        if (code == null) {
            return "UNKNOWN";
        }
        for (OrderAsyncMessageStatus status : values()) {
            if (status.code == code) {
                return status.name();
            }
        }
        return "UNKNOWN";
    }
}
