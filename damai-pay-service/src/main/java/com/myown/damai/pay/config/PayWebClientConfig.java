package com.myown.damai.pay.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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
    public WebClient.Builder payWebClientBuilder() {
        return WebClient.builder();
    }
}
