package com.myown.damai.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Provides standardized Redis string cache operations with null markers, TTL jitter, and mutex rebuilds.
 */
public class RedisStringCacheClient {

    public static final String NULL_VALUE = "__NULL__";

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisStringCacheClient.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final long minJitterSeconds;
    private final long maxJitterSeconds;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Counter cacheErrorCounter;

    /**
     * Creates the cache client with Redis access and jitter settings.
     */
    public RedisStringCacheClient(
            StringRedisTemplate redisTemplate,
            boolean enabled,
            long minJitterSeconds,
            long maxJitterSeconds,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.minJitterSeconds = Math.max(0, minJitterSeconds);
        this.maxJitterSeconds = Math.max(this.minJitterSeconds, maxJitterSeconds);
        this.cacheHitCounter = buildCacheCounter(meterRegistry, "hit");
        this.cacheMissCounter = buildCacheCounter(meterRegistry, "miss");
        this.cacheErrorCounter = buildCacheCounter(meterRegistry, "error");
    }

    /**
     * Reads one raw string value from Redis.
     */
    public Optional<String> get(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                cacheMissCounter.increment();
            } else {
                cacheHitCounter.increment();
            }
            return Optional.ofNullable(value);
        } catch (RuntimeException exception) {
            cacheErrorCounter.increment();
            LOGGER.warn("redis get failed, key={}", key, exception);
            return Optional.empty();
        }
    }

    /**
     * Writes one raw string value with a jittered TTL.
     */
    public void put(String key, String value, Duration ttl) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, withJitter(ttl));
        } catch (RuntimeException exception) {
            LOGGER.warn("redis set failed, key={}", key, exception);
        }
    }

    /**
     * Writes a short-lived null marker to protect the database from invalid-id penetration.
     */
    public void putNull(String key, Duration ttl) {
        put(key, NULL_VALUE, ttl);
    }

    /**
     * Deletes one cache key.
     */
    public void delete(String key) {
        if (!enabled) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn("redis delete failed, key={}", key, exception);
        }
    }

    /**
     * Checks whether a raw value is the standardized null marker.
     */
    public boolean isNullValue(String value) {
        return NULL_VALUE.equals(value);
    }

    /**
     * Rebuilds a missing cache item under a Redis mutex lock.
     */
    public <T> T rebuildWithMutex(
            String lockKey,
            Duration lockTtl,
            Duration waitTimeout,
            Duration retryInterval,
            Supplier<Optional<T>> cachedReader,
            Supplier<T> loader
    ) {
        if (!enabled) {
            return loader.get();
        }
        String lockToken = UUID.randomUUID().toString();
        if (tryAcquireLock(lockKey, lockToken, lockTtl)) {
            try {
                Optional<T> refreshedCache = cachedReader.get();
                return refreshedCache.orElseGet(loader);
            } finally {
                releaseLock(lockKey, lockToken);
            }
        }
        return waitForRebuiltCache(waitTimeout, retryInterval, cachedReader, loader);
    }

    /**
     * Adds a random positive jitter to avoid many keys expiring at the same time.
     */
    public Duration withJitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || maxJitterSeconds == 0) {
            return ttl;
        }
        long jitterSeconds = ThreadLocalRandom.current().nextLong(minJitterSeconds, maxJitterSeconds + 1);
        return ttl.plusSeconds(jitterSeconds);
    }

    /**
     * Attempts to acquire one Redis mutex lock.
     */
    private boolean tryAcquireLock(String lockKey, String lockToken, Duration lockTtl) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, lockTtl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException exception) {
            LOGGER.warn("redis lock acquire failed, key={}", lockKey, exception);
            return false;
        }
    }

    /**
     * Releases a Redis mutex lock only when the token still belongs to this caller.
     */
    private void releaseLock(String lockKey, String lockToken) {
        try {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
        } catch (RuntimeException exception) {
            LOGGER.warn("redis lock release failed, key={}", lockKey, exception);
        }
    }

    /**
     * Waits briefly for another caller to rebuild the cache before falling back to the loader.
     */
    private <T> T waitForRebuiltCache(
            Duration waitTimeout,
            Duration retryInterval,
            Supplier<Optional<T>> cachedReader,
            Supplier<T> loader
    ) {
        long deadline = System.nanoTime() + waitTimeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<T> cachedValue = cachedReader.get();
            if (cachedValue.isPresent()) {
                return cachedValue.get();
            }
            if (!sleepBeforeRetry(retryInterval)) {
                break;
            }
        }
        // If the rebuilding request failed or took too long, this caller loads once to keep the API available.
        return loader.get();
    }

    /**
     * Sleeps between cache rebuild polling attempts.
     */
    private boolean sleepBeforeRetry(Duration retryInterval) {
        try {
            Thread.sleep(Math.max(1, retryInterval.toMillis()));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Builds one Redis request counter used to calculate cache hit and error ratios.
     */
    private Counter buildCacheCounter(MeterRegistry meterRegistry, String result) {
        return Counter.builder("damai.cache.requests")
                .description("Damai Redis cache read outcomes")
                .tag("result", result)
                .register(meterRegistry);
    }
}
