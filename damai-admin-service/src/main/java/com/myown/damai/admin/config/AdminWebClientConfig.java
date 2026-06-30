package com.myown.damai.admin.config;

import com.myown.damai.common.observability.TraceContext;
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
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configures bounded and trace-aware HTTP clients for management operations.
 */
@Configuration
public class AdminWebClientConfig {

    /**
     * Creates the load-balanced WebClient builder used to call domain services.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder adminWebClientBuilder(
            @Value("${damai.admin.client.connect-timeout-millis:1000}") int connectTimeoutMillis,
            @Value("${damai.admin.client.response-timeout-millis:3000}") long responseTimeoutMillis,
            @Value("${damai.admin.client.read-timeout-millis:3000}") long readTimeoutMillis,
            @Value("${damai.admin.client.write-timeout-millis:3000}") long writeTimeoutMillis
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .responseTimeout(Duration.ofMillis(responseTimeoutMillis))
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(writeTimeoutMillis, TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter((request, next) -> {
                    ClientRequest tracedRequest = ClientRequest.from(request)
                            .headers(TraceContext::writeTo)
                            .build();
                    return next.exchange(tracedRequest);
                });
    }
}
