package com.myown.damai.program.config;

import com.myown.damai.common.cache.RedisStringCacheClient;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Creates cache infrastructure beans for the program service.
 */
@Configuration
public class ProgramCacheConfig {

    /**
     * Builds the standardized Redis string cache client for program caches.
     */
    @Bean
    public RedisStringCacheClient programRedisStringCacheClient(
            StringRedisTemplate redisTemplate,
            MeterRegistry meterRegistry,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.cache.ttl-jitter-min-seconds:0}") long minJitterSeconds,
            @Value("${damai.cache.ttl-jitter-max-seconds:60}") long maxJitterSeconds
    ) {
        return new RedisStringCacheClient(
                redisTemplate,
                redisEnabled,
                minJitterSeconds,
                maxJitterSeconds,
                meterRegistry
        );
    }
}
