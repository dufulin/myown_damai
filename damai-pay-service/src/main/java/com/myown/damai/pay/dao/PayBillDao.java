package com.myown.damai.pay.dao;

import com.myown.damai.pay.entity.PayBill;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Defines payment bill persistence operations.
 */
public interface PayBillDao {

    /**
     * Finds one payment bill by merchant order number.
     */
    Optional<PayBill> findByOutOrderNo(String outOrderNo);

    /**
     * Saves one payment bill.
     */
    PayBill save(PayBill bill);

    /**
     * Marks one payment bill as paid.
     */
    void markPaid(String outOrderNo, String tradeNumber, BigDecimal payAmount, Instant payTime, int paidStatus);
}
