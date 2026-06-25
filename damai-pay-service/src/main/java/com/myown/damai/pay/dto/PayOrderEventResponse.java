package com.myown.damai.pay.dto;

import com.myown.damai.pay.entity.PayOrderEvent;
import com.myown.damai.pay.entity.PayOrderEventStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exposes local payment event status for compensation inspection.
 */
public record PayOrderEventResponse(
        Long id,
        String eventKey,
        String outOrderNo,
        Long orderNumber,
        String tradeNumber,
        BigDecimal payAmount,
        Instant payTime,
        String eventType,
        Integer eventStatus,
        String eventStatusName,
        Integer retryCount,
        Integer maxRetryCount,
        Instant nextRetryTime,
        String lastError
) {

    /**
     * Builds a response from one local payment event entity.
     */
    public static PayOrderEventResponse from(PayOrderEvent event) {
        return new PayOrderEventResponse(
                event.id,
                event.eventKey,
                event.outOrderNo,
                event.orderNumber,
                event.tradeNumber,
                event.payAmount,
                event.payTime,
                event.eventType,
                event.eventStatus,
                PayOrderEventStatus.nameOf(event.eventStatus),
                event.retryCount,
                event.maxRetryCount,
                event.nextRetryTime,
                event.lastError
        );
    }
}
