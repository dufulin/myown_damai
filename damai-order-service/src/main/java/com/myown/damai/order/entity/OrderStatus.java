package com.myown.damai.order.entity;

import com.myown.damai.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * Defines stable order status codes used by the order tables.
 */
public enum OrderStatus {

    UNPAID(1),
    CANCELED(2),
    PAID(3),
    REFUNDED(4);

    private final int code;

    /**
     * Creates an enum value with its database code.
     */
    OrderStatus(int code) {
        this.code = code;
    }

    /**
     * Returns the database status code.
     */
    public int code() {
        return code;
    }

    /**
     * Converts a database code to an order status.
     */
    public static OrderStatus fromCode(Integer code) {
        for (OrderStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new BusinessException("ORDER_STATUS_INVALID", "order status is invalid", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
