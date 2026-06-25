package com.myown.damai.order.dto;

/**
 * Carries an asynchronous order creation command through Kafka.
 */
public record OrderAsyncCreateMessage(
        String messageKey,
        Long orderNumber,
        OrderCreateRequest request
) {
}
