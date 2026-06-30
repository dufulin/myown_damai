package com.myown.damai.pay.config;

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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.netty.http.client.HttpClient;

/**
 * Provides a load-balanced WebClient for service-to-service calls.
 */
@Configuration
public class PayWebClientConfig {

    /**
     * Creates the load-balanced WebClient builder.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder payWebClientBuilder(
            @Value("${damai.pay.order-client.connect-timeout-millis:1000}") int connectTimeoutMillis,
            @Value("${damai.pay.order-client.response-timeout-millis:2000}") long responseTimeoutMillis,
            @Value("${damai.pay.order-client.read-timeout-millis:2000}") long readTimeoutMillis,
            @Value("${damai.pay.order-client.write-timeout-millis:2000}") long writeTimeoutMillis
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
