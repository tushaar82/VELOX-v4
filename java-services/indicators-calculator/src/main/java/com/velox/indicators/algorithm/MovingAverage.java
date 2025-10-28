package com.velox.indicators.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Implementation of moving average indicators (SMA and EMA).
 * Optimized for real-time calculation with sub-millisecond performance.
 */
@Component
@Slf4j
public class MovingAverage {

    /**
     * Simple Moving Average (SMA) calculator
     */
    public static class SMA {
        private final int period;
        private final Deque<BigDecimal> prices;
        private BigDecimal sum;
        private boolean isInitialized;

        public SMA(int period) {
            this.period = period;
            this.prices = new ArrayDeque<>(period);
            this.sum = BigDecimal.ZERO;
            this.isInitialized = false;
        }

        /**
         * Add new price and calculate SMA
         */
        public BigDecimal addPrice(BigDecimal price) {
            if (price == null) {
                return getCurrentValue();
            }

            prices.addLast(price);
            sum = sum.add(price);

            if (prices.size() > period) {
                BigDecimal removedPrice = prices.removeFirst();
                sum = sum.subtract(removedPrice);
            }

            if (prices.size() >= period) {
                isInitialized = true;
            }

            return getCurrentValue();
        }

        /**
         * Get current SMA value
         */
        public BigDecimal getCurrentValue() {
            if (!isInitialized || prices.isEmpty()) {
                return null;
            }

            return sum.divide(BigDecimal.valueOf(prices.size()), 8, RoundingMode.HALF_UP);
        }

        /**
         * Check if SMA is initialized
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
         * Reset SMA calculator
         */
        public void reset() {
            prices.clear();
            sum = BigDecimal.ZERO;
            isInitialized = false;
        }

        /**
         * Get current count of prices
         */
        public int getCurrentCount() {
            return prices.size();
        }
    }

    /**
     * Exponential Moving Average (EMA) calculator
     */
    public static class EMA {
        private final int period;
        private final BigDecimal multiplier;
        private BigDecimal previousEMA;
        private boolean isInitialized;

        public EMA(int period) {
            this.period = period;
            this.multiplier = BigDecimal.valueOf(2.0 / (period + 1));
            this.previousEMA = null;
            this.isInitialized = false;
        }

        /**
         * Add new price and calculate EMA
         */
        public BigDecimal addPrice(BigDecimal price) {
            if (price == null) {
                return getCurrentValue();
            }

            if (previousEMA == null) {
                // First price becomes the initial EMA
                previousEMA = price;
                isInitialized = true;
                return price;
            }

            // Calculate EMA: EMA = (Close * K) + (Previous EMA * (1 - K))
            BigDecimal currentEMA = price.multiply(multiplier)
                    .add(previousEMA.multiply(BigDecimal.ONE.subtract(multiplier)));

            previousEMA = currentEMA;

            return currentEMA;
        }

        /**
         * Get current EMA value
         */
        public BigDecimal getCurrentValue() {
            return previousEMA;
        }

        /**
         * Check if EMA is initialized
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
         * Get multiplier
         */
        public BigDecimal getMultiplier() {
            return multiplier;
        }

        /**
         * Reset EMA calculator
         */
        public void reset() {
            previousEMA = null;
            isInitialized = false;
        }

        /**
         * Initialize EMA with a specific value
         */
        public void initialize(BigDecimal initialValue) {
            if (initialValue != null) {
                this.previousEMA = initialValue;
                this.isInitialized = true;
            }
        }
    }

    /**
     * Weighted Moving Average (WMA) calculator
     */
    public static class WMA {
        private final int period;
        private final Deque<BigDecimal> prices;
        private boolean isInitialized;

        public WMA(int period) {
            this.period = period;
            this.prices = new ArrayDeque<>(period);
            this.isInitialized = false;
        }

        /**
         * Add new price and calculate WMA
         */
        public BigDecimal addPrice(BigDecimal price) {
            if (price == null) {
                return getCurrentValue();
            }

            prices.addLast(price);

            if (prices.size() > period) {
                prices.removeFirst();
            }

            if (prices.size() >= period) {
                isInitialized = true;
            }

            return getCurrentValue();
        }

        /**
         * Get current WMA value
         */
        public BigDecimal getCurrentValue() {
            if (!isInitialized || prices.isEmpty()) {
                return null;
            }

            BigDecimal weightedSum = BigDecimal.ZERO;
            BigDecimal weightSum = BigDecimal.ZERO;
            int size = prices.size();
            int index = 1;

            for (BigDecimal price : prices) {
                BigDecimal weight = BigDecimal.valueOf(index);
                weightedSum = weightedSum.add(price.multiply(weight));
                weightSum = weightSum.add(weight);
                index++;
            }

            return weightedSum.divide(weightSum, 8, RoundingMode.HALF_UP);
        }

        /**
         * Check if WMA is initialized
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
         * Reset WMA calculator
         */
        public void reset() {
            prices.clear();
            isInitialized = false;
        }
    }

    /**
     * Calculate SMA for an array of prices
     */
    public static BigDecimal calculateSMA(BigDecimal[] prices, int period) {
        if (prices == null || prices.length < period) {
            return null;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int start = Math.max(0, prices.length - period);

        for (int i = start; i < prices.length; i++) {
            if (prices[i] != null) {
                sum = sum.add(prices[i]);
            }
        }

        return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    /**
     * Calculate EMA for an array of prices
     */
    public static BigDecimal[] calculateEMA(BigDecimal[] prices, int period) {
        if (prices == null || prices.length == 0) {
            return new BigDecimal[0];
        }

        BigDecimal[] emaValues = new BigDecimal[prices.length];
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));

        // Initialize with first price
        emaValues[0] = prices[0];

        for (int i = 1; i < prices.length; i++) {
            if (prices[i] != null && emaValues[i - 1] != null) {
                emaValues[i] = prices[i].multiply(multiplier)
                        .add(emaValues[i - 1].multiply(BigDecimal.ONE.subtract(multiplier)));
            } else {
                emaValues[i] = emaValues[i - 1];
            }
        }

        return emaValues;
    }

    /**
     * Calculate WMA for an array of prices
     */
    public static BigDecimal calculateWMA(BigDecimal[] prices, int period) {
        if (prices == null || prices.length < period) {
            return null;
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightSum = BigDecimal.ZERO;
        int start = Math.max(0, prices.length - period);

        for (int i = start; i < prices.length; i++) {
            if (prices[i] != null) {
                int weight = (i - start) + 1;
                weightedSum = weightedSum.add(prices[i].multiply(BigDecimal.valueOf(weight)));
                weightSum = weightSum.add(BigDecimal.valueOf(weight));
            }
        }

        return weightedSum.divide(weightSum, 8, RoundingMode.HALF_UP);
    }

    /**
     * Validate period parameter
     */
    public static boolean isValidPeriod(int period) {
        return period > 0 && period <= 1000; // Reasonable limits
    }

    /**
     * Get optimal period for given timeframe
     */
    public static int getOptimalPeriod(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1min" -> 20;
            case "5min" -> 20;
            case "15min" -> 20;
            case "30min" -> 20;
            case "1hour" -> 20;
            case "4hour" -> 20;
            case "1day" -> 20;
            default -> 20;
        };
    }
}