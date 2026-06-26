package com.myown.damai.user.config;

import com.myown.damai.common.cache.RedisStringCacheClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Creates cache infrastructure beans for the user service.
 */
@Configuration
public class UserCacheConfig {

    /**
     * Builds the standardized Redis string cache client for user caches.
     */
    @Bean
    public RedisStringCacheClient userRedisStringCacheClient(
            StringRedisTemplate redisTemplate,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.cache.ttl-jitter-min-seconds:0}") long minJitterSeconds,
            @Value("${damai.cache.ttl-jitter-max-seconds:60}") long maxJitterSeconds
    ) {
        return new RedisStringCacheClient(redisTemplate, redisEnabled, minJitterSeconds, maxJitterSeconds);
    }
}
