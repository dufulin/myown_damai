package com.myown.damai.program.messaging;

import java.time.Instant;

/**
 * Carries a program change that should be reflected in Elasticsearch.
 */
public record ProgramChangeEvent(
        String messageKey,
        Long programId,
        ProgramChangeType changeType,
        String reason,
        Instant occurredAt
) {

    /**
     * Builds a new program change event with a stable Kafka key prefix.
     */
    public static ProgramChangeEvent of(Long programId, ProgramChangeType changeType, String reason) {
        Instant now = Instant.now();
        String messageKey = "program-change:" + changeType.name().toLowerCase() + ":" + programId + ":" + now.toEpochMilli();
        return new ProgramChangeEvent(messageKey, programId, changeType, reason, now);
    }
}
