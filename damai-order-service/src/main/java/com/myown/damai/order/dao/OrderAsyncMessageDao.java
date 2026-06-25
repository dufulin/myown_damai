package com.myown.damai.order.dao;

import com.myown.damai.order.entity.OrderAsyncMessage;
import java.util.Optional;

/**
 * Defines persistence operations for asynchronous order creation message tracking.
 */
public interface OrderAsyncMessageDao {

    /**
     * Saves one asynchronous message row.
     */
    OrderAsyncMessage saveMessage(OrderAsyncMessage message);

    /**
     * Finds one asynchronous message by message key.
     */
    Optional<OrderAsyncMessage> findByMessageKey(String messageKey);

    /**
     * Finds the latest asynchronous message by order number.
     */
    Optional<OrderAsyncMessage> findLatestByOrderNumber(Long orderNumber);

    /**
     * Marks one message as sent to Kafka.
     */
    void markSent(String messageKey, String topic);

    /**
     * Marks one message as failed during Kafka send.
     */
    void markSendFailed(String messageKey, String lastError);

    /**
     * Tries to claim one message for consumption.
     */
    boolean tryMarkConsuming(String messageKey);

    /**
     * Marks one message as successfully consumed.
     */
    void markSucceeded(String messageKey);

    /**
     * Marks one message as waiting for retry.
     */
    void markRetrying(String messageKey, int retryCount, String lastError);

    /**
     * Marks one message as dead.
     */
    void markDead(String messageKey, int retryCount, String lastError);
}
