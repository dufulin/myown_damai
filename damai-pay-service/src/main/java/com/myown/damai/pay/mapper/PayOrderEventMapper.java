package com.myown.damai.pay.mapper;

import com.myown.damai.pay.entity.PayOrderEvent;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for local payment-to-order events.
 */
@Mapper
public interface PayOrderEventMapper {

    /**
     * Inserts one local payment event.
     */
    int insert(PayOrderEvent event);

    /**
     * Selects one active event by its unique key.
     */
    PayOrderEvent selectByEventKey(@Param("eventKey") String eventKey);

    /**
     * Lists due events that can be retried now.
     */
    List<PayOrderEvent> selectDueEvents(
            @Param("eventStatuses") List<Integer> eventStatuses,
            @Param("now") Instant now,
            @Param("limit") int limit
    );

    /**
     * Claims one due event for the current worker.
     */
    int tryMarkProcessing(
            @Param("eventKey") String eventKey,
            @Param("eventStatuses") List<Integer> eventStatuses,
            @Param("processingStatus") int processingStatus,
            @Param("now") Instant now
    );

    /**
     * Resets stale processing events so they can be retried after worker crashes.
     */
    int resetStuckProcessing(
            @Param("processingStatus") int processingStatus,
            @Param("retryingStatus") int retryingStatus,
            @Param("deadline") Instant deadline,
            @Param("now") Instant now
    );

    /**
     * Marks one event as successfully delivered.
     */
    int markSucceeded(
            @Param("eventKey") String eventKey,
            @Param("succeededStatus") int succeededStatus,
            @Param("now") Instant now
    );

    /**
     * Marks one event as waiting for retry.
     */
    int markRetrying(
            @Param("eventKey") String eventKey,
            @Param("retryingStatus") int retryingStatus,
            @Param("retryCount") int retryCount,
            @Param("nextRetryTime") Instant nextRetryTime,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );

    /**
     * Marks one event as dead after retry exhaustion.
     */
    int markDead(
            @Param("eventKey") String eventKey,
            @Param("deadStatus") int deadStatus,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );
}
