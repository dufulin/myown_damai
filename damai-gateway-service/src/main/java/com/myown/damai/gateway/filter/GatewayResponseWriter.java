package com.myown.damai.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myown.damai.common.dto.ApiResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

/**
 * Writes stable JSON responses from reactive gateway filters.
 */
final class GatewayResponseWriter {

    /**
     * Prevents utility class instantiation.
     */
    private GatewayResponseWriter() {
    }

    /**
     * Writes a failed API response and completes the reactive exchange.
     */
    static Mono<Void> writeError(
            ServerHttpResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message
    ) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = serializeBody(objectMapper, code, message);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * Serializes an API error response to bytes.
     */
    private static byte[] serializeBody(ObjectMapper objectMapper, String code, String message) {
        try {
            return objectMapper.writeValueAsBytes(ApiResponse.failed(code, message));
        } catch (JsonProcessingException exception) {
            return ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}").getBytes();
        }
    }
}
