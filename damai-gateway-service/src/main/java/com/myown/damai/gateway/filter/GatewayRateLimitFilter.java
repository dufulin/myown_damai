package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Applies global, interface-type, and hot-order IP limits before authentication.
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRateLimitFilter.class);

    private final GatewayRateLimitCounter rateLimitCounter;
    private final ObjectMapper objectMapper;
    private final boolean rateLimitEnabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final int apiMaxRequests;
    private final long apiWindowSeconds;
    private final int authIpMaxRequests;
    private final long authIpWindowSeconds;
    private final int orderIpMaxRequests;
    private final long orderIpWindowSeconds;

    /**
     * Creates the rate limit filter with Redis and local fallback settings.
     */
    public GatewayRateLimitFilter(
            GatewayRateLimitCounter rateLimitCounter,
            ObjectMapper objectMapper,
            @Value("${damai.gateway.rate-limit-enabled:true}") boolean rateLimitEnabled,
            @Value("${damai.gateway.rate-limit.max-requests:120}") int maxRequests,
            @Value("${damai.gateway.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${damai.gateway.rate-limit.api.max-requests:120}") int apiMaxRequests,
            @Value("${damai.gateway.rate-limit.api.window-seconds:60}") long apiWindowSeconds,
            @Value("${damai.gateway.rate-limit.auth-ip.max-requests:20}") int authIpMaxRequests,
            @Value("${damai.gateway.rate-limit.auth-ip.window-seconds:60}") long authIpWindowSeconds,
            @Value("${damai.gateway.rate-limit.order-ip.max-requests:20}") int orderIpMaxRequests,
            @Value("${damai.gateway.rate-limit.order-ip.window-seconds:60}") long orderIpWindowSeconds
    ) {
        this.rateLimitCounter = rateLimitCounter;
        this.objectMapper = objectMapper;
        this.rateLimitEnabled = rateLimitEnabled;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.apiMaxRequests = apiMaxRequests;
        this.apiWindowSeconds = apiWindowSeconds;
        this.authIpMaxRequests = authIpMaxRequests;
        this.authIpWindowSeconds = authIpWindowSeconds;
        this.orderIpMaxRequests = orderIpMaxRequests;
        this.orderIpWindowSeconds = orderIpWindowSeconds;
    }

    /**
     * Returns the filter order so rate limiting runs before authentication.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * Counts requests across IP dimensions and rejects the first exceeded rule.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange);
        List<GatewayRateLimitRule> rules = buildIpRules(exchange, clientIp);
        return rateLimitCounter.firstExceeded(rules)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(exceeded -> exceeded.isPresent()
                        ? rejectRequest(exchange, clientIp, exceeded.get())
                        : chain.filter(exchange));
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
     * Builds the global and interface-specific IP rules for one request.
     */
    private List<GatewayRateLimitRule> buildIpRules(ServerWebExchange exchange, String clientIp) {
        GatewayRateLimitRule globalRule = new GatewayRateLimitRule(
                "ip:global",
                clientIp,
                maxRequests,
                windowSeconds
        );
        String interfaceType = resolveInterfaceType(exchange);
        GatewayRateLimitRule interfaceRule;
        if ("auth".equals(interfaceType)) {
            interfaceRule = new GatewayRateLimitRule(
                    "ip:auth",
                    clientIp,
                    authIpMaxRequests,
                    authIpWindowSeconds
            );
        } else if ("order-create".equals(interfaceType)) {
            interfaceRule = new GatewayRateLimitRule(
                    "ip:order-create",
                    clientIp,
                    orderIpMaxRequests,
                    orderIpWindowSeconds
            );
        } else {
            interfaceRule = new GatewayRateLimitRule(
                    "ip:api:" + interfaceType,
                    clientIp,
                    apiMaxRequests,
                    apiWindowSeconds
            );
        }
        return List.of(globalRule, interfaceRule);
    }

    /**
     * Resolves a stable interface category so unrelated APIs do not share one secondary bucket.
     */
    private String resolveInterfaceType(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        if (HttpMethod.POST.equals(method)
                && ("/api/users/login".equals(path)
                || "/api/users/register".equals(path)
                || "/api/users/refresh".equals(path))) {
            return "auth";
        }
        if (HttpMethod.POST.equals(method) && "/api/orders".equals(path)) {
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
     * Writes a stable 429 response and exposes the retry window to the caller.
     */
    private Mono<Void> rejectRequest(
            ServerWebExchange exchange,
            String clientIp,
            GatewayRateLimitCounter.GatewayRateLimitResult exceeded
    ) {
        GatewayRateLimitRule rule = exceeded.rule();
        exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(Math.max(1L, rule.windowSeconds())));
        LOGGER.warn(
                "gateway rate limit rejected, scope={}, ip={}, method={}, path={}, count={}, maxRequests={}",
                rule.scope(),
                clientIp,
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
}
