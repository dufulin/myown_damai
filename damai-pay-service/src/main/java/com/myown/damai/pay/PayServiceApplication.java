package com.myown.damai.pay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Damai payment service.
 */
@SpringBootApplication
public class PayServiceApplication {

    /**
     * Starts the Spring Boot application.
     */
    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
