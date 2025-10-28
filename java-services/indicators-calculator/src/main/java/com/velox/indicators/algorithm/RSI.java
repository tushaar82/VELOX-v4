package com.velox.indicators.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Relative Strength Index (RSI) indicator implementation.
 * Optimized for real-time calculation with sub-millisecond performance.
 */
@Component
@Slf4j
public class RSI {

    private final int period;
    private final Deque<BigDecimal> gains;
    private final Deque<BigDecimal> losses;
    private BigDecimal previousPrice;
    private BigDecimal avgGain;
    private BigDecimal avgLoss;
    private boolean isInitialized;

    /**
     * Constructor for RSI calculator
     */
    public RSI(int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("RSI period must be positive");
        }
        
        this.period = period;
        this.gains = new ArrayDeque<>(period);
        this.losses = new ArrayDeque<>(period);
        this.previousPrice = null;
        this.avgGain = BigDecimal.ZERO;
        this.avgLoss = BigDecimal.ZERO;
        this.isInitialized = false;
    }

    /**
     * Add new price and calculate RSI
     */
    public BigDecimal addPrice(BigDecimal price) {
        if (price == null) {
            return getCurrentValue();
        }

        if (previousPrice == null) {
            previousPrice = price;
            return null; // RSI undefined for first price
        }

        BigDecimal change = price.subtract(previousPrice);
        BigDecimal gain = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
        BigDecimal loss = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;

        gains.addLast(gain);
        losses.addLast(loss);

        if (gains.size() > period) {
            BigDecimal removedGain = gains.removeFirst();
            BigDecimal removedLoss = losses.removeFirst();
            
            // Update average gains and losses using smoothing
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gain)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
                    
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(loss)
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        } else if (gains.size() == period) {
            // First calculation - use simple average
            BigDecimal sumGains = gains.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumLosses = losses.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            
            avgGain = sumGains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
            avgLoss = sumLosses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
            
            isInitialized = true;
        }

        previousPrice = price;

        return calculateRSI();
    }

    /**
     * Calculate current RSI value
     */
    private BigDecimal calculateRSI() {
        if (!isInitialized || avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100); // RSI = 100 when no losses
        }

        if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO; // RSI = 0 when no gains
        }

        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));

        return rsi;
    }

    /**
     * Get current RSI value
     */
    public BigDecimal getCurrentValue() {
        if (!isInitialized) {
            return null;
        }
        return calculateRSI();
    }

    /**
     * Check if RSI is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Get period
     */
    public int getPeriod() {
        return period;
    }

    /**
     * Get average gain
     */
    public BigDecimal getAvgGain() {
        return avgGain;
    }

    /**
     * Get average loss
     */
    public BigDecimal getAvgLoss() {
        return avgLoss;
    }

    /**
     * Reset RSI calculator
     */
    public void reset() {
        gains.clear();
        losses.clear();
        previousPrice = null;
        avgGain = BigDecimal.ZERO;
        avgLoss = BigDecimal.ZERO;
        isInitialized = false;
    }

    /**
     * Initialize RSI with specific values
     */
    public void initialize(BigDecimal initialPrice, BigDecimal initialAvgGain, BigDecimal initialAvgLoss) {
        this.previousPrice = initialPrice;
        this.avgGain = initialAvgGain;
        this.avgLoss = initialAvgLoss;
        this.isInitialized = true;
    }

    /**
     * Calculate RSI for an array of prices
     */
    public static BigDecimal[] calculateRSI(BigDecimal[] prices, int period) {
        if (prices == null || prices.length < period + 1) {
            return new BigDecimal[0];
        }

        BigDecimal[] rsiValues = new BigDecimal[prices.length];
        BigDecimal[] gains = new BigDecimal[prices.length];
        BigDecimal[] losses = new BigDecimal[prices.length];

        // Calculate gains and losses
        for (int i = 1; i < prices.length; i++) {
            BigDecimal change = prices[i].subtract(prices[i - 1]);
            gains[i] = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            losses[i] = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;
        }

        // Calculate first average gain and loss
        BigDecimal sumGains = BigDecimal.ZERO;
        BigDecimal sumLosses = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            sumGains = sumGains.add(gains[i]);
            sumLosses = sumLosses.add(losses[i]);
        }

        BigDecimal avgGain = sumGains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLosses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        // Calculate RSI for remaining prices
        for (int i = period + 1; i < prices.length; i++) {
            // Smooth the averages
            avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gains[i])
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
                    
            avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(losses[i])
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

            // Calculate RSI
            if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                rsiValues[i] = BigDecimal.valueOf(100);
            } else if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
                rsiValues[i] = BigDecimal.ZERO;
            } else {
                BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
                rsiValues[i] = BigDecimal.valueOf(100)
                        .subtract(BigDecimal.valueOf(100)
                                .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
            }
        }

        return rsiValues;
    }

    /**
     * Calculate RSI using Wilder's smoothing method
     */
    public static BigDecimal[] calculateRSIWilder(BigDecimal[] prices, int period) {
        if (prices == null || prices.length < period + 1) {
            return new BigDecimal[0];
        }

        BigDecimal[] rsiValues = new BigDecimal[prices.length];
        BigDecimal[] gains = new BigDecimal[prices.length];
        BigDecimal[] losses = new BigDecimal[prices.length];

        // Calculate gains and losses
        for (int i = 1; i < prices.length; i++) {
            BigDecimal change = prices[i].subtract(prices[i - 1]);
            gains[i] = change.compareTo(BigDecimal.ZERO) > 0 ? change : BigDecimal.ZERO;
            losses[i] = change.compareTo(BigDecimal.ZERO) < 0 ? change.abs() : BigDecimal.ZERO;
        }

        // Calculate first average gain and loss (Wilder's smoothing)
        BigDecimal sumGains = BigDecimal.ZERO;
        BigDecimal sumLosses = BigDecimal.ZERO;
        for (int i = 1; i <= period; i++) {
            sumGains = sumGains.add(gains[i]);
            sumLosses = sumLosses.add(losses[i]);
        }

        BigDecimal avgGain = sumGains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = sumLosses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        // Calculate RSI for remaining prices using Wilder's smoothing
        for (int i = period + 1; i < prices.length; i++) {
            // Smooth the averages (Wilder's method)
            avgGain = (avgGain.multiply(BigDecimal.valueOf(period - 1)).add(gains[i]))
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
                    
            avgLoss = (avgLoss.multiply(BigDecimal.valueOf(period - 1)).add(losses[i]))
                    .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

            // Calculate RSI
            if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
                rsiValues[i] = BigDecimal.valueOf(100);
            } else if (avgGain.compareTo(BigDecimal.ZERO) == 0) {
                rsiValues[i] = BigDecimal.ZERO;
            } else {
                BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
                rsiValues[i] = BigDecimal.valueOf(100)
                        .subtract(BigDecimal.valueOf(100)
                                .divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));
            }
        }

        return rsiValues;
    }

    /**
     * Validate RSI period
     */
    public static boolean isValidPeriod(int period) {
        return period > 0 && period <= 100; // Reasonable limits
    }

    /**
     * Get optimal period for given timeframe
     */
    public static int getOptimalPeriod(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1min" -> 14;
            case "5min" -> 14;
            case "15min" -> 14;
            case "30min" -> 14;
            case "1hour" -> 14;
            case "4hour" -> 14;
            case "1day" -> 14;
            default -> 14;
        };
    }

    /**
     * Check if RSI indicates overbought condition
     */
    public static boolean isOverbought(BigDecimal rsi, BigDecimal threshold) {
        return rsi != null && rsi.compareTo(threshold) > 0;
    }

    /**
     * Check if RSI indicates oversold condition
     */
    public static boolean isOversold(BigDecimal rsi, BigDecimal threshold) {
        return rsi != null && rsi.compareTo(threshold) < 0;
    }

    /**
     * Get default overbought threshold (70)
     */
    public static BigDecimal getDefaultOverboughtThreshold() {
        return BigDecimal.valueOf(70);
    }

    /**
     * Get default oversold threshold (30)
     */
    public static BigDecimal getDefaultOversoldThreshold() {
        return BigDecimal.valueOf(30);
    }
}