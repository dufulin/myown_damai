package com.myown.damai.gateway.filter;

import com.myown.damai.common.cache.DamaiCacheKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Maintains shared gateway fixed-window counters in Redis with an in-memory fallback.
 */
@Component
public class GatewayRateLimitCounter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRateLimitCounter.class);
    private static final long LOCAL_CLEANUP_INTERVAL = 1024L;
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class
    );

    private final ReactiveStringRedisTemplate redisTemplate;
    private final boolean redisEnabled;
    private final ConcurrentHashMap<String, LocalRateCounter> localCounters = new ConcurrentHashMap<>();
    private final AtomicLong localIncrementSequence = new AtomicLong();

    /**
     * Creates the shared counter with Redis availability configuration.
     */
    public GatewayRateLimitCounter(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled
    ) {
        this.redisTemplate = redisTemplate;
        this.redisEnabled = redisEnabled;
    }

    /**
     * Increments rules in order and returns the first exceeded rule.
     */
    public Mono<GatewayRateLimitResult> firstExceeded(List<GatewayRateLimitRule> rules) {
        return Flux.fromIterable(rules)
                .concatMap(rule -> increaseRequestCount(rule)
                        .map(count -> new GatewayRateLimitResult(rule, count)))
                .filter(result -> result.count() > result.rule().maxRequests())
                .next();
    }

    /**
     * Increases one rule counter using Redis first and local memory as a fallback.
     */
    private Mono<Long> increaseRequestCount(GatewayRateLimitRule rule) {
        if (!redisEnabled) {
            return Mono.just(increaseLocalRequestCount(rule));
        }
        return increaseRedisRequestCount(rule)
                .onErrorResume(exception -> {
                    LOGGER.warn(
                            "gateway redis rate counter failed, scope={}, subject={}",
                            rule.scope(),
                            rule.subject(),
                            exception
                    );
                    return Mono.just(increaseLocalRequestCount(rule));
                });
    }

    /**
     * Increases one Redis fixed-window counter and attaches its expiry on first use.
     */
    private Mono<Long> increaseRedisRequestCount(GatewayRateLimitRule rule) {
        String key = rateLimitKey(rule);
        return redisTemplate.execute(
                        INCREMENT_WITH_EXPIRE_SCRIPT,
                        List.of(key),
                        List.of(String.valueOf(normalizeWindow(rule.windowSeconds())))
                )
                .next();
    }

    /**
     * Increases an in-memory fixed-window counter when Redis is disabled or unavailable.
     */
    private long increaseLocalRequestCount(GatewayRateLimitRule rule) {
        long windowSeconds = normalizeWindow(rule.windowSeconds());
        long currentWindow = currentWindow(windowSeconds);
        String localKey = rule.scope() + ":" + rule.subject() + ":" + windowSeconds;
        LocalRateCounter counter = localCounters.compute(localKey, (key, existingCounter) -> {
            if (existingCounter == null || existingCounter.window() != currentWindow) {
                return new LocalRateCounter(currentWindow, 1L, windowSeconds);
            }
            return new LocalRateCounter(currentWindow, existingCounter.count() + 1L, windowSeconds);
        });
        cleanupExpiredLocalCounters();
        return counter.count();
    }

    /**
     * Periodically removes expired local subjects so fallback mode remains memory bounded.
     */
    private void cleanupExpiredLocalCounters() {
        if (localIncrementSequence.incrementAndGet() % LOCAL_CLEANUP_INTERVAL != 0L) {
            return;
        }
        localCounters.entrySet().removeIf(entry -> {
            LocalRateCounter counter = entry.getValue();
            return counter.window() != currentWindow(counter.windowSeconds());
        });
    }

    /**
     * Builds a normalized Redis key for one rule and fixed time window.
     */
    private String rateLimitKey(GatewayRateLimitRule rule) {
        long windowSeconds = normalizeWindow(rule.windowSeconds());
        return DamaiCacheKey.of(
                "gateway",
                "rate",
                rule.scope(),
                rule.subject(),
                currentWindow(windowSeconds)
        );
    }

    /**
     * Returns the current fixed-window bucket number.
     */
    private long currentWindow(long windowSeconds) {
        return Instant.now().getEpochSecond() / normalizeWindow(windowSeconds);
    }

    /**
     * Prevents invalid configuration from producing a zero-length time window.
     */
    private long normalizeWindow(long windowSeconds) {
        return Math.max(1L, windowSeconds);
    }

    /**
     * Stores one local fixed-window request count.
     */
    private record LocalRateCounter(long window, long count, long windowSeconds) {
    }

    /**
     * Describes the first rate limit dimension exceeded by a request.
     */
    public record GatewayRateLimitResult(GatewayRateLimitRule rule, long count) {
    }
}
