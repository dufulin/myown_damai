package com.myown.damai.pay.dao;

import com.myown.damai.pay.entity.PayOrderEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Defines persistence operations for local payment-to-order compensation events.
 */
public interface PayOrderEventDao {

    /**
     * Saves one event when its unique event key has not been recorded.
     */
    PayOrderEvent saveIfAbsent(PayOrderEvent event);

    /**
     * Finds one event by event key.
     */
    Optional<PayOrderEvent> findByEventKey(String eventKey);

    /**
     * Lists due events that are ready for delivery.
     */
    List<PayOrderEvent> listDueEvents(Instant now, int limit);

    /**
     * Claims one event for processing.
     */
    boolean tryMarkProcessing(String eventKey, Instant now);

    /**
     * Resets stale processing events to retrying status.
     */
    int resetStuckProcessing(Instant deadline, Instant now);

    /**
     * Marks one event as delivered successfully.
     */
    void markSucceeded(String eventKey);

    /**
     * Marks one event for a later retry.
     */
    void markRetrying(String eventKey, int retryCount, Instant nextRetryTime, String lastError);

    /**
     * Marks one event as dead after retry exhaustion.
     */
    void markDead(String eventKey, int retryCount, String lastError);
}
