package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Limits frequent API requests from the same IP before they reach downstream services.
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRateLimitFilter.class);
    private static final String RATE_KEY_PREFIX = "damai:gateway:rate:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean rateLimitEnabled;
    private final boolean redisEnabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, LocalRateCounter> localCounters = new ConcurrentHashMap<>();

    /**
     * Creates the rate limit filter with Redis and local fallback settings.
     */
    public GatewayRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${damai.gateway.rate-limit-enabled:true}") boolean rateLimitEnabled,
            @Value("${damai.cache.redis-enabled:true}") boolean redisEnabled,
            @Value("${damai.gateway.rate-limit.max-requests:120}") int maxRequests,
            @Value("${damai.gateway.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.rateLimitEnabled = rateLimitEnabled;
        this.redisEnabled = redisEnabled;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Returns the filter order so rate limiting runs before authentication.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Counts requests for the caller IP and rejects requests over the configured limit.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        return increaseRequestCount(clientIp)
                .flatMap(count -> {
                    if (count > maxRequests) {
                        LOGGER.warn(
                                "gateway rate limit rejected, ip={}, method={}, path={}, count={}, maxRequests={}",
                                clientIp,
                                exchange.getRequest().getMethod(),
                                exchange.getRequest().getURI().getPath(),
                                count,
                                maxRequests
                        );
                        return GatewayResponseWriter.writeError(
                                exchange.getResponse(),
                                objectMapper,
                                HttpStatus.TOO_MANY_REQUESTS,
                                "TOO_MANY_REQUESTS",
                                "too many requests"
                        );
                    }
                    LOGGER.debug("gateway rate limit passed, ip={}, count={}", clientIp, count);
                    return chain.filter(exchange);
                });
    }

    /**
     * Checks whether rate limiting should be skipped for this request.
     */
    private boolean shouldSkip(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        return !rateLimitEnabled
                || !path.startsWith("/api/")
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod());
    }

    /**
     * Increases the request counter using Redis first and local memory as a fallback.
     */
    private Mono<Long> increaseRequestCount(String clientIp) {
        if (!redisEnabled) {
            return Mono.just(increaseLocalRequestCount(clientIp));
        }
        return increaseRedisRequestCount(clientIp)
                .onErrorResume(exception -> {
                    LOGGER.warn("gateway redis rate counter failed, ip={}", clientIp, exception);
                    return Mono.just(increaseLocalRequestCount(clientIp));
                });
    }

    /**
     * Increases the Redis fixed-window counter for one IP.
     */
    private Mono<Long> increaseRedisRequestCount(String clientIp) {
        String key = rateLimitKey(clientIp);
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1L) {
                        return redisTemplate.expire(key, Duration.ofSeconds(windowSeconds)).thenReturn(count);
                    }
                    return Mono.just(count);
                });
    }

    /**
     * Increases an in-memory fixed-window counter when Redis is disabled or unavailable.
     */
    private long increaseLocalRequestCount(String clientIp) {
        long currentWindow = currentWindow();
        LocalRateCounter counter = localCounters.compute(clientIp, (ip, existingCounter) -> {
            if (existingCounter == null || existingCounter.window() != currentWindow) {
                return new LocalRateCounter(currentWindow, 1L);
            }
            return new LocalRateCounter(currentWindow, existingCounter.count() + 1L);
        });
        return counter.count();
    }

    /**
     * Builds a Redis key for the current fixed time window.
     */
    private String rateLimitKey(String clientIp) {
        return RATE_KEY_PREFIX + clientIp + ":" + currentWindow();
    }

    /**
     * Returns the current fixed-window bucket number.
     */
    private long currentWindow() {
        return Instant.now().getEpochSecond() / windowSeconds;
    }

    /**
     * Resolves the caller IP, honoring common reverse-proxy headers.
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            // X-Forwarded-For can contain a proxy chain; the first IP is the original caller.
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(address -> address.getHostAddress())
                .orElse("unknown");
    }

    /**
     * Stores one local fixed-window request count.
     */
    private record LocalRateCounter(long window, long count) {
    }
}
