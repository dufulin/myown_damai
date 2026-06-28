package com.myown.damai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.web.AuthenticatedUserHeader;
import com.myown.damai.gateway.filter.GatewayAuthenticationFilter;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Verifies gateway authentication replaces untrusted identity headers with user-service identity.
 */
class GatewayAuthenticationIdentityTest {

    /**
     * Verifies spoofed user and role headers are overwritten before forwarding downstream.
     */
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void authenticatedIdentityOverwritesSpoofedHeaders() {
        AtomicReference<ClientRequest> authenticationRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            authenticationRequest.set(request);
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body("""
                            {
                              "code": "SUCCESS",
                              "data": {
                                "id": 12345,
                                "role": "OPERATOR"
                              }
                            }
                            """)
                    .build();
            return Mono.just(response);
        };
        ReactiveCircuitBreaker circuitBreaker = mock(ReactiveCircuitBreaker.class);
        when(circuitBreaker.run(any(Mono.class), any(Function.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ReactiveCircuitBreakerFactory circuitBreakerFactory = mock(ReactiveCircuitBreakerFactory.class);
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);
        GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
                WebClient.builder().exchangeFunction(exchangeFunction),
                new ObjectMapper(),
                circuitBreakerFactory,
                true,
                "http://damai-user-service",
                1800,
                0,
                1
        );
        MockServerWebExchange incomingExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-test-token")
                        .header(AuthenticatedUserHeader.USER_ID, "99999")
                        .header(AuthenticatedUserHeader.USER_ROLE, "ADMIN")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();

        filter.filter(incomingExchange, exchange -> {
            forwardedExchange.set(exchange);
            return Mono.empty();
        }).block();

        assertNotNull(authenticationRequest.get());
        assertEquals(
                "Bearer valid-test-token",
                authenticationRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION)
        );
        assertNotNull(forwardedExchange.get());
        assertEquals(
                "12345",
                forwardedExchange.get().getRequest().getHeaders().getFirst(AuthenticatedUserHeader.USER_ID)
        );
        assertEquals(
                "OPERATOR",
                forwardedExchange.get().getRequest().getHeaders().getFirst(AuthenticatedUserHeader.USER_ROLE)
        );
    }
}
