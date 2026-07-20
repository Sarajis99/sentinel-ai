package com.sentinel.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SimulatorApp {
    public static void main(String[] args) {
        SpringApplication.run(SimulatorApp.class, args);
    }
}
