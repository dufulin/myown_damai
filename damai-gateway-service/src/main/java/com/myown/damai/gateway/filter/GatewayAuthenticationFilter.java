package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates logged-in users before private API requests are routed downstream.
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final boolean authEnabled;
    private final String userServiceUrl;

    /**
     * Creates the authentication filter with user-service validation settings.
     */
    public GatewayAuthenticationFilter(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${damai.gateway.auth-enabled:true}") boolean authEnabled,
            @Value("${damai.gateway.user-service-url:http://damai-user-service}") String userServiceUrl
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.authEnabled = authEnabled;
        this.userServiceUrl = userServiceUrl;
    }

    /**
     * Returns the filter order so authentication runs after rate limiting.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * Validates bearer tokens for private API requests.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (shouldSkip(exchange)) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return webClient.get()
                .uri(userServiceUrl + "/api/users/me")
                .header(HttpHeaders.AUTHORIZATION, authorization == null ? "" : authorization)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    String userId = resolveUserId(body);
                                    LOGGER.info(
                                            "gateway auth passed, method={}, path={}, userId={}",
                                            exchange.getRequest().getMethod(),
                                            exchange.getRequest().getURI().getPath(),
                                            userId
                                    );
                                    ServerHttpRequest request = exchange.getRequest()
                                            .mutate()
                                            .headers(headers -> {
                                                headers.remove(AuthenticatedUserHeader.USER_ID);
                                                headers.set(AuthenticatedUserHeader.USER_ID, userId);
                                            })
                                            .build();
                                    return chain.filter(exchange.mutate().request(request).build());
                                });
                    }
                    LOGGER.warn(
                            "gateway auth rejected, method={}, path={}, status={}",
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getURI().getPath(),
                            response.statusCode().value()
                    );
                    return GatewayResponseWriter.writeError(
                            exchange.getResponse(),
                            objectMapper,
                            HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED",
                            "invalid or expired token"
                    );
                })
                .onErrorResume(exception -> {
                    LOGGER.warn("gateway auth user-service call failed", exception);
                    return GatewayResponseWriter.writeError(
                            exchange.getResponse(),
                            objectMapper,
                            HttpStatus.UNAUTHORIZED,
                            "UNAUTHORIZED",
                            "invalid or expired token"
                    );
                });
    }

    /**
     * Checks whether authentication should be skipped for this request.
     */
    private boolean shouldSkip(ServerWebExchange exchange) {
        if (!authEnabled) {
            return true;
        }
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        return !path.startsWith("/api/")
                || HttpMethod.OPTIONS.equals(method)
                || isPublicPayEndpoint(path, method)
                || isPublicUserEndpoint(path, method);
    }

    /**
     * Checks whether the request is a public login or registration API.
     */
    private boolean isPublicUserEndpoint(String path, HttpMethod method) {
        return HttpMethod.POST.equals(method)
                && ("/api/users/login".equals(path) || "/api/users/register".equals(path));
    }

    /**
     * Checks whether the request is a public payment provider callback.
     */
    private boolean isPublicPayEndpoint(String path, HttpMethod method) {
        return HttpMethod.POST.equals(method)
                && "/api/pay/alipay/notify".equals(path);
    }

    /**
     * Resolves the authenticated user id from the user service profile response.
     */
    private String resolveUserId(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode userIdNode = root.path("data").path("id");
            if (userIdNode.isIntegralNumber()) {
                return userIdNode.asText();
            }
        } catch (Exception exception) {
            LOGGER.warn("gateway auth response parse failed", exception);
        }
        throw new IllegalStateException("user id is missing from auth response");
    }
}
