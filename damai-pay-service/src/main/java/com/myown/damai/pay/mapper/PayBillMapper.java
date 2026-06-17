package com.myown.damai.pay.mapper;

import com.myown.damai.pay.entity.PayBill;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for payment bills.
 */
@Mapper
public interface PayBillMapper {

    /**
     * Inserts one payment bill.
     */
    int insert(PayBill bill);

    /**
     * Selects one active bill by merchant order number.
     */
    PayBill selectByOutOrderNo(@Param("outOrderNo") String outOrderNo);

    /**
     * Marks one bill as paid.
     */
    int markPaid(
            @Param("outOrderNo") String outOrderNo,
            @Param("tradeNumber") String tradeNumber,
            @Param("payAmount") BigDecimal payAmount,
            @Param("payTime") Instant payTime,
            @Param("paidStatus") int paidStatus
    );
}
