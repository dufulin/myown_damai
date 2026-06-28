package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.auth.UserRole;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Enforces centralized role policies after gateway authentication has established identity.
 */
@Component
public class GatewayAuthorizationFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthorizationFilter.class);

    private final GatewayRolePolicy rolePolicy;
    private final ObjectMapper objectMapper;

    /**
     * Creates the authorization filter with route policy and response serialization support.
     */
    public GatewayAuthorizationFilter(GatewayRolePolicy rolePolicy, ObjectMapper objectMapper) {
        this.rolePolicy = rolePolicy;
        this.objectMapper = objectMapper;
    }

    /**
     * Runs authorization immediately after authentication has populated trusted headers.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    /**
     * Rejects internal routes and human users without the required role.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Optional<GatewayRolePolicy.Requirement> requirement = rolePolicy.resolve(
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath()
        );
        if (requirement.isEmpty()) {
            return chain.filter(exchange);
        }

        String userId = exchange.getRequest().getHeaders().getFirst(AuthenticatedUserHeader.USER_ID);
        String roleHeader = exchange.getRequest().getHeaders().getFirst(AuthenticatedUserHeader.USER_ROLE);
        UserRole role = resolveRole(roleHeader);
        if (requirement.get().allows(role)) {
            LOGGER.info(
                    "gateway authorization passed, method={}, path={}, userId={}, role={}",
                    exchange.getRequest().getMethod(),
                    exchange.getRequest().getURI().getPath(),
                    userId,
                    role
            );
            return chain.filter(exchange);
        }

        LOGGER.warn(
                "gateway authorization rejected, method={}, path={}, userId={}, role={}, internalOnly={}",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(),
                userId,
                role,
                requirement.get().internalOnly()
        );
        return GatewayResponseWriter.writeError(
                exchange.getResponse(),
                objectMapper,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "insufficient permissions"
        );
    }

    /**
     * Resolves a trusted role header and treats missing or malformed values as USER.
     */
    private UserRole resolveRole(String roleHeader) {
        try {
            return UserRole.fromNullable(roleHeader);
        } catch (IllegalArgumentException exception) {
            return UserRole.USER;
        }
    }
}
