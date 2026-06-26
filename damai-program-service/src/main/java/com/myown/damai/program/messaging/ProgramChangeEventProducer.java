package com.myown.damai.program.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes program change events to Kafka.
 */
@Component
@ConditionalOnProperty(value = "damai.program.change.kafka-enabled", havingValue = "true")
public class ProgramChangeEventProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramChangeEventProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long sendTimeoutSeconds;

    /**
     * Creates the producer with Kafka and JSON serialization dependencies.
     */
    public ProgramChangeEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${damai.program.change.topic:damai-program-change}") String topic,
            @Value("${damai.program.change.send-timeout-seconds:5}") long sendTimeoutSeconds
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    /**
     * Sends one program change event and waits for broker acknowledgement.
     */
    public void send(ProgramChangeEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.messageKey(), payload).get(sendTimeoutSeconds, TimeUnit.SECONDS);
            LOGGER.info("program change event sent, topic={}, messageKey={}, programId={}, changeType={}", topic, event.messageKey(), event.programId(), event.changeType());
        } catch (JsonProcessingException exception) {
            LOGGER.warn("program change event serialization failed, programId={}", event.programId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("program change event send interrupted, topic={}, messageKey={}", topic, event.messageKey(), exception);
        } catch (ExecutionException | TimeoutException | RuntimeException exception) {
            LOGGER.warn("program change event send failed, topic={}, messageKey={}", topic, event.messageKey(), exception);
        }
    }
}
