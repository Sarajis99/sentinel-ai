package com.sentinel.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ApiApp — REST API gateway for Sentinel AI Dashboard.
 * Serves incidents, metrics, simulation triggers, and data retention.
 */
@SpringBootApplication
@EnableScheduling
public class ApiApp {
    public static void main(String[] args) {
        SpringApplication.run(ApiApp.class, args);
    }
}