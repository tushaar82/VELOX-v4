package com.velox.strategy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for Strategy Execution microservice.
 * 
 * This service handles strategy execution, order management, and signal processing
 * for the Velox algotrading system in Phase 3.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
public class StrategyExecutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyExecutionApplication.class, args);
    }
}