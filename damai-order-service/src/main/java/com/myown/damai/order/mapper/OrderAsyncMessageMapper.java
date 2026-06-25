package com.myown.damai.order.mapper;

import com.myown.damai.order.entity.OrderAsyncMessage;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Provides MyBatis operations for asynchronous order creation message ledger rows.
 */
@Mapper
public interface OrderAsyncMessageMapper {

    /**
     * Inserts one asynchronous message ledger row.
     */
    int insertMessage(OrderAsyncMessage message);

    /**
     * Selects one message by unique message key.
     */
    OrderAsyncMessage selectByMessageKey(@Param("messageKey") String messageKey);

    /**
     * Selects the latest message by order number.
     */
    OrderAsyncMessage selectLatestByOrderNumber(@Param("orderNumber") Long orderNumber);

    /**
     * Marks a message as sent to Kafka.
     */
    int markSent(
            @Param("messageKey") String messageKey,
            @Param("topic") String topic,
            @Param("sentStatus") int sentStatus,
            @Param("now") Instant now
    );

    /**
     * Marks a message as failed during send.
     */
    int markSendFailed(
            @Param("messageKey") String messageKey,
            @Param("sendFailedStatus") int sendFailedStatus,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );

    /**
     * Tries to claim a message for consumption from allowed states.
     */
    int tryMarkConsuming(
            @Param("messageKey") String messageKey,
            @Param("fromStatuses") List<Integer> fromStatuses,
            @Param("consumingStatus") int consumingStatus,
            @Param("now") Instant now
    );

    /**
     * Marks a message as successfully consumed.
     */
    int markSucceeded(
            @Param("messageKey") String messageKey,
            @Param("succeededStatus") int succeededStatus,
            @Param("now") Instant now
    );

    /**
     * Marks a message as waiting for retry.
     */
    int markRetrying(
            @Param("messageKey") String messageKey,
            @Param("retryingStatus") int retryingStatus,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );

    /**
     * Marks a message as dead after retries are exhausted.
     */
    int markDead(
            @Param("messageKey") String messageKey,
            @Param("deadStatus") int deadStatus,
            @Param("retryCount") int retryCount,
            @Param("lastError") String lastError,
            @Param("now") Instant now
    );
}
