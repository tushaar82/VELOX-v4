package com.velox.indicators;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Indicators Calculator microservice.
 * 
 * This service handles real-time technical indicator calculations with forming candle approach,
 * providing sub-millisecond calculation times for high-frequency trading strategies.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
public class IndicatorsCalculatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IndicatorsCalculatorApplication.class, args);
    }
}