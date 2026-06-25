package com.myown.damai.pay.service;

import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.pay.client.OrderClient;
import com.myown.damai.pay.dao.PayOrderEventDao;
import com.myown.damai.pay.dto.OrderPayRequest;
import com.myown.damai.pay.dto.PayOrderEventResponse;
import com.myown.damai.pay.entity.PayOrderEvent;
import com.myown.damai.pay.entity.PayOrderEventStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Delivers local payment events to the order service with retry and manual compensation support.
 */
@Service
public class PayOrderEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayOrderEventService.class);
    private static final String PAY_SUCCESS_EVENT_TYPE = "PAY_SUCCESS";

    private final PayOrderEventDao payOrderEventDao;
    private final OrderClient orderClient;
    private final boolean compensationEnabled;
    private final int scanLimit;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final Duration processingTimeout;

    /**
     * Creates the event delivery service with persistence, order client, and retry settings.
     */
    public PayOrderEventService(
            PayOrderEventDao payOrderEventDao,
            OrderClient orderClient,
            @Value("${damai.pay.event.compensation-enabled:true}") boolean compensationEnabled,
            @Value("${damai.pay.event.scan-limit:100}") int scanLimit,
            @Value("${damai.pay.event.retry-base-delay-seconds:30}") long retryBaseDelaySeconds,
            @Value("${damai.pay.event.retry-max-delay-seconds:300}") long retryMaxDelaySeconds,
            @Value("${damai.pay.event.processing-timeout-seconds:120}") long processingTimeoutSeconds
    ) {
        this.payOrderEventDao = payOrderEventDao;
        this.orderClient = orderClient;
        this.compensationEnabled = compensationEnabled;
        this.scanLimit = scanLimit;
        this.retryBaseDelay = Duration.ofSeconds(retryBaseDelaySeconds);
        this.retryMaxDelay = Duration.ofSeconds(retryMaxDelaySeconds);
        this.processingTimeout = Duration.ofSeconds(processingTimeoutSeconds);
    }

    /**
     * Periodically delivers due payment events to the order service.
     */
    @Scheduled(fixedDelayString = "${damai.pay.event.scan-delay-millis:30000}")
    public void scheduledCompensatePayOrderEvents() {
        if (!compensationEnabled) {
            return;
        }
        int processedCount = compensateDueEvents();
        if (processedCount > 0) {
            LOGGER.info("pay order event scheduled compensation processed, count={}", processedCount);
        }
    }

    /**
     * Delivers due local payment events and returns processed event count.
     */
    public int compensateDueEvents() {
        Instant now = Instant.now();
        int resetCount = payOrderEventDao.resetStuckProcessing(now.minus(processingTimeout), now);
        if (resetCount > 0) {
            LOGGER.warn("stuck pay order events reset for retry, count={}", resetCount);
        }
        List<PayOrderEvent> events = payOrderEventDao.listDueEvents(now, scanLimit);
        int processedCount = 0;
        for (PayOrderEvent event : events) {
            if (payOrderEventDao.tryMarkProcessing(event.eventKey, now)) {
                deliverEvent(event);
                processedCount++;
            }
        }
        return processedCount;
    }

    /**
     * Manually marks one failed event ready and tries to deliver due events immediately.
     */
    public int manualRetry(String eventKey) {
        PayOrderEvent event = payOrderEventDao.findByEventKey(eventKey)
                .orElseThrow(() -> new BusinessException("PAY_ORDER_EVENT_NOT_FOUND", "pay order event not found", HttpStatus.NOT_FOUND));
        if (PayOrderEventStatus.SUCCEEDED.code == event.eventStatus) {
            LOGGER.info("pay order event manual retry skipped because event succeeded, eventKey={}", eventKey);
            return 0;
        }
        payOrderEventDao.markRetrying(eventKey, event.retryCount, Instant.now(), null);
        LOGGER.info("pay order event manual retry requested, eventKey={}", eventKey);
        return compensateDueEvents();
    }

    /**
     * Gets one local payment event by its unique event key.
     */
    public PayOrderEventResponse getEvent(String eventKey) {
        PayOrderEvent event = payOrderEventDao.findByEventKey(eventKey)
                .orElseThrow(() -> new BusinessException("PAY_ORDER_EVENT_NOT_FOUND", "pay order event not found", HttpStatus.NOT_FOUND));
        return PayOrderEventResponse.from(event);
    }

    /**
     * Delivers one payment event to the order service and updates its local status.
     */
    private void deliverEvent(PayOrderEvent event) {
        try {
            if (!PAY_SUCCESS_EVENT_TYPE.equals(event.eventType)) {
                throw new BusinessException("PAY_ORDER_EVENT_TYPE_INVALID", "pay order event type is invalid", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            orderClient.markOrderPaid(event.orderNumber, new OrderPayRequest(event.tradeNumber, event.payAmount, event.payTime));
            payOrderEventDao.markSucceeded(event.eventKey);
            LOGGER.info("pay order event delivered, eventKey={}, orderNumber={}", event.eventKey, event.orderNumber);
        } catch (RuntimeException exception) {
            handleDeliveryFailure(event, exception);
        }
    }

    /**
     * Records retry or dead status after delivery failure.
     */
    private void handleDeliveryFailure(PayOrderEvent event, RuntimeException exception) {
        int nextRetryCount = event.retryCount + 1;
        String lastError = exception.getMessage();
        if (nextRetryCount >= event.maxRetryCount) {
            payOrderEventDao.markDead(event.eventKey, nextRetryCount, lastError);
            LOGGER.warn("pay order event dead after retry exhausted, eventKey={}, retryCount={}", event.eventKey, nextRetryCount, exception);
            return;
        }
        Instant nextRetryTime = Instant.now().plus(nextRetryDelay(nextRetryCount));
        payOrderEventDao.markRetrying(event.eventKey, nextRetryCount, nextRetryTime, lastError);
        LOGGER.warn(
                "pay order event delivery failed and will retry, eventKey={}, retryCount={}, nextRetryTime={}",
                event.eventKey,
                nextRetryCount,
                nextRetryTime,
                exception
        );
    }

    /**
     * Calculates a bounded linear backoff delay for the next retry.
     */
    private Duration nextRetryDelay(int retryCount) {
        // Keep retry delay predictable for operations while still spreading repeated failures over time.
        Duration delay = retryBaseDelay.multipliedBy(Math.max(retryCount, 1));
        return delay.compareTo(retryMaxDelay) > 0 ? retryMaxDelay : delay;
    }
}
