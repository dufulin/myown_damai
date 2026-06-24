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
    private final long sendTimeoutSeconds;

    /**
     * Creates the producer with Kafka and JSON serialization dependencies.
     */
    public OrderAsyncProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${damai.order.async.create-topic:damai-order-create}") String createTopic,
            @Value("${damai.order.async.send-timeout-seconds:10}") long sendTimeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.createTopic = createTopic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * Sends one order creation command to Kafka.
     */
    public void sendCreateOrderMessage(OrderAsyncCreateMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(createTopic, String.valueOf(message.orderNumber()), payload).get(sendTimeoutSeconds, TimeUnit.SECONDS);
            LOGGER.info("order async create message sent, topic={}, orderNumber={}", createTopic, message.orderNumber());
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ORDER_ASYNC_MESSAGE_INVALID", "order async message serialization failed", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("order async create message send interrupted, topic={}, orderNumber={}", createTopic, message.orderNumber(), exception);
            throw new BusinessException("ORDER_ASYNC_SEND_INTERRUPTED", "order async message send interrupted", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ExecutionException | TimeoutException exception) {
            LOGGER.warn("order async create message send failed, topic={}, orderNumber={}", createTopic, message.orderNumber(), exception);
            throw new BusinessException("ORDER_ASYNC_SEND_FAILED", "order async message send failed", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (RuntimeException exception) {
            LOGGER.warn("order async create message send failed, topic={}, orderNumber={}", createTopic, message.orderNumber(), exception);
            throw new BusinessException("ORDER_ASYNC_SEND_FAILED", "order async message send failed", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
