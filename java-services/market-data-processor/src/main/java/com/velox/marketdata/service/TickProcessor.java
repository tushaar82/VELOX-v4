package com.velox.marketdata.service;

import com.velox.marketdata.model.TickData;
import com.velox.marketdata.repository.MarketDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for processing real-time tick data.
 * Handles validation, normalization, quality checks, and persistence of tick data.
 */
@Service
@Slf4j
public class TickProcessor {

    private final MarketDataRepository marketDataRepository;
    private final KafkaTemplate<String, Object> marketDataKafkaTemplate;
    private final DataValidator dataValidator;
    private final DataNormalizer dataNormalizer;

    @Value("${marketdata.tick.batch.size:100}")
    private int batchSize;

    @Value("${marketdata.tick.batch.timeout.ms:1000}")
    private long batchTimeout;

    @Value("${marketdata.tick.max.age.hours:24}")
    private int maxTickAgeHours;

    // Performance metrics
    private final AtomicLong ticksProcessed = new AtomicLong(0);
    private final AtomicLong ticksRejected = new AtomicLong(0);
    private final AtomicLong ticksStored = new AtomicLong(0);

    // In-memory batch for performance
    private final ConcurrentHashMap<String, TickData> tickBatch = new ConcurrentHashMap<>();
    private volatile long lastBatchFlush = System.currentTimeMillis();

    @Autowired
    public TickProcessor(
            MarketDataRepository marketDataRepository,
            KafkaTemplate<String, Object> marketDataKafkaTemplate,
            DataValidator dataValidator,
            DataNormalizer dataNormalizer) {
        this.marketDataRepository = marketDataRepository;
        this.marketDataKafkaTemplate = marketDataKafkaTemplate;
        this.dataValidator = dataValidator;
        this.dataNormalizer = dataNormalizer;
    }

    /**
     * Process tick data from Kafka topic
     */
    @KafkaListener(topics = "tick-data", groupId = "tick-processor-group")
    public void processTickFromKafka(TickData tickData) {
        try {
            processTick(tickData);
        } catch (Exception e) {
            log.error("Error processing tick from Kafka: {}", tickData, e);
            ticksRejected.incrementAndGet();
        }
    }

    /**
     * Process a single tick
     */
    @Async
    public CompletableFuture<Void> processTickAsync(TickData tickData) {
        return CompletableFuture.runAsync(() -> processTick(tickData));
    }

    /**
     * Process tick data with validation and normalization
     */
    private void processTick(TickData tickData) {
        try {
            // Validate tick data
            TickData.ValidationResult validation = dataValidator.validateTick(tickData);
            if (!validation.isValid()) {
                log.debug("Tick validation failed for {}: {}", tickData.getSymbol(), validation.getReason());
                ticksRejected.incrementAndGet();
                return;
            }

            // Normalize tick data
            TickData normalizedTick = dataNormalizer.normalizeTick(tickData);
            
            // Apply quality checks
            TickData.TickQuality quality = determineTickQuality(normalizedTick);
            normalizedTick.setQuality(quality);

            // Update sequence number if not present
            if (normalizedTick.getSequenceNumber() == null) {
                normalizedTick.setSequenceNumber(generateSequenceNumber(normalizedTick.getSymbol()));
            }

            // Add to batch for storage
            addToBatch(normalizedTick);

            // Publish to market data topic for downstream processing
            marketDataKafkaTemplate.send("market-data", normalizedTick.getSymbol(), normalizedTick);

            ticksProcessed.incrementAndGet();

            log.trace("Processed tick for {}: {} @ {}", 
                     normalizedTick.getSymbol(), normalizedTick.getLastPrice(), normalizedTick.getTimestamp());

        } catch (Exception e) {
            log.error("Error processing tick: {}", tickData, e);
            ticksRejected.incrementAndGet();
        }
    }

    /**
     * Add tick to batch for efficient storage
     */
    private void addToBatch(TickData tickData) {
        String key = tickData.getSymbol() + ":" + tickData.getTimestamp().toEpochMilli();
        tickBatch.put(key, tickData);

        // Check if batch should be flushed
        long currentTime = System.currentTimeMillis();
        if (tickBatch.size() >= batchSize || (currentTime - lastBatchFlush) > batchTimeout) {
            flushBatch();
        }
    }

    /**
     * Flush batch to database
     */
    private synchronized void flushBatch() {
        if (tickBatch.isEmpty()) {
            return;
        }

        try {
            List<TickData> ticksToStore = List.copyOf(tickBatch.values());
            
            // Store in database
            marketDataRepository.saveAll(ticksToStore);
            
            ticksStored.addAndGet(ticksToStore.size());
            
            log.debug("Flushed {} ticks to database", ticksToStore.size());
            
            // Clear batch
            tickBatch.clear();
            lastBatchFlush = System.currentTimeMillis();
            
        } catch (Exception e) {
            log.error("Error flushing tick batch to database", e);
        }
    }

