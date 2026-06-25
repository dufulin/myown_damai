package com.myown.damai.pay.dao;

import com.myown.damai.pay.entity.PayOrderEvent;
import com.myown.damai.pay.entity.PayOrderEventStatus;
import com.myown.damai.pay.mapper.PayOrderEventMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * Implements local payment event persistence with MyBatis.
 */
@Repository
public class PayOrderEventDaoImpl implements PayOrderEventDao {

    private static final int LAST_ERROR_MAX_LENGTH = 1024;

    private final PayOrderEventMapper payOrderEventMapper;

    /**
     * Creates the DAO with its MyBatis mapper.
     */
    public PayOrderEventDaoImpl(PayOrderEventMapper payOrderEventMapper) {
        this.payOrderEventMapper = payOrderEventMapper;
    }

    @Override
    public PayOrderEvent saveIfAbsent(PayOrderEvent event) {
        PayOrderEvent existingEvent = payOrderEventMapper.selectByEventKey(event.eventKey);
        if (existingEvent != null) {
            return existingEvent;
        }
        try {
            payOrderEventMapper.insert(event);
            Objects.requireNonNull(event.id, "generated pay order event id must not be null");
            return event;
        } catch (DuplicateKeyException exception) {
            // Concurrent duplicate callbacks should share the same local event instead of failing the payment transaction.
            return Objects.requireNonNull(payOrderEventMapper.selectByEventKey(event.eventKey), "existing pay order event must not be null");
        }
    }

    @Override
    public Optional<PayOrderEvent> findByEventKey(String eventKey) {
        return Optional.ofNullable(payOrderEventMapper.selectByEventKey(eventKey));
    }

    @Override
    public List<PayOrderEvent> listDueEvents(Instant now, int limit) {
        return payOrderEventMapper.selectDueEvents(
                Arrays.asList(PayOrderEventStatus.INIT.code, PayOrderEventStatus.RETRYING.code),
                now,
                limit
        );
    }

    @Override
    public boolean tryMarkProcessing(String eventKey, Instant now) {
        return payOrderEventMapper.tryMarkProcessing(
                eventKey,
                Arrays.asList(PayOrderEventStatus.INIT.code, PayOrderEventStatus.RETRYING.code),
                PayOrderEventStatus.PROCESSING.code,
                now
        ) > 0;
    }

    @Override
    public int resetStuckProcessing(Instant deadline, Instant now) {
        return payOrderEventMapper.resetStuckProcessing(
                PayOrderEventStatus.PROCESSING.code,
                PayOrderEventStatus.RETRYING.code,
                deadline,
                now
        );
    }

    @Override
    public void markSucceeded(String eventKey) {
        payOrderEventMapper.markSucceeded(eventKey, PayOrderEventStatus.SUCCEEDED.code, Instant.now());
    }

    @Override
    public void markRetrying(String eventKey, int retryCount, Instant nextRetryTime, String lastError) {
        payOrderEventMapper.markRetrying(
                eventKey,
                PayOrderEventStatus.RETRYING.code,
                retryCount,
                nextRetryTime,
                trimLastError(lastError),
                Instant.now()
        );
    }

    @Override
    public void markDead(String eventKey, int retryCount, String lastError) {
        payOrderEventMapper.markDead(
                eventKey,
                PayOrderEventStatus.DEAD.code,
                retryCount,
                trimLastError(lastError),
                Instant.now()
        );
    }

    /**
     * Trims the last error text so it fits the database column.
     */
    private String trimLastError(String lastError) {
        if (lastError == null || lastError.length() <= LAST_ERROR_MAX_LENGTH) {
            return lastError;
        }
        return lastError.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
