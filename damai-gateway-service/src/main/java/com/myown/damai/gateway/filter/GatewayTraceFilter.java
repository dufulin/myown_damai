package com.myown.damai.gateway.filter;

import com.myown.damai.common.observability.TraceContext;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Creates and propagates one trace id across the complete gateway request.
 */
@Component
public class GatewayTraceFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_ATTRIBUTE = "damai.observability.userId";
    public static final String ORDER_NUMBER_ATTRIBUTE = "damai.observability.orderNumber";
    public static final String PROGRAM_ID_ATTRIBUTE = "damai.observability.programId";

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayTraceFilter.class);
    private static final Pattern ORDER_PATH = Pattern.compile("/api/orders/(\\d+)(?:/.*)?$");
    private static final Pattern PROGRAM_PATH = Pattern.compile("/api/programs/(\\d+)(?:/.*)?$");

    /**
     * Runs before rate limiting so rejected requests also receive a trace id.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * Adds the trace header, records known business identifiers, and logs request completion.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = TraceContext.resolveOrCreateTraceId(
                exchange.getRequest().getHeaders().getFirst(TraceContext.TRACE_ID_HEADER)
        );
        resolvePathIdentifier(exchange, ORDER_PATH, ORDER_NUMBER_ATTRIBUTE);
        resolvePathIdentifier(exchange, PROGRAM_PATH, PROGRAM_ID_ATTRIBUTE);
        ServerHttpRequest tracedRequest = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(TraceContext.TRACE_ID_HEADER, traceId))
                .build();
        ServerWebExchange tracedExchange = exchange.mutate().request(tracedRequest).build();
        tracedExchange.getResponse().getHeaders().set(TraceContext.TRACE_ID_HEADER, traceId);
        long startedAt = System.nanoTime();

        return Mono.defer(() -> {
            try (TraceContext.Scope ignored = openScope(tracedExchange, traceId)) {
                return chain.filter(tracedExchange);
            }
        }).doFinally(signalType -> logRequestCompleted(tracedExchange, traceId, startedAt));
    }

    /**
     * Resolves a numeric identifier from a REST path and stores it on the exchange.
     */
    private void resolvePathIdentifier(ServerWebExchange exchange, Pattern pattern, String attributeName) {
        Matcher matcher = pattern.matcher(exchange.getRequest().getURI().getPath());
        if (matcher.matches()) {
            exchange.getAttributes().put(attributeName, matcher.group(1));
        }
    }

    /**
     * Opens an MDC scope from the identifiers currently attached to the exchange.
     */
    private TraceContext.Scope openScope(ServerWebExchange exchange, String traceId) {
        return TraceContext.open(
                traceId,
                exchange.getAttribute(USER_ID_ATTRIBUTE),
                exchange.getAttribute(ORDER_NUMBER_ATTRIBUTE),
                exchange.getAttribute(PROGRAM_ID_ATTRIBUTE)
        );
    }

    /**
     * Logs one stable completion event with trace and business identifiers.
     */
    private void logRequestCompleted(ServerWebExchange exchange, String traceId, long startedAt) {
        try (TraceContext.Scope ignored = openScope(exchange, traceId)) {
            LOGGER.info(
                    "gateway request completed, method={}, path={}, status={}, durationMs={}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath(),
                    exchange.getResponse().getStatusCode(),
                    (System.nanoTime() - startedAt) / 1_000_000
            );
        }
    }
}
