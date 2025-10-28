package com.velox.marketdata.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for the Market Data Processor microservice.
 * Configures producers for publishing market data to Kafka topics.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.producer.acks}")
    private String acks;

    @Value("${spring.kafka.producer.retries}")
    private Integer retries;

    @Value("${spring.kafka.producer.batch-size}")
    private Integer batchSize;

    @Value("${spring.kafka.producer.buffer-memory}")
    private Integer bufferMemory;

    @Value("${spring.kafka.producer.linger-ms}")
    private Integer lingerMs;

    /**
     * Producer factory configuration for market data.
     * Optimized for high-throughput, low-latency market data publishing.
     */
    @Bean
    public ProducerFactory<String, Object> marketDataProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Connection settings
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Reliability settings
        configProps.put(ProducerConfig.ACKS_CONFIG, acks);
        configProps.put(ProducerConfig.RETRIES_CONFIG, retries);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Performance tuning
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        
        // High throughput settings
        configProps.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for publishing market data.
     */
    @Bean
    public KafkaTemplate<String, Object> marketDataKafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(marketDataProducerFactory());
        template.setDefaultTopic("market-data");
        return template;
    }

    /**
     * Producer factory configuration for tick data.
     * Optimized for ultra-low latency tick data publishing.
     */
    @Bean
    public ProducerFactory<String, Object> tickDataProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        
        // Connection settings
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Serialization
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        
        // Ultra-low latency settings
        configProps.put(ProducerConfig.ACKS_CONFIG, "1"); // Faster but less durable
        configProps.put(ProducerConfig.RETRIES_CONFIG, 0); // No retries for real-time data
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 0); // Disable batching
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 0); // No linger
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none"); // No compression for speed
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    /**
     * Kafka template for publishing tick data with ultra-low latency.
     */
    @Bean
    public KafkaTemplate<String, Object> tickDataKafkaTemplate() {
        KafkaTemplate<String, Object> template = new KafkaTemplate<>(tickDataProducerFactory());
        template.setDefaultTopic("tick-data");
        return template;
    }
}