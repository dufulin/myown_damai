package com.myown.damai.pay.dao;

import com.myown.damai.pay.entity.PayBill;
import com.myown.damai.pay.mapper.PayBillMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Implements payment bill persistence with MyBatis.
 */
@Repository
public class PayBillDaoImpl implements PayBillDao {

    private final PayBillMapper payBillMapper;

    /**
     * Creates the DAO with its MyBatis mapper.
     */
    public PayBillDaoImpl(PayBillMapper payBillMapper) {
        this.payBillMapper = payBillMapper;
    }

    @Override
    public Optional<PayBill> findByOutOrderNo(String outOrderNo) {
        return Optional.ofNullable(payBillMapper.selectByOutOrderNo(outOrderNo));
    }

    @Override
    public PayBill save(PayBill bill) {
        payBillMapper.insert(bill);
        Objects.requireNonNull(bill.id, "generated pay bill id must not be null");
        return bill;
    }

    @Override
    public void markPaid(String outOrderNo, String tradeNumber, BigDecimal payAmount, Instant payTime, int paidStatus) {
        payBillMapper.markPaid(outOrderNo, tradeNumber, payAmount, payTime, paidStatus);
    }
}
