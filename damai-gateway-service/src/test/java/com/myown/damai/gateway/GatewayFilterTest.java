package com.myown.damai.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.myown.damai.common.observability.TraceContext;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies gateway authentication and IP rate limiting filters.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.discovery.enabled=false",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.discovery.register-enabled=false",
                "damai.gateway.rate-limit.max-requests=2",
                "damai.gateway.rate-limit.window-seconds=60",
                "damai.cache.redis-enabled=false"
        }
)
@AutoConfigureWebTestClient
class GatewayFilterTest {

    @Autowired
    private WebTestClient webTestClient;

    /**
     * Verifies private APIs are rejected by the gateway when no bearer token is present.
     */
    @Test
    void protectedApiWithoutTokenReturnsUnauthorized() {
        webTestClient.get()
                .uri("/api/programs")
                .header("X-Forwarded-For", "10.0.0.20")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists(TraceContext.TRACE_ID_HEADER)
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    /**
     * Verifies a valid caller trace id is returned unchanged for cross-system correlation.
     */
    @Test
    void validTraceIdIsPropagatedToResponse() {
        String traceId = "frontend-trace-12345678";
        webTestClient.get()
                .uri("/api/programs")
                .header("X-Forwarded-For", "10.0.0.21")
                .header(TraceContext.TRACE_ID_HEADER, traceId)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(TraceContext.TRACE_ID_HEADER, traceId);
    }

    /**
     * Verifies the same IP is rejected after exceeding the configured request count.
     */
    @Test
    void sameIpRequestsOverLimitReturnTooManyRequests() {
        String body = """
                {
                  "login": "nobody@example.com",
                  "password": "wrong-password"
                }
                """;

        postLogin(body)
                .expectStatus().is5xxServerError();

        postLogin(body)
                .expectStatus().is5xxServerError();

        postLogin(body)
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOO_MANY_REQUESTS");
    }

    /**
     * Sends one public login request from the rate-limit test IP.
     */
    private WebTestClient.ResponseSpec postLogin(String body) {
        return webTestClient.post()
                .uri("/api/users/login")
                .header("X-Forwarded-For", "10.0.0.10")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange();
    }
}
