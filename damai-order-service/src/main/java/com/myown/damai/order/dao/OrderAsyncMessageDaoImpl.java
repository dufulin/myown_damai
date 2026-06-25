package com.myown.damai.order.dao;

import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import com.myown.damai.order.mapper.OrderAsyncMessageMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Implements asynchronous order message tracking persistence with MyBatis.
 */
@Repository
public class OrderAsyncMessageDaoImpl implements OrderAsyncMessageDao {

    private static final int LAST_ERROR_MAX_LENGTH = 1024;

    private final OrderAsyncMessageMapper orderAsyncMessageMapper;

    /**
     * Creates the DAO with its MyBatis mapper.
     */
    public OrderAsyncMessageDaoImpl(OrderAsyncMessageMapper orderAsyncMessageMapper) {
        this.orderAsyncMessageMapper = orderAsyncMessageMapper;
    }

    /**
     * Saves one asynchronous message row and validates the generated id.
     */
    @Override
    public OrderAsyncMessage saveMessage(OrderAsyncMessage message) {
        orderAsyncMessageMapper.insertMessage(message);
        Objects.requireNonNull(message.id, "generated async message id must not be null");
        return message;
    }

    @Override
    public Optional<OrderAsyncMessage> findByMessageKey(String messageKey) {
        return Optional.ofNullable(orderAsyncMessageMapper.selectByMessageKey(messageKey));
    }

    @Override
    public Optional<OrderAsyncMessage> findLatestByOrderNumber(Long orderNumber) {
        return Optional.ofNullable(orderAsyncMessageMapper.selectLatestByOrderNumber(orderNumber));
    }

    @Override
    public void markSent(String messageKey, String topic) {
        orderAsyncMessageMapper.markSent(messageKey, topic, OrderAsyncMessageStatus.SENT.code(), Instant.now());
    }

    @Override
    public void markSendFailed(String messageKey, String lastError) {
        orderAsyncMessageMapper.markSendFailed(
                messageKey,
                OrderAsyncMessageStatus.SEND_FAILED.code(),
                trimLastError(lastError),
                Instant.now()
        );
    }

    @Override
    public boolean tryMarkConsuming(String messageKey) {
        return orderAsyncMessageMapper.tryMarkConsuming(
                messageKey,
                Arrays.asList(OrderAsyncMessageStatus.SENT.code(), OrderAsyncMessageStatus.RETRYING.code()),
                OrderAsyncMessageStatus.CONSUMING.code(),
                Instant.now()
        ) > 0;
    }

    @Override
    public void markSucceeded(String messageKey) {
        orderAsyncMessageMapper.markSucceeded(messageKey, OrderAsyncMessageStatus.SUCCEEDED.code(), Instant.now());
    }

    @Override
    public void markRetrying(String messageKey, int retryCount, String lastError) {
        orderAsyncMessageMapper.markRetrying(
                messageKey,
                OrderAsyncMessageStatus.RETRYING.code(),
                retryCount,
                trimLastError(lastError),
                Instant.now()
        );
    }

    @Override
    public void markDead(String messageKey, int retryCount, String lastError) {
        orderAsyncMessageMapper.markDead(
                messageKey,
                OrderAsyncMessageStatus.DEAD.code(),
                retryCount,
                trimLastError(lastError),
                Instant.now()
        );
    }

    /**
     * Trims error text to the database column length.
     */
    private String trimLastError(String lastError) {
        if (lastError == null || lastError.length() <= LAST_ERROR_MAX_LENGTH) {
            return lastError;
        }
        return lastError.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
