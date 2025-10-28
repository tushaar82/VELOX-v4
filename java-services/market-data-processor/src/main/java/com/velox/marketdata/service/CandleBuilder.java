package com.velox.marketdata.service;

import com.velox.marketdata.model.CandleData;
import com.velox.marketdata.model.FormingCandle;
import com.velox.marketdata.model.TickData;
import com.velox.marketdata.repository.CandleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for building candles from real-time tick data.
 * Manages forming candles for multiple symbols and timeframes.
 */
@Service
@Slf4j
public class CandleBuilder {

    private final CandleRepository candleRepository;
    private final KafkaTemplate<String, Object> marketDataKafkaTemplate;

    @Value("${marketdata.candle.batch.size:50}")
    private int candleBatchSize;

    @Value("${marketdata.candle.flush.interval.ms:5000}")
    private long candleFlushInterval;

    // Active forming candles by symbol and timeframe
    private final Map<String, Map<CandleData.Timeframe, FormingCandle>> formingCandles = new ConcurrentHashMap<>();

    // Completed candles ready for storage
    private final List<CandleData> completedCandles = Collections.synchronizedList(new ArrayList<>());

    // Performance metrics
    private final AtomicLong candlesBuilt = new AtomicLong(0);
    private final AtomicLong candlesStored = new AtomicLong(0);
    private final AtomicLong ticksProcessed = new AtomicLong(0);

    // Supported timeframes for candle formation
    private static final List<CandleData.Timeframe> SUPPORTED_TIMEFRAMES = Arrays.asList(
            CandleData.Timeframe.ONE_MINUTE,
            CandleData.Timeframe.FIVE_MINUTES,
            CandleData.Timeframe.FIFTEEN_MINUTES,
            CandleData.Timeframe.THIRTY_MINUTES,
            CandleData.Timeframe.ONE_HOUR
    );

    @Autowired
    public CandleBuilder(
            CandleRepository candleRepository,
            KafkaTemplate<String, Object> marketDataKafkaTemplate) {
        this.candleRepository = candleRepository;
        this.marketDataKafkaTemplate = marketDataKafkaTemplate;
    }

    /**
     * Process tick data from Kafka topic
     */
    @KafkaListener(topics = "market-data", groupId = "candle-builder-group")
    public void processTickFromKafka(TickData tickData) {
        try {
            processTick(tickData);
        } catch (Exception e) {
            log.error("Error processing tick for candle building: {}", tickData, e);
        }
    }

    /**
     * Process a single tick for candle formation
     */
    public void processTick(TickData tickData) {
        if (tickData == null || !tickData.isValid()) {
            return;
        }

        String symbol = tickData.getSymbol();
        ticksProcessed.incrementAndGet();

        // Process tick for each supported timeframe
        for (CandleData.Timeframe timeframe : SUPPORTED_TIMEFRAMES) {
            processTickForTimeframe(tickData, timeframe);
        }

        log.trace("Processed tick for {} across {} timeframes", symbol, SUPPORTED_TIMEFRAMES.size());
    }

    /**
     * Process tick for a specific timeframe
     */
    private void processTickForTimeframe(TickData tickData, CandleData.Timeframe timeframe) {
        String symbol = tickData.getSymbol();
        String key = symbol + ":" + timeframe;

        // Get or create forming candle map for symbol
        Map<CandleData.Timeframe, FormingCandle> symbolCandles = 
                formingCandles.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());

        // Get existing forming candle or create new one
        FormingCandle formingCandle = symbolCandles.get(timeframe);
        
