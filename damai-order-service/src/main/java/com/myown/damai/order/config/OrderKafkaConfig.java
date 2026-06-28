package com.myown.damai.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Creates Kafka infrastructure used by asynchronous order creation.
 */
@Configuration
@ConditionalOnProperty(value = "damai.order.async.enabled", havingValue = "true")
public class OrderKafkaConfig {

    /**
     * Creates the order creation topic when Kafka auto topic creation is not enough.
     */
    @Bean
    public NewTopic orderCreateTopic(
            @Value("${damai.order.async.create-topic:damai-order-create}") String topicName,
            @Value("${damai.order.async.topic-partitions:6}") int partitions
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    /**
     * Creates the retry topic for failed asynchronous order creation messages.
     */
    @Bean
    public NewTopic orderCreateRetryTopic(
            @Value("${damai.order.async.retry-topic:damai-order-create-retry}") String topicName,
            @Value("${damai.order.async.topic-partitions:6}") int partitions
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    /**
     * Creates the dead-letter topic for exhausted asynchronous order creation messages.
     */
    @Bean
    public NewTopic orderCreateDeadTopic(
            @Value("${damai.order.async.dead-topic:damai-order-create-dead}") String topicName,
            @Value("${damai.order.async.topic-partitions:6}") int partitions
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
