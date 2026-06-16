package com.myown.damai.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Damai order service for order creation, query, cancellation, and timeout handling.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.myown.damai")
public class OrderServiceApplication {

    /**
     * Runs the order service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
