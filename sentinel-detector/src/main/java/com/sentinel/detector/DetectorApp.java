package com.sentinel.detector;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DetectorApp {
    public static void main(String[] args) {
        SpringApplication.run(DetectorApp.class, args);
    }
}