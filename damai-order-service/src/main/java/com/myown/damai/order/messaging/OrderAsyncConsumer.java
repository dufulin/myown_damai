package com.myown.damai.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.observability.TraceContext;
import com.myown.damai.order.dao.OrderAsyncMessageDao;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import com.myown.damai.order.entity.OrderAsyncMessage;
import com.myown.damai.order.entity.OrderAsyncMessageStatus;
import com.myown.damai.order.service.OrderService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes Kafka messages and creates orders asynchronously.
 */
@Component
@ConditionalOnProperty(value = "damai.order.async.enabled", havingValue = "true")
public class OrderAsyncConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderAsyncConsumer.class);

    private final ObjectMapper objectMapper;
    private final OrderService orderService;
    private final OrderAsyncMessageDao orderAsyncMessageDao;
    private final OrderAsyncProducer orderAsyncProducer;
    private final int maxRetryCount;

    /**
     * Creates the consumer with JSON parsing and order service dependencies.
     */
    public OrderAsyncConsumer(
            ObjectMapper objectMapper,
            OrderService orderService,
            OrderAsyncMessageDao orderAsyncMessageDao,
            OrderAsyncProducer orderAsyncProducer,
            @Value("${damai.order.async.max-retry-count:3}") int maxRetryCount
    ) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
        this.orderAsyncMessageDao = orderAsyncMessageDao;
        this.orderAsyncProducer = orderAsyncProducer;
        this.maxRetryCount = maxRetryCount;
    }

    /**
     * Handles one Kafka order creation message.
     */
    @KafkaListener(topics = {
            "${damai.order.async.create-topic:damai-order-create}",
            "${damai.order.async.retry-topic:damai-order-create-retry}"
    }, concurrency = "${damai.order.async.consumer-concurrency:3}")
    public void consumeCreateOrderMessage(
            String payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey,
            @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(name = KafkaHeaders.OFFSET, required = false) Long offset
    ) {
        try {
            OrderAsyncCreateMessage message = objectMapper.readValue(payload, OrderAsyncCreateMessage.class);
            try (TraceContext.Scope ignored = TraceContext.open(
                    message.traceId(),
                    message.request().userId(),
                    message.orderNumber(),
                    message.request().programId()
            )) {
                processMessage(message, payload, topic, kafkaKey, partition, offset);
            }
        } catch (JsonProcessingException exception) {
            handleInvalidPayload(payload, kafkaKey, exception);
        } catch (RuntimeException exception) {
            handleMessageFailure(payload, exception);
        }
    }

    /**
     * Processes one decoded order message inside its restored trace context.
     */
    private void processMessage(
            OrderAsyncCreateMessage message,
            String payload,
            String topic,
            String kafkaKey,
            Integer partition,
            Long offset
    ) {
        ensureMessageTracked(message, payload, topic);
        if (!claimMessage(message)) {
            return;
        }
        LOGGER.info(
                "order async create message received, topic={}, kafkaKey={}, partition={}, offset={}, messageKey={}, orderNumber={}, programId={}",
                topic,
                kafkaKey,
                partition,
                offset,
                message.messageKey(),
                message.orderNumber(),
                message.request().programId()
        );
        orderService.createOrderFromAsyncMessage(message);
        orderAsyncMessageDao.markSucceeded(message.messageKey());
        LOGGER.info("order async create message handled, messageKey={}, orderNumber={}", message.messageKey(), message.orderNumber());
    }

    /**
     * Ensures a consumed Kafka message has a database tracking row.
     */
    private void ensureMessageTracked(OrderAsyncCreateMessage message, String payload, String topic) {
        Optional<OrderAsyncMessage> foundMessage = orderAsyncMessageDao.findByMessageKey(message.messageKey());
        if (foundMessage.isPresent()) {
            return;
        }
        Instant now = Instant.now();
        OrderAsyncMessage asyncMessage = new OrderAsyncMessage();
        asyncMessage.messageKey = message.messageKey();
        asyncMessage.orderNumber = message.orderNumber();
        asyncMessage.userId = message.request().userId();
        asyncMessage.programId = message.request().programId();
        asyncMessage.topic = topic;
        asyncMessage.retryCount = 0;
        asyncMessage.maxRetryCount = maxRetryCount;
        asyncMessage.messageStatus = OrderAsyncMessageStatus.SENT.code();
        asyncMessage.payload = payload;
        asyncMessage.createdAt = now;
        asyncMessage.updatedAt = now;
        asyncMessage.status = 1;
        orderAsyncMessageDao.saveMessage(asyncMessage);
    }

    /**
     * Claims a message for consumption or skips already-finished duplicate messages.
     */
    private boolean claimMessage(OrderAsyncCreateMessage message) {
        Optional<OrderAsyncMessage> foundMessage = orderAsyncMessageDao.findByMessageKey(message.messageKey());
        if (foundMessage.isPresent() && OrderAsyncMessageStatus.SUCCEEDED.code() == foundMessage.get().messageStatus) {
            LOGGER.info("order async duplicate message skipped because already succeeded, messageKey={}", message.messageKey());
            return false;
        }
        boolean claimed = orderAsyncMessageDao.tryMarkConsuming(message.messageKey());
        if (!claimed) {
            LOGGER.info("order async duplicate message skipped because status is not consumable, messageKey={}", message.messageKey());
        }
        return claimed;
    }

    /**
     * Sends an invalid JSON payload to dead letter topic.
     */
    private void handleInvalidPayload(String payload, String kafkaKey, JsonProcessingException exception) {
        String messageKey = kafkaKey == null ? "invalid-order-create:" + UUID.randomUUID() : kafkaKey;
        LOGGER.warn("order async create message parse failed, messageKey={}", messageKey, exception);
        orderAsyncProducer.sendDeadLetterMessage(messageKey, payload);
    }

    /**
     * Handles a business or infrastructure failure with retry and dead-letter routing.
     */
    private void handleMessageFailure(String payload, RuntimeException exception) {
        try {
            OrderAsyncCreateMessage message = objectMapper.readValue(payload, OrderAsyncCreateMessage.class);
            try (TraceContext.Scope ignored = TraceContext.open(
                    message.traceId(),
                    message.request().userId(),
                    message.orderNumber(),
                    message.request().programId()
            )) {
                OrderAsyncMessage asyncMessage = orderAsyncMessageDao.findByMessageKey(message.messageKey())
                        .orElseThrow(() -> exception);
                int nextRetryCount = asyncMessage.retryCount + 1;
                if (nextRetryCount >= asyncMessage.maxRetryCount) {
                    orderAsyncMessageDao.markDead(message.messageKey(), nextRetryCount, exception.getMessage());
                    orderService.clearAsyncPendingOrder(message);
                    orderAsyncProducer.sendDeadLetterMessage(message.messageKey(), payload);
                    LOGGER.warn("order async create message moved to dead letter, messageKey={}, retryCount={}", message.messageKey(), nextRetryCount, exception);
                    return;
                }
                orderAsyncMessageDao.markRetrying(message.messageKey(), nextRetryCount, exception.getMessage());
                orderAsyncProducer.sendRetryOrderMessage(message.messageKey(), payload);
                LOGGER.warn("order async create message scheduled for retry, messageKey={}, retryCount={}", message.messageKey(), nextRetryCount, exception);
            }
        } catch (JsonProcessingException parseException) {
            handleInvalidPayload(payload, null, parseException);
        }
    }
}
