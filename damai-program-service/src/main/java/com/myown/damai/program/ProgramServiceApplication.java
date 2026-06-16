package com.myown.damai.program;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Damai program service for categories, programs, tickets, and seats.
 */
@SpringBootApplication(scanBasePackages = "com.myown.damai")
public class ProgramServiceApplication {

    /**
     * Runs the program service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(ProgramServiceApplication.class, args);
    }
}
