package com.myown.damai.pay.dto;

import com.myown.damai.pay.entity.PayBill;
import com.myown.damai.pay.entity.PayBillStatus;
import java.math.BigDecimal;

/**
 * Exposes payment bill data and the generated Alipay payment form.
 */
public record PagePayResponse(
        Long payBillId,
        String payNumber,
        String outOrderNo,
        String payChannel,
        BigDecimal payAmount,
        Integer payBillStatus,
        String payBillStatusName,
        String payForm
) {

    /**
     * Builds a response from a payment bill and generated payment form.
     */
    public static PagePayResponse of(PayBill bill, String payForm) {
        return new PagePayResponse(
                bill.id,
                bill.payNumber,
                bill.outOrderNo,
                bill.payChannel,
                bill.payAmount,
                bill.payBillStatus,
                PayBillStatus.fromCode(bill.payBillStatus).name(),
                payForm
        );
    }
}
