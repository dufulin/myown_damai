package com.myown.damai.gateway.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Provides load-balanced WebClient support for gateway-to-service calls.
 */
@Configuration
public class GatewayWebClientConfig {

    /**
     * Builds a load-balanced WebClient builder that can resolve Nacos service names.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder loadBalancedWebClientBuilder(
            @Value("${damai.gateway.user-service.connect-timeout-millis:1000}") int connectTimeoutMillis,
            @Value("${damai.gateway.user-service.response-timeout-millis:1500}") long responseTimeoutMillis,
            @Value("${damai.gateway.user-service.read-timeout-millis:1500}") long readTimeoutMillis,
            @Value("${damai.gateway.user-service.write-timeout-millis:1500}") long writeTimeoutMillis
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofMillis(responseTimeoutMillis))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMillis, TimeUnit.MILLISECONDS)));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
