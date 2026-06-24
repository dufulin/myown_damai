package com.myown.damai.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.order.dto.OrderAsyncCreateMessage;
import com.myown.damai.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
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

    /**
     * Creates the consumer with JSON parsing and order service dependencies.
     */
    public OrderAsyncConsumer(ObjectMapper objectMapper, OrderService orderService) {
        this.objectMapper = objectMapper;
        this.orderService = orderService;
    }

    /**
     * Handles one Kafka order creation message.
     */
    @KafkaListener(topics = "${damai.order.async.create-topic:damai-order-create}")
    public void consumeCreateOrderMessage(String payload) {
        try {
            OrderAsyncCreateMessage message = objectMapper.readValue(payload, OrderAsyncCreateMessage.class);
            LOGGER.info("order async create message received, orderNumber={}", message.orderNumber());
            orderService.createOrderFromAsyncMessage(message);
            LOGGER.info("order async create message handled, orderNumber={}", message.orderNumber());
        } catch (JsonProcessingException exception) {
            LOGGER.warn("order async create message parse failed", exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("order async create message handle failed", exception);
            throw exception;
        }
    }
}
