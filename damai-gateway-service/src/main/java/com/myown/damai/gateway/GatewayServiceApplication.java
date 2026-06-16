package com.myown.damai.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Damai gateway service for routing, authentication checks, and rate limiting.
 */
@SpringBootApplication(scanBasePackages = "com.myown.damai")
public class GatewayServiceApplication {

    /**
     * Runs the gateway service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
