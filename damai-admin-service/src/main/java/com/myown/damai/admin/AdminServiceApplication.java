package com.myown.damai.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the Damai management service for operational read models and privileged workflows.
 */
@SpringBootApplication(scanBasePackages = "com.myown.damai")
@MapperScan("com.myown.damai.admin.mapper")
public class AdminServiceApplication {

    /**
     * Runs the management service application.
     */
    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
