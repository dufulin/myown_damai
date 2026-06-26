package com.myown.damai.program.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes program change events after the surrounding database transaction commits.
 */
@Component
public class ProgramChangeEventPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProgramChangeEventPublisher.class);

    private final ObjectProvider<ProgramChangeEventProducer> producerProvider;

    /**
     * Creates the publisher with an optional Kafka producer.
     */
    public ProgramChangeEventPublisher(ObjectProvider<ProgramChangeEventProducer> producerProvider) {
        this.producerProvider = producerProvider;
    }

    /**
     * Publishes an upsert event for a program detail document.
     */
    public void publishUpsert(Long programId, String reason) {
        publish(ProgramChangeEvent.of(programId, ProgramChangeType.UPSERT, reason));
    }

    /**
     * Publishes a delete event for a program detail document.
     */
    public void publishDelete(Long programId, String reason) {
        publish(ProgramChangeEvent.of(programId, ProgramChangeType.DELETE, reason));
    }

    /**
     * Registers the event so Kafka sees only committed program data.
     */
    private void publish(ProgramChangeEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                /**
                 * Sends the event after the database transaction commits.
                 */
                @Override
                public void afterCommit() {
                    sendSafely(event);
                }
            });
            return;
        }
        sendSafely(event);
    }

    /**
     * Sends one event when Kafka is enabled, otherwise logs a skipped synchronization.
     */
    private void sendSafely(ProgramChangeEvent event) {
        ProgramChangeEventProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            LOGGER.info("program change event skipped because Kafka is disabled, programId={}, changeType={}", event.programId(), event.changeType());
            return;
        }
        producer.send(event);
    }
}
