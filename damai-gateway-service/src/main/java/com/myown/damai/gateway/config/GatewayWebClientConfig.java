package com.myown.damai.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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
    public WebClient.Builder loadBalancedWebClientBuilder() {
        return WebClient.builder();
    }
}
