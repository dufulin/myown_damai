package com.myown.damai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.gateway.filter.GatewayAdvancedRateLimitFilter;
import com.myown.damai.gateway.filter.GatewayRateLimitCounter;
import com.myown.damai.gateway.filter.GatewayRateLimitFilter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies authenticated account and hot-program order rate limits.
 */
class GatewayAdvancedRateLimitFilterTest {

    private static final String USER_ID_HEADER = "X-Damai-User-Id";

    /**
     * Verifies the order endpoint has an IP bucket independent from the global IP threshold.
     */
    @Test
    void orderEndpointUsesDedicatedIpLimit() {
        GatewayRateLimitCounter counter = localCounter();
        GatewayRateLimitFilter filter = new GatewayRateLimitFilter(
                counter,
                new ObjectMapper(),
                true,
                100,
                60,
                100,
                60,
                100,
                60,
                2,
                60
        );
        AtomicInteger forwardedCount = new AtomicInteger();
        GatewayFilterChain chain = exchange -> {
            forwardedCount.incrementAndGet();
            return Mono.empty();
        };

        filter.filter(orderExchange("30001", "10.1.0.1", 40001L), chain).block();
        filter.filter(orderExchange("30002", "10.1.0.1", 40002L), chain).block();
        MockServerWebExchange rejected = orderExchange("30003", "10.1.0.1", 40003L);
        filter.filter(rejected, chain).block();

        assertEquals(2, forwardedCount.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getResponse().getStatusCode());
    }

    /**
     * Verifies one account cannot bypass its order limit by changing caller IP.
     */
    @Test
    void sameUserOrderRequestsAreLimitedAcrossIps() {
        GatewayAdvancedRateLimitFilter filter = createFilter(2, 100, 100);
        AtomicInteger forwardedCount = new AtomicInteger();
        AtomicReference<String> forwardedBody = new AtomicReference<>();
        GatewayFilterChain chain = bodyCapturingChain(forwardedCount, forwardedBody);

        filter.filter(orderExchange("10001", "10.0.0.1", 20001L), chain).block();
        filter.filter(orderExchange("10001", "10.0.0.2", 20002L), chain).block();
        MockServerWebExchange rejected = orderExchange("10001", "10.0.0.3", 20003L);
        filter.filter(rejected, chain).block();

        assertEquals(2, forwardedCount.get());
        assertTrue(forwardedBody.get().contains("\"programId\":20002"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getResponse().getStatusCode());
    }

    /**
     * Verifies one hot program is limited even when requests come from different accounts.
     */
    @Test
    void sameProgramOrderRequestsAreLimitedAcrossUsers() {
        GatewayAdvancedRateLimitFilter filter = createFilter(100, 2, 100);
        AtomicInteger forwardedCount = new AtomicInteger();
        GatewayFilterChain chain = bodyCapturingChain(forwardedCount, new AtomicReference<>());

        filter.filter(orderExchange("20001", "10.0.1.1", 30001L), chain).block();
        filter.filter(orderExchange("20002", "10.0.1.2", 30001L), chain).block();
        MockServerWebExchange rejected = orderExchange("20003", "10.0.1.3", 30001L);
        filter.filter(rejected, chain).block();

        assertEquals(2, forwardedCount.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.getResponse().getStatusCode());
        assertEquals("60", rejected.getResponse().getHeaders().getFirst("Retry-After"));
    }

    /**
     * Creates a local-fallback advanced filter with selected order thresholds.
     */
    private GatewayAdvancedRateLimitFilter createFilter(
            int orderUserMaxRequests,
            int orderProgramMaxRequests,
            int orderUserProgramMaxRequests
    ) {
        return new GatewayAdvancedRateLimitFilter(
                localCounter(),
                new ObjectMapper(),
                true,
                1000,
                60,
                1000,
                1000,
                60,
                orderUserMaxRequests,
                60,
                orderProgramMaxRequests,
                60,
                orderUserProgramMaxRequests,
                60,
                65536
        );
    }

    /**
     * Creates a shared counter configured to use its in-memory fallback.
     */
    private GatewayRateLimitCounter localCounter() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        return new GatewayRateLimitCounter(redisTemplate, false);
    }

    /**
     * Creates one authenticated order request with a program id and caller IP.
     */
    private MockServerWebExchange orderExchange(String userId, String clientIp, Long programId) {
        String body = "{\"programId\":" + programId + ",\"showTimeId\":1,\"ticketCategoryId\":1,\"ticketUserIds\":[1]}";
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/orders")
                .header(USER_ID_HEADER, userId)
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
        return MockServerWebExchange.from(request);
    }

    /**
     * Returns a downstream chain that records forwarding and consumes the restored body.
     */
    private GatewayFilterChain bodyCapturingChain(
            AtomicInteger forwardedCount,
            AtomicReference<String> forwardedBody
    ) {
        return exchange -> DataBufferUtils.join(exchange.getRequest().getBody())
                .doOnNext(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    forwardedBody.set(new String(bytes, StandardCharsets.UTF_8));
                    forwardedCount.incrementAndGet();
                })
                .then();
    }
}
