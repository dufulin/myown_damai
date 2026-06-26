package com.myown.damai.program.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates Kafka infrastructure used by program change synchronization.
 */
@Configuration
@ConditionalOnProperty(value = "damai.program.change.kafka-enabled", havingValue = "true")
public class ProgramKafkaConfig {

    /**
     * Creates the program change topic when broker auto topic creation is not enough.
     */
    @Bean
    public NewTopic programChangeTopic(
            @Value("${damai.program.change.topic:damai-program-change}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
