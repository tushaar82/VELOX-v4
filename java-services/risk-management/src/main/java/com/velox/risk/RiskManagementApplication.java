package com.velox.risk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Risk Management microservice.
 * 
 * This service handles real-time risk monitoring, position management,
 * and automated risk controls for the Velox algotrading system.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
public class RiskManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskManagementApplication.class, args);
    }
}