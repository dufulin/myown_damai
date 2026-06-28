package com.myown.damai.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.exception.BusinessException;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes asynchronous order creation messages to Kafka.
 */
@Component
@ConditionalOnProperty(value = "damai.order.async.enabled", havingValue = "true")
public class OrderAsyncProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderAsyncProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String createTopic;
    private final String retryTopic;
    private final String deadTopic;
    private final long sendTimeoutSeconds;

    /**
     * Creates the producer with Kafka and JSON serialization dependencies.
     */
    public OrderAsyncProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${damai.order.async.create-topic:damai-order-create}") String createTopic,
            @Value("${damai.order.async.retry-topic:damai-order-create-retry}") String retryTopic,
            @Value("${damai.order.async.dead-topic:damai-order-create-dead}") String deadTopic,
            @Value("${damai.order.async.send-timeout-seconds:10}") long sendTimeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.createTopic = createTopic;
        this.retryTopic = retryTopic;
        this.deadTopic = deadTopic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * Sends one order creation command to Kafka.
     */
    public void sendCreateOrderMessage(OrderAsyncCreateMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            String kafkaKey = programPartitionKey(message);
            sendPayload(createTopic, kafkaKey, message.messageKey(), payload);
            LOGGER.info("order async create message sent, topic={}, kafkaKey={}, messageKey={}, orderNumber={}", createTopic, kafkaKey, message.messageKey(), message.orderNumber());
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ORDER_ASYNC_MESSAGE_INVALID", "order async message serialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Sends one failed order creation message to the retry topic.
     */
    public void sendRetryOrderMessage(String messageKey, String payload) {
        String kafkaKey = programPartitionKey(payload, messageKey);
        sendPayload(retryTopic, kafkaKey, messageKey, payload);
        LOGGER.info("order async retry message sent, topic={}, kafkaKey={}, messageKey={}", retryTopic, kafkaKey, messageKey);
    }

    /**
     * Sends one exhausted or invalid order creation message to the dead-letter topic.
     */
    public void sendDeadLetterMessage(String messageKey, String payload) {
        String kafkaKey = programPartitionKey(payload, messageKey);
        sendPayload(deadTopic, kafkaKey, messageKey, payload);
        LOGGER.info("order async dead-letter message sent, topic={}, kafkaKey={}, messageKey={}", deadTopic, kafkaKey, messageKey);
    }

    /**
     * Sends one serialized payload to Kafka and waits for broker acknowledgement.
     */
    private void sendPayload(String topic, String kafkaKey, String messageKey, String payload) {
        try {
            kafkaTemplate.send(topic, kafkaKey, payload).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("order async message send interrupted, topic={}, kafkaKey={}, messageKey={}", topic, kafkaKey, messageKey, exception);
            throw new BusinessException("ORDER_ASYNC_SEND_INTERRUPTED", "order async message send interrupted", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ExecutionException | TimeoutException exception) {
            LOGGER.warn("order async message send failed, topic={}, kafkaKey={}, messageKey={}", topic, kafkaKey, messageKey, exception);
            throw new BusinessException("ORDER_ASYNC_SEND_FAILED", "order async message send failed", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            LOGGER.warn("order async message send failed, topic={}, kafkaKey={}, messageKey={}", topic, kafkaKey, messageKey, exception);
            throw new BusinessException("ORDER_ASYNC_SEND_FAILED", "order async message send failed", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Builds the Kafka partition key so one program is processed in partition order.
     */
    private String programPartitionKey(OrderAsyncCreateMessage message) {
        return "program:" + message.request().programId();
    }

    /**
     * Resolves the Kafka partition key from payload and falls back to the message key for invalid payloads.
     */
    private String programPartitionKey(String payload, String fallbackKey) {
        try {
            OrderAsyncCreateMessage message = objectMapper.readValue(payload, OrderAsyncCreateMessage.class);
            return programPartitionKey(message);
        } catch (JsonProcessingException exception) {
            return fallbackKey;
        }
    }
}
