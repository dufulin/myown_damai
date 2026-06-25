package com.myown.damai.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Damai payment service.
 */
@SpringBootApplication
@EnableScheduling
public class PayServiceApplication {

    /**
     * Starts the Spring Boot application.
     */
    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
