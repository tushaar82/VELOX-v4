package com.velox.indicators.service;

import com.velox.indicators.algorithm.MACD;
import com.velox.indicators.algorithm.MovingAverage;
import com.velox.indicators.algorithm.RSI;
import com.velox.indicators.model.IndicatorValue;
import com.velox.marketdata.model.CandleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time indicator calculation engine.
 * Provides sub-millisecond indicator calculations with forming candle approach.
 */
@Service
@Slf4j
public class RealTimeIndicatorEngine {

    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> indicatorKafkaTemplate;

    @Value("${indicators.cache.ttl.seconds:300}")
    private long cacheTTLSeconds;

    @Value("${indicators.calculation.timeout.ms:50}")
    private long calculationTimeoutMs;

    // Indicator storage by symbol and timeframe
    private final Map<String, Map<String, IndicatorCalculator>> symbolIndicators = new ConcurrentHashMap<>();

    // Performance metrics
    private final AtomicLong calculationsPerformed = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);

    @Autowired
    public RealTimeIndicatorEngine(
            RedisTemplate<String, Object> redisTemplate,
            KafkaTemplate<String, Object> indicatorKafkaTemplate) {
        this.redisTemplate = redisTemplate;
        this.indicatorKafkaTemplate = indicatorKafkaTemplate;
    }

    /**
     * Process candle data from Kafka topic
     */
    @KafkaListener(topics = "candle-data", groupId = "indicator-calculator-group")
    public void processCandleFromKafka(CandleData candle) {
        try {
            processCandle(candle);
        } catch (Exception e) {
            log.error("Error processing candle for indicator calculation: {}", candle, e);
        }
    }

    /**
     * Process a single candle for indicator calculation
     */
    @Async
    public CompletableFuture<Void> processCandleAsync(CandleData candle) {
        return CompletableFuture.runAsync(() -> processCandle(candle));
    }

    /**
     * Process candle data and update all indicators
     */
    private void processCandle(CandleData candle) {
        if (candle == null || !candle.isValid()) {
            return;
        }

        String symbol = candle.getSymbol();
        String timeframe = candle.getTimeframe().name();

        // Get or create indicator calculator for symbol
        Map<String, IndicatorCalculator> symbolCalculator = symbolIndicators
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());

        // Get or create indicator calculator for timeframe
        IndicatorCalculator calculator = symbolCalculator
                .computeIfAbsent(timeframe, k -> new IndicatorCalculator(symbol, timeframe));

        // Update all indicators with new candle
        calculator.updateIndicators(candle);

        // Publish updated indicators
        publishIndicators(symbol, timeframe, calculator.getAllIndicators());

        calculationsPerformed.incrementAndGet();

        log.trace("Processed indicators for {} {}: O={}, H={}, L={}, C={}", 
                  symbol, timeframe, candle.getOpenPrice(), candle.getHighPrice(), 
                  candle.getLowPrice(), candle.getClosePrice());
    }

    /**
     * Get indicator values for symbol and timeframe
     */
    public IndicatorValues getIndicators(String symbol, String timeframe) {
        String cacheKey = getCacheKey(symbol, timeframe);
        
        // Try to get from cache first
        IndicatorValues cachedValues = (IndicatorValues) redisTemplate.opsForValue().get(cacheKey);
        if (cachedValues != null) {
            cacheHits.incrementAndGet();
            return cachedValues;
        }

        // Get from calculator
        Map<String, IndicatorCalculator> symbolCalculator = symbolIndicators.get(symbol);
        if (symbolCalculator == null) {
            cacheMisses.incrementAndGet();
            return null;
        }

        IndicatorCalculator calculator = symbolCalculator.get(timeframe);
        if (calculator == null) {
            cacheMisses.incrementAndGet();
            return null;
        }

        IndicatorValues values = calculator.getAllIndicators();
        
        // Cache the result
        redisTemplate.opsForValue().set(cacheKey, values, java.time.Duration.ofSeconds(cacheTTLSeconds));
        cacheMisses.incrementAndGet();

        return values;
    }

    /**
     * Get specific indicator value
     */
    public BigDecimal getIndicator(String symbol, String timeframe, String indicatorType) {
        IndicatorValues values = getIndicators(symbol, timeframe);
        if (values == null) {
            return null;
        }

        return switch (indicatorType.toLowerCase()) {
            case "sma20" -> values.getSma20();
            case "ema20" -> values.getEma20();
            case "rsi14" -> values.getRsi14();
            case "macd" -> values.getMacdLine();
            case "macd_signal" -> values.getMacdSignal();
            case "macd_histogram" -> values.getMacdHistogram();
            default -> null;
        };
    }

    /**
     * Publish indicator values to Kafka
     */
    private void publishIndicators(String symbol, String timeframe, IndicatorValues values) {
        try {
            IndicatorValue indicatorValue = IndicatorValue.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .timestamp(Instant.now())
                    .sma20(values.getSma20())
                    .ema20(values.getEma20())
                    .rsi14(values.getRsi14())
                    .macdLine(values.getMacdLine())
                    .macdSignal(values.getMacdSignal())
                    .macdHistogram(values.getMacdHistogram())
                    .build();

            indicatorKafkaTemplate.send("indicator-values", symbol, indicatorValue);
        } catch (Exception e) {
            log.error("Error publishing indicators for {} {}", symbol, timeframe, e);
        }
    }

    /**
     * Generate cache key
     */
    private String getCacheKey(String symbol, String timeframe) {
        return String.format("indicators:%s:%s", symbol, timeframe);
    }

    /**
     * Initialize indicators for a symbol with historical data
     */
    public void initializeIndicators(String symbol, String timeframe, List<CandleData> historicalCandles) {
        if (historicalCandles == null || historicalCandles.isEmpty()) {
            return;
        }

        Map<String, IndicatorCalculator> symbolCalculator = symbolIndicators
                .computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());

        IndicatorCalculator calculator = symbolCalculator
                .computeIfAbsent(timeframe, k -> new IndicatorCalculator(symbol, timeframe));

        // Initialize with historical data
        calculator.initializeWithHistoricalData(historicalCandles);

        log.info("Initialized indicators for {} {} with {} historical candles", 
                  symbol, timeframe, historicalCandles.size());
    }

    /**
     * Get calculation statistics
     */
    public CalculationStats getStats() {
        return CalculationStats.builder()
                .calculationsPerformed(calculationsPerformed.get())
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .activeSymbols(symbolIndicators.size())
                .totalCalculators(symbolIndicators.values().stream()
                        .mapToInt(Map::size)
                        .sum())
                .cacheHitRate(calculateCacheHitRate())
                .build();
    }

    /**
     * Calculate cache hit rate
     */
    private double calculateCacheHitRate() {
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        long total = hits + misses;
        
        return total > 0 ? (double) hits / total * 100 : 0;
    }

    /**
     * Cleanup old indicators
     */
    public void cleanupOldIndicators() {
        Instant cutoff = Instant.now().minusSeconds(cacheTTLSeconds * 2);
        int cleanedCount = 0;

        for (Map.Entry<String, Map<String, IndicatorCalculator>> symbolEntry : symbolIndicators.entrySet()) {
            for (Map.Entry<String, IndicatorCalculator> timeframeEntry : symbolEntry.getValue().entrySet()) {
                IndicatorCalculator calculator = timeframeEntry.getValue();
                if (calculator.getLastUpdate().isBefore(cutoff)) {
                    timeframeEntry.getValue().clear();
                    cleanedCount++;
                }
            }
        }

        if (cleanedCount > 0) {
            log.info("Cleaned up {} old indicator calculators", cleanedCount);
        }
    }

    /**
     * Reset all indicators
     */
    public void resetAllIndicators() {
        symbolIndicators.clear();
        calculationsPerformed.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
        log.info("Reset all indicator calculators");
    }

    /**
     * Get all active symbols
     */
    public Set<String> getActiveSymbols() {
        return new HashSet<>(symbolIndicators.keySet());
    }

    /**
     * Get all active timeframes for a symbol
     */
    public Set<String> getActiveTimeframes(String symbol) {
        Map<String, IndicatorCalculator> symbolCalculator = symbolIndicators.get(symbol);
        return symbolCalculator != null ? new HashSet<>(symbolCalculator.keySet()) : new HashSet<>();
    }

    /**
     * Container class for indicator values
     */
    @lombok.Builder
    @lombok.Data
    public static class IndicatorValues {
        private BigDecimal sma20;
        private BigDecimal ema20;
        private BigDecimal rsi14;
        private BigDecimal macdLine;
        private BigDecimal macdSignal;
        private BigDecimal macdHistogram;
        private Instant timestamp;
    }

    /**
     * Container class for calculation statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class CalculationStats {
        private long calculationsPerformed;
        private long cacheHits;
        private long cacheMisses;
        private int activeSymbols;
        private int totalCalculators;
        private double cacheHitRate;
    }

    /**
     * Scheduled cleanup of old indicators
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void scheduledCleanup() {
        cleanupOldIndicators();
    }

    /**
     * Internal indicator calculator class
     */
    private static class IndicatorCalculator {
        private final String symbol;
        private final String timeframe;
        private final MovingAverage.SMA sma20;
        private final MovingAverage.EMA ema20;
        private final RSI rsi14;
        private final MACD macd;
        private volatile Instant lastUpdate;

        public IndicatorCalculator(String symbol, String timeframe) {
            this.symbol = symbol;
            this.timeframe = timeframe;
            this.sma20 = new MovingAverage.SMA(20);
            this.ema20 = new MovingAverage.EMA(20);
            this.rsi14 = new RSI(14);
            this.macd = new MACD(12, 26, 9);
            this.lastUpdate = Instant.now();
        }

        public synchronized void updateIndicators(CandleData candle) {
            BigDecimal closePrice = candle.getClosePrice();
            
            // Update all indicators
            sma20.addPrice(closePrice);
            ema20.addPrice(closePrice);
            rsi14.addPrice(closePrice);
            macd.addPrice(closePrice);
            
            lastUpdate = Instant.now();
        }

        public IndicatorValues getAllIndicators() {
            return IndicatorValues.builder()
                    .sma20(sma20.getCurrentValue())
                    .ema20(ema20.getCurrentValue())
                    .rsi14(rsi14.getCurrentValue())
                    .macdLine(macd.getMACDLine())
                    .macdSignal(macd.getSignalLine())
                    .macdHistogram(macd.getHistogram())
                    .timestamp(lastUpdate)
                    .build();
        }

        public void initializeWithHistoricalData(List<CandleData> historicalCandles) {
            for (CandleData candle : historicalCandles) {
                if (candle != null && candle.isValid()) {
                    updateIndicators(candle);
                }
            }
        }

        public Instant getLastUpdate() {
            return lastUpdate;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getTimeframe() {
            return timeframe;
        }
    }
}