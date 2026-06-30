package com.myown.damai.order.dto;

import com.myown.damai.order.client.ProgramOrderSnapshot;

/**
 * Carries an asynchronous order creation command through Kafka.
 */
public record OrderAsyncCreateMessage(
        String messageKey,
        Long orderNumber,
        OrderCreateRequest request,
        ProgramOrderSnapshot programSnapshot,
        String traceId
) {
}
