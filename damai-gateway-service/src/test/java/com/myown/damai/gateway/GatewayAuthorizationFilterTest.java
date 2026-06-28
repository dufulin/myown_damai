package com.myown.damai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.gateway.filter.GatewayAuthorizationFilter;
import com.myown.damai.gateway.filter.GatewayRolePolicy;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies gateway authorization filter decisions with trusted identity headers.
 */
class GatewayAuthorizationFilterTest {

    private final GatewayAuthorizationFilter authorizationFilter =
            new GatewayAuthorizationFilter(new GatewayRolePolicy(), new ObjectMapper());

    /**
     * Verifies a normal user receives a forbidden response for program creation.
     */
    @Test
    void normalUserCannotCreateProgram() {
        MockServerWebExchange exchange = exchangeForRole("USER");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        authorizationFilter.filter(exchange, ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertFalse(chainCalled.get());
    }

    /**
     * Verifies an operator can continue through the gateway for program creation.
     */
    @Test
    void operatorCanCreateProgram() {
        MockServerWebExchange exchange = exchangeForRole("OPERATOR");
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        authorizationFilter.filter(exchange, ignored -> {
            chainCalled.set(true);
            return Mono.empty();
        }).block();

        assertTrue(chainCalled.get());
    }

    /**
     * Creates a program-write exchange with trusted identity headers.
     */
    private MockServerWebExchange exchangeForRole(String role) {
        return MockServerWebExchange.from(MockServerHttpRequest.post("/api/programs")
                .header(AuthenticatedUserHeader.USER_ID, "10001")
                .header(AuthenticatedUserHeader.USER_ROLE, role)
                .build());
    }
}
