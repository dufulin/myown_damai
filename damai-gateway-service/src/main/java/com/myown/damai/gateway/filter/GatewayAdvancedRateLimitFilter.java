package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Applies authenticated user, interface-type, and hot-program order limits after authentication.
 */
@Component
public class GatewayAdvancedRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAdvancedRateLimitFilter.class);

    private final GatewayRateLimitCounter rateLimitCounter;
    private final ObjectMapper objectMapper;
    private final boolean rateLimitEnabled;
    private final int userMaxRequests;
    private final long userWindowSeconds;
    private final int userReadMaxRequests;
    private final int userWriteMaxRequests;
    private final long userApiWindowSeconds;
    private final int orderUserMaxRequests;
    private final long orderUserWindowSeconds;
    private final int orderProgramMaxRequests;
    private final long orderProgramWindowSeconds;
    private final int orderUserProgramMaxRequests;
    private final long orderUserProgramWindowSeconds;
    private final int maxOrderBodyBytes;

    /**
     * Creates the authenticated rate limiter with user and hot-program thresholds.
     */
    public GatewayAdvancedRateLimitFilter(
            GatewayRateLimitCounter rateLimitCounter,
            ObjectMapper objectMapper,
            @Value("${damai.gateway.rate-limit-enabled:true}") boolean rateLimitEnabled,
            @Value("${damai.gateway.rate-limit.user.max-requests:120}") int userMaxRequests,
            @Value("${damai.gateway.rate-limit.user.window-seconds:60}") long userWindowSeconds,
            @Value("${damai.gateway.rate-limit.user-api.read-max-requests:120}") int userReadMaxRequests,
            @Value("${damai.gateway.rate-limit.user-api.write-max-requests:30}") int userWriteMaxRequests,
            @Value("${damai.gateway.rate-limit.user-api.window-seconds:60}") long userApiWindowSeconds,
            @Value("${damai.gateway.rate-limit.order-user.max-requests:5}") int orderUserMaxRequests,
            @Value("${damai.gateway.rate-limit.order-user.window-seconds:60}") long orderUserWindowSeconds,
            @Value("${damai.gateway.rate-limit.order-program.max-requests:200}") int orderProgramMaxRequests,
            @Value("${damai.gateway.rate-limit.order-program.window-seconds:1}") long orderProgramWindowSeconds,
            @Value("${damai.gateway.rate-limit.order-user-program.max-requests:3}") int orderUserProgramMaxRequests,
            @Value("${damai.gateway.rate-limit.order-user-program.window-seconds:10}") long orderUserProgramWindowSeconds,
            @Value("${damai.gateway.rate-limit.order-body-max-bytes:65536}") int maxOrderBodyBytes
    ) {
        this.rateLimitCounter = rateLimitCounter;
        this.objectMapper = objectMapper;
        this.rateLimitEnabled = rateLimitEnabled;
        this.userMaxRequests = userMaxRequests;
        this.userWindowSeconds = userWindowSeconds;
        this.userReadMaxRequests = userReadMaxRequests;
        this.userWriteMaxRequests = userWriteMaxRequests;
        this.userApiWindowSeconds = userApiWindowSeconds;
        this.orderUserMaxRequests = orderUserMaxRequests;
        this.orderUserWindowSeconds = orderUserWindowSeconds;
        this.orderProgramMaxRequests = orderProgramMaxRequests;
        this.orderProgramWindowSeconds = orderProgramWindowSeconds;
        this.orderUserProgramMaxRequests = orderUserProgramMaxRequests;
        this.orderUserProgramWindowSeconds = orderUserProgramWindowSeconds;
        this.maxOrderBodyBytes = maxOrderBodyBytes;
    }

    /**
     * Runs after authentication and authorization have established trusted identity headers.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }

    /**
     * Applies authenticated dimensions and reads programId only for order creation requests.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst(AuthenticatedUserHeader.USER_ID);
        if (shouldSkip(exchange, userId)) {
            return chain.filter(exchange);
        }
        if (isOrderCreateRequest(exchange)) {
            return filterOrderCreate(exchange, chain, userId);
        }
        return applyRules(exchange, chain, buildUserRules(exchange, userId, null));
    }

    /**
     * Caches the order body, extracts programId, and restores the same bytes for downstream use.
     */
    private Mono<Void> filterOrderCreate(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String userId
    ) {
        // The body is consumed only once in WebFlux, so the decorated request must replay the cached bytes.
        return DataBufferUtils.join(
                        exchange.getRequest().getBody(),
                        Math.max(1, maxOrderBodyBytes)
                )
                .defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
                .flatMap(buffer -> {
                    byte[] body = readAndRelease(buffer);
                    Long programId = resolveProgramId(body);
                    ServerWebExchange cachedExchange = decorateRequestBody(exchange, body);
                    return applyRules(
                            cachedExchange,
                            chain,
                            buildUserRules(cachedExchange, userId, programId)
                    );
                })
                .onErrorResume(
                        DataBufferLimitException.class,
                        exception -> rejectOversizedBody(exchange, userId)
                );
    }

    /**
     * Builds user, interface-type, and optional order-program dimensions.
     */
    private List<GatewayRateLimitRule> buildUserRules(
            ServerWebExchange exchange,
            String userId,
            Long programId
    ) {
        List<GatewayRateLimitRule> rules = new ArrayList<>();
        rules.add(new GatewayRateLimitRule(
                "user:global",
                userId,
                userMaxRequests,
                userWindowSeconds
        ));
        boolean readRequest = HttpMethod.GET.equals(exchange.getRequest().getMethod());
        String interfaceType = resolveInterfaceType(exchange);
        rules.add(new GatewayRateLimitRule(
                "user:api:" + interfaceType,
                userId,
                readRequest ? userReadMaxRequests : userWriteMaxRequests,
                userApiWindowSeconds
        ));
        if (isOrderCreateRequest(exchange)) {
            rules.add(new GatewayRateLimitRule(
                    "user:order-create",
                    userId,
                    orderUserMaxRequests,
                    orderUserWindowSeconds
            ));
            if (programId != null) {
                rules.add(new GatewayRateLimitRule(
                        "program:order-create",
                        String.valueOf(programId),
                        orderProgramMaxRequests,
                        orderProgramWindowSeconds
                ));
                rules.add(new GatewayRateLimitRule(
                        "user-program:order-create",
                        userId + ":" + programId,
                        orderUserProgramMaxRequests,
                        orderUserProgramWindowSeconds
                ));
            }
        }
        return List.copyOf(rules);
    }

    /**
     * Applies all rules and rejects the request when any dimension exceeds its threshold.
     */
    private Mono<Void> applyRules(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            List<GatewayRateLimitRule> rules
    ) {
        return rateLimitCounter.firstExceeded(rules)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(exceeded -> exceeded.isPresent()
                        ? rejectRequest(exchange, exceeded.get())
                        : chain.filter(exchange));
    }

    /**
     * Checks whether advanced limiting is irrelevant for this request.
     */
    private boolean shouldSkip(ServerWebExchange exchange, String userId) {
        String path = exchange.getRequest().getURI().getPath();
        return !rateLimitEnabled
                || !path.startsWith("/api/")
                || HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || !StringUtils.hasText(userId);
    }

    /**
     * Checks whether this request submits a new order.
     */
    private boolean isOrderCreateRequest(ServerWebExchange exchange) {
        return HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/api/orders".equals(exchange.getRequest().getURI().getPath());
    }

    /**
     * Resolves a stable interface category for independent per-user buckets.
     */
    private String resolveInterfaceType(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (isOrderCreateRequest(exchange)) {
            return "order-create";
        }
        if (path.startsWith("/api/programs")) {
            return HttpMethod.GET.equals(method) ? "program-read" : "program-write";
        }
        if (path.startsWith("/api/orders")) {
            return HttpMethod.GET.equals(method) ? "order-read" : "order-write";
        }
        if (path.startsWith("/api/pay")) {
            return "pay";
        }
        if (path.startsWith("/api/users")) {
            return HttpMethod.GET.equals(method) ? "user-read" : "user-write";
        }
        return "other";
    }

    /**
     * Copies cached request bytes out of a data buffer and releases pooled memory.
     */
    private byte[] readAndRelease(DataBuffer buffer) {
        byte[] body = new byte[buffer.readableByteCount()];
        buffer.read(body);
        DataBufferUtils.release(buffer);
        return body;
    }

    /**
     * Parses a positive numeric programId from the order JSON body.
     */
    private Long resolveProgramId(byte[] body) {
        try {
            JsonNode programIdNode = objectMapper.readTree(body).path("programId");
            if (programIdNode.canConvertToLong() && programIdNode.asLong() > 0L) {
                return programIdNode.asLong();
            }
        } catch (Exception exception) {
            LOGGER.debug("gateway order programId parse skipped because request body is invalid", exception);
        }
        return null;
    }

    /**
     * Decorates the request so downstream filters and services can read the cached body.
     */
    private ServerWebExchange decorateRequestBody(ServerWebExchange exchange, byte[] body) {
        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            /**
             * Replays the cached body for the remaining gateway chain.
             */
            @Override
            public Flux<DataBuffer> getBody() {
                return Flux.defer(() -> Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
            }
        };
        return exchange.mutate().request(decoratedRequest).build();
    }

    /**
     * Writes a 429 response with the exceeded dimension and retry window in logs and headers.
     */
    private Mono<Void> rejectRequest(
            ServerWebExchange exchange,
            GatewayRateLimitCounter.GatewayRateLimitResult exceeded
    ) {
        GatewayRateLimitRule rule = exceeded.rule();
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(Math.max(1L, rule.windowSeconds())));
        LOGGER.warn(
                "gateway advanced rate limit rejected, scope={}, subject={}, method={}, path={}, count={}, maxRequests={}",
                rule.scope(),
                rule.subject(),
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                exceeded.count(),
                rule.maxRequests()
        );
        return GatewayResponseWriter.writeError(
                exchange.getResponse(),
                objectMapper,
                HttpStatus.TOO_MANY_REQUESTS,
                "TOO_MANY_REQUESTS",
                "too many requests"
        );
    }

    /**
     * Rejects oversized order submissions before buffering excessive gateway memory.
     */
    private Mono<Void> rejectOversizedBody(ServerWebExchange exchange, String userId) {
        LOGGER.warn(
                "gateway order body rejected because it is too large, userId={}, maxBytes={}",
                userId,
                maxOrderBodyBytes
        );
        return GatewayResponseWriter.writeError(
                exchange.getResponse(),
                objectMapper,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "REQUEST_BODY_TOO_LARGE",
                "request body is too large"
        );
    }
}
