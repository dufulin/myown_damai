package com.myown.damai.order.dto;

import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import java.time.Instant;

/**
 * Exposes asynchronous order creation message tracking data.
 */
public record OrderAsyncMessageResponse(
        String messageKey,
        Long orderNumber,
        Long userId,
        Long programId,
        String topic,
        Integer retryCount,
        Integer maxRetryCount,
        Integer messageStatus,
        String messageStatusName,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Builds a response from one asynchronous message entity.
     */
    public static OrderAsyncMessageResponse from(OrderAsyncMessage message) {
        return new OrderAsyncMessageResponse(
                message.messageKey,
                message.orderNumber,
                message.userId,
                message.programId,
                message.topic,
                message.retryCount,
                message.maxRetryCount,
                message.messageStatus,
                OrderAsyncMessageStatus.nameOf(message.messageStatus),
                message.lastError,
                message.createdAt,
                message.updatedAt
        );
    }
}