    /**
     * Determine tick quality based on various factors
     */
    private TickData.TickQuality determineTickQuality(TickData tick) {
        // Check for delayed tick
        Instant now = Instant.now();
        long delaySeconds = now.getEpochSecond() - tick.getTimestamp().getEpochSecond();
        if (delaySeconds > 5) {
            return TickData.TickQuality.DELAYED;
        }

        // Check for out-of-order tick
        TickData lastTick = getLastTickForSymbol(tick.getSymbol());
        if (lastTick != null && tick.getTimestamp().isBefore(lastTick.getTimestamp())) {
            return TickData.TickQuality.OUT_OF_ORDER;
        }

        // Check for duplicate tick
        if (isDuplicateTick(tick)) {
            return TickData.TickQuality.DUPLICATE;
        }

        // Check for suspicious price movements
        if (isSuspiciousPriceMovement(tick)) {
            return TickData.TickQuality.CORRUPTED;
        }

        return TickData.TickQuality.GOOD;
    }

    /**
     * Check if tick is a duplicate
     */
    private boolean isDuplicateTick(TickData tick) {
        TickData lastTick = getLastTickForSymbol(tick.getSymbol());
        if (lastTick != null) {
            return tick.getTimestamp().equals(lastTick.getTimestamp()) &&
                   tick.getLastPrice().equals(lastTick.getLastPrice()) &&
                   tick.getVolume() != null && tick.getVolume().equals(lastTick.getVolume());
        }
        return false;
    }

    /**
     * Check for suspicious price movements
     */
    private boolean isSuspiciousPriceMovement(TickData tick) {
        TickData lastTick = getLastTickForSymbol(tick.getSymbol());
        if (lastTick != null && lastTick.getLastPrice() != null) {
            BigDecimal priceChange = tick.getLastPrice().subtract(lastTick.getLastPrice());
            BigDecimal percentageChange = priceChange.divide(lastTick.getLastPrice(), 4, BigDecimal.ROUND_HALF_UP)
                                            .multiply(BigDecimal.valueOf(100));
            
            // Flag movements > 10% as suspicious
            return percentageChange.abs().compareTo(BigDecimal.valueOf(10)) > 0;
        }
        return false;
    }

    /**
     * Get last tick for a symbol (in-memory cache)
     */
    private TickData getLastTickForSymbol(String symbol) {
        // This would ideally use a proper cache like Redis
        return tickBatch.values().stream()
                .filter(t -> t.getSymbol().equals(symbol))
                .max((t1, t2) -> t1.getTimestamp().compareTo(t2.getTimestamp()))
                .orElse(null);
    }

    /**
     * Generate sequence number for tick
     */
    private Long generateSequenceNumber(String symbol) {
        // Simple sequence number generation - in production, use a more robust mechanism
        return System.currentTimeMillis();
    }

    /**
     * Clean up old ticks to prevent database bloat
     */
    public void cleanupOldTicks() {
        try {
            Instant cutoffTime = Instant.now().minusSeconds(maxTickAgeHours * 3600L);
            int deletedCount = marketDataRepository.deleteByTimestampBefore(cutoffTime);
            log.info("Cleaned up {} old ticks older than {}", deletedCount, cutoffTime);
        } catch (Exception e) {
            log.error("Error cleaning up old ticks", e);
        }
    }

    /**
     * Get processing statistics
     */
    public TickProcessingStats getStats() {
        return TickProcessingStats.builder()
                .ticksProcessed(ticksProcessed.get())
                .ticksRejected(ticksRejected.get())
                .ticksStored(ticksStored.get())
                .batchSize(tickBatch.size())
                .lastBatchFlush(lastBatchFlush)
                .build();
    }

    /**
     * Force flush current batch
     */
    public void flushBatchNow() {
        flushBatch();
    }

    /**
     * Statistics for tick processing
     */
    @lombok.Builder
    @lombok.Data
    public static class TickProcessingStats {
        private long ticksProcessed;
        private long ticksRejected;
        private long ticksStored;
        private int batchSize;
        private long lastBatchFlush;
        
        public double getRejectionRate() {
            long total = ticksProcessed + ticksRejected;
            return total > 0 ? (double) ticksRejected / total * 100 : 0;
        }
        
        public double getStorageRate() {
            return ticksProcessed > 0 ? (double) ticksStored / ticksProcessed * 100 : 0;
        }
    }

    /**
     * Scheduled batch flush
     */
    public void scheduledBatchFlush() {
        flushBatch();
    }

    /**
     * Scheduled cleanup
     */
    public void scheduledCleanup() {
        cleanupOldTicks();
    }
}