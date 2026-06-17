package com.myown.damai.pay.entity;

/**
 * Defines payment bill statuses stored in d_pay_bill.
 */
public enum PayBillStatus {
    UNPAID(1),
    PAID(2),
    CLOSED(3);

    public final int code;

    /**
     * Creates a payment bill status with its database code.
     */
    PayBillStatus(int code) {
        this.code = code;
    }

    /**
     * Resolves a status by its database code.
     */
    public static PayBillStatus fromCode(Integer code) {
        if (code == null) {
            return UNPAID;
        }
        for (PayBillStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return UNPAID;
    }
}
