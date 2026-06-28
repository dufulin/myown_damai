package com.myown.damai.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the Damai user service for account, login, logout, and session APIs.
 */
@SpringBootApplication(scanBasePackages = "com.myown.damai")
@EnableScheduling
public class UserServiceApplication {

    /**
     * Runs the user service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
