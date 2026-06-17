package com.myown.damai.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Creates the Redisson client used by program-level distributed locks.
 */
@Configuration
@ConditionalOnProperty(value = "damai.order.lock.redisson-enabled", havingValue = "true", matchIfMissing = true)
public class OrderRedissonConfig {

    /**
     * Builds a single-server Redisson client from Redis connection settings.
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient orderRedissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database
    ) {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setPassword(StringUtils.hasText(password) ? password : null);
        return Redisson.create(config);
    }
}
