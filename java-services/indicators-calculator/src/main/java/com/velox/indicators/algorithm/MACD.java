package com.velox.indicators.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Moving Average Convergence Divergence (MACD) indicator implementation.
 * Optimized for real-time calculation with sub-millisecond performance.
 */
@Component
@Slf4j
public class MACD {

    private final int fastPeriod;
    private final int slowPeriod;
    private final int signalPeriod;
    private final MovingAverage.EMA fastEMA;
    private final MovingAverage.EMA slowEMA;
    private final MovingAverage.EMA signalEMA;
    private final Deque<BigDecimal> histogramValues;
    private boolean isInitialized;

    /**
     * Constructor for MACD calculator
     */
    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        if (fastPeriod <= 0 || slowPeriod <= 0 || signalPeriod <= 0) {
            throw new IllegalArgumentException("MACD periods must be positive");
        }
        if (fastPeriod >= slowPeriod) {
            throw new IllegalArgumentException("Fast period must be less than slow period");
        }

        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
        this.fastEMA = new MovingAverage.EMA(fastPeriod);
        this.slowEMA = new MovingAverage.EMA(slowPeriod);
        this.signalEMA = new MovingAverage.EMA(signalPeriod);
        this.histogramValues = new ArrayDeque<>(signalPeriod);
        this.isInitialized = false;
    }

    /**
     * Add new price and calculate MACD values
     */
    public MACDValues addPrice(BigDecimal price) {
        if (price == null) {
            return getCurrentValues();
        }

        // Calculate EMAs
        BigDecimal fastEMAValue = fastEMA.addPrice(price);
        BigDecimal slowEMAValue = slowEMA.addPrice(price);

        // Calculate MACD line
        BigDecimal macdLine = fastEMAValue.subtract(slowEMAValue);

        // Calculate signal line
        BigDecimal signalLine = signalEMA.addPrice(macdLine);

        // Calculate histogram
        BigDecimal histogram = macdLine.subtract(signalLine);

        // Store histogram value for signal line calculation
        histogramValues.addLast(histogram);
        if (histogramValues.size() > signalPeriod) {
            histogramValues.removeFirst();
        }

        // Check if initialized
        if (fastEMA.isInitialized() && slowEMA.isInitialized() && signalEMA.isInitialized()) {
            isInitialized = true;
        }

        return new MACDValues(macdLine, signalLine, histogram);
    }

    /**
     * Get current MACD values
     */
    public MACDValues getCurrentValues() {
        if (!isInitialized) {
            return null;
        }

        BigDecimal macdLine = fastEMA.getCurrentValue().subtract(slowEMA.getCurrentValue());
        BigDecimal signalLine = signalEMA.getCurrentValue();
        BigDecimal histogram = macdLine.subtract(signalLine);

        return new MACDValues(macdLine, signalLine, histogram);
    }

    /**
     * Check if MACD is initialized
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * Get fast period
     */
    public int getFastPeriod() {
        return fastPeriod;
    }

    /**
     * Get slow period
     */
    public int getSlowPeriod() {
        return slowPeriod;
    }

    /**
     * Get signal period
     */
    public int getSignalPeriod() {
        return signalPeriod;
    }

    /**
     * Get current MACD line
     */
    public BigDecimal getMACDLine() {
        if (!isInitialized) {
            return null;
        }
        return fastEMA.getCurrentValue().subtract(slowEMA.getCurrentValue());
    }

    /**
     * Get current signal line
     */
    public BigDecimal getSignalLine() {
        return signalEMA.getCurrentValue();
    }

    /**
     * Get current histogram
     */
    public BigDecimal getHistogram() {
        if (!isInitialized) {
            return null;
        }
        return getMACDLine().subtract(getSignalLine());
    }

    /**
     * Reset MACD calculator
     */
    public void reset() {
        fastEMA.reset();
        slowEMA.reset();
        signalEMA.reset();
        histogramValues.clear();
        isInitialized = false;
    }

    /**
     * Initialize MACD with specific values
     */
    public void initialize(BigDecimal initialPrice, BigDecimal initialFastEMA, 
                     BigDecimal initialSlowEMA, BigDecimal initialSignalEMA) {
        fastEMA.initialize(initialFastEMA);
        slowEMA.initialize(initialSlowEMA);
        signalEMA.initialize(initialSignalEMA);
        isInitialized = true;
    }

    /**
     * Calculate MACD for an array of prices
     */
    public static MACDValues[] calculateMACD(BigDecimal[] prices, int fastPeriod, 
                                              int slowPeriod, int signalPeriod) {
        if (prices == null || prices.length < Math.max(fastPeriod, slowPeriod) + signalPeriod) {
            return new MACDValues[0];
        }

        // Calculate EMAs
        BigDecimal[] fastEMA = MovingAverage.calculateEMA(prices, fastPeriod);
        BigDecimal[] slowEMA = MovingAverage.calculateEMA(prices, slowPeriod);

        // Calculate MACD line
        BigDecimal[] macdLine = new BigDecimal[prices.length];
        for (int i = 0; i < prices.length; i++) {
            if (fastEMA[i] != null && slowEMA[i] != null) {
                macdLine[i] = fastEMA[i].subtract(slowEMA[i]);
            }
        }

        // Calculate signal line (EMA of MACD line)
        BigDecimal[] signalLine = MovingAverage.calculateEMA(macdLine, signalPeriod);

        // Calculate histogram
        MACDValues[] macdValues = new MACDValues[prices.length];
        for (int i = 0; i < prices.length; i++) {
            if (macdLine[i] != null && signalLine[i] != null) {
                BigDecimal histogram = macdLine[i].subtract(signalLine[i]);
                macdValues[i] = new MACDValues(macdLine[i], signalLine[i], histogram);
            }
        }

        return macdValues;
    }

    /**
     * Check for MACD bullish crossover (MACD line crosses above signal line)
     */
    public static boolean isBullishCrossover(MACDValues previous, MACDValues current) {
        if (previous == null || current == null) {
            return false;
        }
        return previous.getMACDLine().compareTo(previous.getSignalLine()) <= 0 &&
               current.getMACDLine().compareTo(current.getSignalLine()) > 0;
    }

    /**
     * Check for MACD bearish crossover (MACD line crosses below signal line)
     */
    public static boolean isBearishCrossover(MACDValues previous, MACDValues current) {
        if (previous == null || current == null) {
            return false;
        }
        return previous.getMACDLine().compareTo(previous.getSignalLine()) >= 0 &&
               current.getMACDLine().compareTo(current.getSignalLine()) < 0;
    }

    /**
     * Check for MACD bullish divergence (price makes higher high but MACD doesn't)
     */
    public static boolean isBullishDivergence(BigDecimal[] prices, MACDValues[] macdValues, 
                                           int lookbackPeriod) {
        if (prices == null || macdValues == null || 
            prices.length < lookbackPeriod + 1 || macdValues.length < lookbackPeriod + 1) {
            return false;
        }

        // Find recent price high and corresponding MACD high
        BigDecimal recentPriceHigh = prices[prices.length - 1];
        BigDecimal recentMACDHigh = macdValues[macdValues.length - 1].getMACDLine();

        for (int i = 2; i <= lookbackPeriod; i++) {
            int priceIndex = prices.length - i;
            int macdIndex = macdValues.length - i;

            if (prices[priceIndex].compareTo(recentPriceHigh) > 0) {
                recentPriceHigh = prices[priceIndex];
            }

            if (macdValues[macdIndex].getMACDLine().compareTo(recentMACDHigh) > 0) {
                recentMACDHigh = macdValues[macdIndex].getMACDLine();
            }
        }

        // Check for divergence (price higher but MACD lower)
        return recentPriceHigh.compareTo(prices[prices.length - 1]) > 0 &&
               recentMACDHigh.compareTo(macdValues[macdValues.length - 1].getMACDLine()) > 0;
    }

    /**
     * Check for MACD bearish divergence (price makes lower low but MACD doesn't)
     */
    public static boolean isBearishDivergence(BigDecimal[] prices, MACDValues[] macdValues, 
                                           int lookbackPeriod) {
        if (prices == null || macdValues == null || 
            prices.length < lookbackPeriod + 1 || macdValues.length < lookbackPeriod + 1) {
            return false;
        }

        // Find recent price low and corresponding MACD low
        BigDecimal recentPriceLow = prices[prices.length - 1];
        BigDecimal recentMACDLow = macdValues[macdValues.length - 1].getMACDLine();

        for (int i = 2; i <= lookbackPeriod; i++) {
            int priceIndex = prices.length - i;
            int macdIndex = macdValues.length - i;

            if (prices[priceIndex].compareTo(recentPriceLow) < 0) {
                recentPriceLow = prices[priceIndex];
            }

            if (macdValues[macdIndex].getMACDLine().compareTo(recentMACDLow) < 0) {
                recentMACDLow = macdValues[macdIndex].getMACDLine();
            }
        }

        // Check for divergence (price lower but MACD higher)
        return recentPriceLow.compareTo(prices[prices.length - 1]) < 0 &&
               recentMACDLow.compareTo(macdValues[macdValues.length - 1].getMACDLine()) < 0;
    }

    /**
     * Validate MACD periods
     */
    public static boolean isValidPeriods(int fastPeriod, int slowPeriod, int signalPeriod) {
        return fastPeriod > 0 && slowPeriod > 0 && signalPeriod > 0 &&
               fastPeriod < slowPeriod &&
               fastPeriod <= 50 && slowPeriod <= 200 && signalPeriod <= 50;
    }

    /**
     * Get optimal periods for given timeframe
     */
    public static MACDPeriods getOptimalPeriods(String timeframe) {
        return switch (timeframe.toLowerCase()) {
            case "1min" -> new MACDPeriods(12, 26, 9);
            case "5min" -> new MACDPeriods(12, 26, 9);
            case "15min" -> new MACDPeriods(12, 26, 9);
            case "30min" -> new MACDPeriods(12, 26, 9);
            case "1hour" -> new MACDPeriods(12, 26, 9);
            case "4hour" -> new MACDPeriods(12, 26, 9);
            case "1day" -> new MACDPeriods(12, 26, 9);
            default -> new MACDPeriods(12, 26, 9);
        };
    }

    /**
     * Container class for MACD values
     */
    public static class MACDValues {
        private final BigDecimal macdLine;
        private final BigDecimal signalLine;
        private final BigDecimal histogram;

        public MACDValues(BigDecimal macdLine, BigDecimal signalLine, BigDecimal histogram) {
            this.macdLine = macdLine;
            this.signalLine = signalLine;
            this.histogram = histogram;
        }

        public BigDecimal getMACDLine() {
            return macdLine;
        }

        public BigDecimal getSignalLine() {
            return signalLine;
        }

        public BigDecimal getHistogram() {
            return histogram;
        }

        @Override
        public String toString() {
            return String.format("MACD[%.4f, Signal[%.4f, Hist[%.4f]", 
                             macdLine, signalLine, histogram);
        }
    }

    /**
     * Container class for MACD periods
     */
    public static class MACDPeriods {
        private final int fastPeriod;
        private final int slowPeriod;
        private final int signalPeriod;

        public MACDPeriods(int fastPeriod, int slowPeriod, int signalPeriod) {
            this.fastPeriod = fastPeriod;
            this.slowPeriod = slowPeriod;
            this.signalPeriod = signalPeriod;
        }

        public int getFastPeriod() {
            return fastPeriod;
        }

        public int getSlowPeriod() {
            return slowPeriod;
        }

        public int getSignalPeriod() {
            return signalPeriod;
        }
    }
}