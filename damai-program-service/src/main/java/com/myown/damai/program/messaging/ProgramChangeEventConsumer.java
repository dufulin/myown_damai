package com.myown.damai.program.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.observability.TraceContext;
import com.myown.damai.program.service.ProgramService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes program change events and synchronizes Elasticsearch asynchronously.
 */
@Component
@ConditionalOnProperty(value = "damai.program.change.kafka-enabled", havingValue = "true")
public class ProgramChangeEventConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramChangeEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProgramService programService;

    /**
     * Creates the consumer with JSON parsing and program search synchronization dependencies.
     */
    public ProgramChangeEventConsumer(ObjectMapper objectMapper, ProgramService programService) {
        this.objectMapper = objectMapper;
        this.programService = programService;
    }

    /**
     * Handles one program change event from Kafka.
     */
    @KafkaListener(
            topics = "${damai.program.change.topic:damai-program-change}",
            groupId = "${damai.program.change.consumer-group:damai-program-search-sync}"
    )
    public void handleProgramChange(
            String payload,
            @Header(name = KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey
    ) throws Exception {
        ProgramChangeEvent event = objectMapper.readValue(payload, ProgramChangeEvent.class);
        try (TraceContext.Scope ignored = TraceContext.open(event.traceId(), null, null, event.programId())) {
            LOGGER.info("program change event received, topic={}, kafkaKey={}, programId={}, changeType={}, reason={}", topic, kafkaKey, event.programId(), event.changeType(), event.reason());
            if (ProgramChangeType.DELETE.equals(event.changeType())) {
                programService.deleteProgramDetailFromSearchIndex(event.programId());
                return;
            }
            programService.syncProgramDetailToSearchIndex(event.programId());
        }
    }
}
