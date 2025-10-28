package com.velox.marketdata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Model representing a candle that is currently forming from real-time tick data.
 * This is an in-memory model used for real-time candle formation and is not persisted.
 * Optimized for high-frequency updates with atomic operations for thread safety.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormingCandle {

    /**
     * Trading symbol
     */
    private String symbol;

    /**
     * Exchange where instrument is traded
     */
    private String exchange;

    /**
     * Timeframe for the candle
     */
    private CandleData.Timeframe timeframe;

    /**
     * Candle start timestamp
     */
    private Instant timestamp;

    /**
     * Candle end timestamp
     */
    private Instant endTime;

    /**
     * Opening price - atomic for thread safety
     */
    private final AtomicReference<BigDecimal> openPrice = new AtomicReference<>();

    /**
     * Highest price - atomic for thread safety
     */
    private final AtomicReference<BigDecimal> highPrice = new AtomicReference<>();

    /**
     * Lowest price - atomic for thread safety
     */
    private final AtomicReference<BigDecimal> lowPrice = new AtomicReference<>();

    /**
     * Closing price - atomic for thread safety
     */
    private final AtomicReference<BigDecimal> closePrice = new AtomicReference<>();

    /**
     * Total volume - atomic for thread safety
     */
    private final AtomicLong volume = new AtomicLong();

    /**
     * Total traded value - atomic for thread safety
     */
    private final AtomicReference<BigDecimal> value = new AtomicReference<>();

    /**
     * Number of trades - atomic for thread safety
     */
    private final AtomicLong tradeCount = new AtomicLong();

    /**
     * Number of ticks processed - atomic for thread safety
     */
    private final AtomicLong tickCount = new AtomicLong();

    /**
     * Last update timestamp
     */
    private volatile Instant lastUpdate;

    /**
     * Whether this candle is complete
     */
    private volatile boolean isComplete;

    /**
     * Source of the candle data
     */
    private String source;

    /**
     * Initialize a new forming candle with the first tick
     */
    public static FormingCandle initialize(TickData tick, CandleData.Timeframe timeframe) {
        Instant startTime = calculateStartTime(tick.getTimestamp(), timeframe);
        Instant endTime = calculateEndTime(startTime, timeframe);

        return FormingCandle.builder()
                .symbol(tick.getSymbol())
                .exchange(tick.getExchange())
                .timeframe(timeframe)
                .timestamp(startTime)
                .endTime(endTime)
                .openPrice(tick.getLastPrice())
                .highPrice(tick.getLastPrice())
                .lowPrice(tick.getLastPrice())
                .closePrice(tick.getLastPrice())
                .volume(tick.getVolume() != null ? tick.getVolume() : 0L)
                .value(tick.getValue())
                .tradeCount(tick.getTradeCount() != null ? tick.getTradeCount().longValue() : 0L)
                .tickCount(1L)
                .lastUpdate(tick.getTimestamp())
                .isComplete(false)
                .source(tick.getSource())
                .build();
    }

    /**
     * Update the forming candle with a new tick
     */
    public void updateWithTick(TickData tick) {
        if (isComplete || !shouldIncludeTick(tick)) {
            return;
        }

        BigDecimal tickPrice = tick.getLastPrice();
        
        // Update high price
        highPrice.updateAndGet(current -> 
            current == null || tickPrice.compareTo(current) > 0 ? tickPrice : current);

        // Update low price
        lowPrice.updateAndGet(current -> 
            current == null || tickPrice.compareTo(current) < 0 ? tickPrice : current);

        // Update close price
        closePrice.set(tickPrice);

        // Update volume
        if (tick.getVolume() != null) {
            volume.addAndGet(tick.getVolume());
        }

        // Update value
        if (tick.getValue() != null) {
            value.updateAndGet(current -> 
                current == null ? tick.getValue() : current.add(tick.getValue()));
        }

        // Update trade count
        if (tick.getTradeCount() != null) {
            tradeCount.addAndGet(tick.getTradeCount().longValue());
        }

        // Increment tick count
        tickCount.incrementAndGet();

        // Update last update timestamp
        lastUpdate = tick.getTimestamp();
    }

    /**
     * Check if a tick should be included in this candle
     */
    public boolean shouldIncludeTick(TickData tick) {
        return tick != null &&
               symbol.equals(tick.getSymbol()) &&
               exchange.equals(tick.getExchange()) &&
               !tick.getTimestamp().isAfter(endTime) &&
               tick.isValid();
    }

    /**
     * Mark the candle as complete
     */
    public void markComplete() {
        this.isComplete = true;
    }

    /**
     * Convert to a persistent CandleData entity
     */
    public CandleData toCandleData() {
        return CandleData.builder()
                .symbol(symbol)
                .exchange(exchange)
                .timeframe(timeframe)
                .timestamp(timestamp)
                .openPrice(openPrice.get())
                .highPrice(highPrice.get())
                .lowPrice(lowPrice.get())
                .closePrice(closePrice.get())
                .volume(volume.get())
                .value(value.get())
                .tradeCount(tradeCount.get() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tradeCount.get())
                .vwap(calculateVWAP())
                .isComplete(isComplete)
                .tickCount(tickCount.get() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) tickCount.get())
                .source(source)
                .quality(determineQuality())
                .build();
    }

    /**
     * Calculate Volume Weighted Average Price (VWAP)
     */
    private BigDecimal calculateVWAP() {
        BigDecimal totalValue = value.get();
        Long totalVolume = volume.get();
        
        if (totalValue != null && totalVolume != null && totalVolume > 0) {
            return totalValue.divide(BigDecimal.valueOf(totalVolume), 4, BigDecimal.ROUND_HALF_UP);
        }
        return null;
    }

    /**
     * Determine candle quality based on various factors
     */
    private CandleData.CandleQuality determineQuality() {
        if (!isComplete) {
            return CandleData.CandleQuality.INCOMPLETE;
        }

        Long tickCountValue = tickCount.get();
        if (tickCountValue < 5) {
            return CandleData.CandleQuality.LOW_VOLUME;
        }

        BigDecimal range = highPrice.get().subtract(lowPrice.get());
        BigDecimal avgPrice = openPrice.get().add(closePrice.get()).divide(BigDecimal.valueOf(2), 4, BigDecimal.ROUND_HALF_UP);
        BigDecimal rangePercentage = range.divide(avgPrice, 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));

        if (rangePercentage.compareTo(BigDecimal.valueOf(10)) > 0) {
            return CandleData.CandleQuality.SUSPICIOUS;
        }

        return CandleData.CandleQuality.GOOD;
    }

    /**
     * Calculate candle start time based on timeframe
     */
    private static Instant calculateStartTime(Instant tickTime, CandleData.Timeframe timeframe) {
        if (timeframe == CandleData.Timeframe.TICK) {
            return tickTime;
        }

        long epochSeconds = tickTime.getEpochSecond();
        long duration = timeframe.getDurationInSeconds();
        
        if (duration > 0) {
            long periodStart = (epochSeconds / duration) * duration;
            return Instant.ofEpochSecond(periodStart);
        }
        
        return tickTime;
    }

    /**
     * Calculate candle end time based on start time and timeframe
     */
    private static Instant calculateEndTime(Instant startTime, CandleData.Timeframe timeframe) {
        if (timeframe == CandleData.Timeframe.TICK) {
            return startTime;
        }
        
        long duration = timeframe.getDurationInSeconds();
        return startTime.plusSeconds(duration);
    }

    /**
     * Check if the candle should be completed based on current time
     */
    public boolean shouldComplete(Instant currentTime) {
        return !isComplete && !currentTime.isBefore(endTime);
    }

    /**
     * Get the current age of the candle in seconds
     */
    public long getAgeInSeconds(Instant currentTime) {
        return currentTime.getEpochSecond() - timestamp.getEpochSecond();
    }

    /**
     * Get the remaining time until candle completion in seconds
     */
    public long getRemainingSeconds(Instant currentTime) {
        return endTime.getEpochSecond() - currentTime.getEpochSecond();
    }

    /**
     * Check if this forming candle is valid
     */
    public boolean isValid() {
        return symbol != null && !symbol.trim().isEmpty() &&
               exchange != null && !exchange.trim().isEmpty() &&
               timeframe != null &&
               timestamp != null &&
               endTime != null &&
               openPrice.get() != null &&
               highPrice.get() != null &&
               lowPrice.get() != null &&
               closePrice.get() != null &&
               openPrice.get().compareTo(BigDecimal.ZERO) > 0;
    }
}