        if (formingCandle == null || formingCandle.shouldComplete(tickData.getTimestamp())) {
            // Complete existing candle if it exists
            if (formingCandle != null) {
                completeCandle(formingCandle);
            }

            // Create new forming candle
            formingCandle = FormingCandle.initialize(tickData, timeframe);
            symbolCandles.put(timeframe, formingCandle);
            
            log.debug("Created new forming candle for {} {}", symbol, timeframe);
        } else {
            // Update existing forming candle
            formingCandle.updateWithTick(tickData);
        }
    }

    /**
     * Complete a forming candle and add to storage queue
     */
    private void completeCandle(FormingCandle formingCandle) {
        if (!formingCandle.isValid()) {
            log.warn("Invalid forming candle, skipping: {}", formingCandle.getSymbol());
            return;
        }

        // Mark as complete
        formingCandle.markComplete();

        // Convert to CandleData entity
        CandleData candleData = formingCandle.toCandleData();
        
        // Add to completed candles list
        completedCandles.add(candleData);
        
        // Publish to Kafka for downstream processing
        marketDataKafkaTemplate.send("candle-data", candleData.getSymbol(), candleData);
        
        candlesBuilt.incrementAndGet();
        
        log.debug("Completed candle for {} {}: O={}, H={}, L={}, C={}, V={}", 
                  formingCandle.getSymbol(), formingCandle.getTimeframe(),
                  candleData.getOpenPrice(), candleData.getHighPrice(), 
                  candleData.getLowPrice(), candleData.getClosePrice(), candleData.getVolume());

        // Check if batch should be flushed
        if (completedCandles.size() >= candleBatchSize) {
            flushCandles();
        }
    }

    /**
     * Flush completed candles to database
     */
    public synchronized void flushCandles() {
        if (completedCandles.isEmpty()) {
            return;
        }

        try {
            List<CandleData> candlesToStore = new ArrayList<>(completedCandles);
            
            // Store in database
            candleRepository.saveAll(candlesToStore);
            
            candlesStored.addAndGet(candlesToStore.size());
            
            log.info("Flushed {} candles to database", candlesToStore.size());
            
            // Clear completed candles list
            completedCandles.clear();
            
        } catch (Exception e) {
            log.error("Error flushing candles to database", e);
        }
    }

    /**
     * Complete all active forming candles (called on shutdown)
     */
    public void completeAllActiveCandles() {
        log.info("Completing all active forming candles...");
        
        formingCandles.values().forEach(symbolCandles -> {
            symbolCandles.values().forEach(formingCandle -> {
                if (formingCandle != null && !formingCandle.isComplete()) {
                    completeCandle(formingCandle);
                }
            });
        });
        
        // Flush any remaining completed candles
        flushCandles();
        
        log.info("Completed all active forming candles");
    }

    /**
     * Get forming candle for symbol and timeframe
     */
    public FormingCandle getFormingCandle(String symbol, CandleData.Timeframe timeframe) {
        Map<CandleData.Timeframe, FormingCandle> symbolCandles = formingCandles.get(symbol);
        return symbolCandles != null ? symbolCandles.get(timeframe) : null;
    }

    /**
     * Get all forming candles for a symbol
     */
    public Map<CandleData.Timeframe, FormingCandle> getFormingCandles(String symbol) {
        return formingCandles.getOrDefault(symbol, new ConcurrentHashMap<>());
    }

    /**
     * Get statistics for candle building
     */
    public CandleBuildingStats getStats() {
        return CandleBuildingStats.builder()
                .candlesBuilt(candlesBuilt.get())
                .candlesStored(candlesStored.get())
                .ticksProcessed(ticksProcessed.get())
                .activeFormingCandles(countActiveFormingCandles())
                .completedCandlesInBatch(completedCandles.size())
                .build();
    }

    /**
     * Count active forming candles
     */
    private long countActiveFormingCandles() {
        return formingCandles.values().stream()
                .mapToLong(symbolCandles -> symbolCandles.values().stream()
                        .filter(Objects::nonNull)
                        .filter(candle -> !candle.isComplete())
                        .count())
                .sum();
    }

    /**
     * Clean up old forming candles (stale candles)
     */
    public void cleanupStaleCandles() {
        Instant now = Instant.now();
        int cleanedCount = 0;

        Iterator<Map.Entry<String, Map<CandleData.Timeframe, FormingCandle>>> symbolIterator = 
                formingCandles.entrySet().iterator();

        while (symbolIterator.hasNext()) {
            Map.Entry<String, Map<CandleData.Timeframe, FormingCandle>> symbolEntry = symbolIterator.next();
            
            Iterator<Map.Entry<CandleData.Timeframe, FormingCandle>> timeframeIterator = 
                    symbolEntry.getValue().entrySet().iterator();

            while (timeframeIterator.hasNext()) {
                Map.Entry<CandleData.Timeframe, FormingCandle> timeframeEntry = timeframeIterator.next();
                FormingCandle formingCandle = timeframeEntry.getValue();

                if (formingCandle != null) {
                    long ageSeconds = formingCandle.getAgeInSeconds(now);
                    long maxAge = timeframeEntry.getKey().getDurationInSeconds() * 2; // 2x timeframe duration

                    if (ageSeconds > maxAge) {
                        // Complete stale candle
                        completeCandle(formingCandle);
                        timeframeIterator.remove();
                        cleanedCount++;
                    }
                }
            }

            // Remove symbol entry if no more forming candles
            if (symbolEntry.getValue().isEmpty()) {
                symbolIterator.remove();
            }
        }

        if (cleanedCount > 0) {
            log.info("Cleaned up {} stale forming candles", cleanedCount);
        }
    }

    /**
     * Get supported timeframes
     */
    public List<CandleData.Timeframe> getSupportedTimeframes() {
        return new ArrayList<>(SUPPORTED_TIMEFRAMES);
    }

    /**
     * Check if timeframe is supported
     */
    public boolean isTimeframeSupported(CandleData.Timeframe timeframe) {
        return SUPPORTED_TIMEFRAMES.contains(timeframe);
    }

    /**
     * Scheduled flush of completed candles
     */
    @Scheduled(fixedRateString = "${marketdata.candle.flush.interval.ms:5000}")
    public void scheduledCandleFlush() {
        flushCandles();
    }

    /**
     * Scheduled cleanup of stale candles
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void scheduledCleanup() {
        cleanupStaleCandles();
    }

    /**
     * Statistics for candle building
     */
    @lombok.Builder
    @lombok.Data
    public static class CandleBuildingStats {
        private long candlesBuilt;
        private long candlesStored;
        private long ticksProcessed;
        private long activeFormingCandles;
        private int completedCandlesInBatch;
        
        public double getStorageRate() {
            return candlesBuilt > 0 ? (double) candlesStored / candlesBuilt * 100 : 0;
        }
        
        public double getTicksPerCandle() {
            return candlesBuilt > 0 ? (double) ticksProcessed / candlesBuilt : 0;
        }
    }
}