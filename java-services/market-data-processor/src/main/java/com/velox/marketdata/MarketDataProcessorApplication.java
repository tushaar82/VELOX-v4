package com.velox.marketdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the Market Data Processor microservice.
 * 
 * This service handles real-time market data processing, tick data management,
 * and candle formation for the Velox algotrading system.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
@EnableScheduling
public class MarketDataProcessorApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketDataProcessorApplication.class, args);
    }
}