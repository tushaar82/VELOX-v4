package com.velox.indicators.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Model for indicator values.
 * Represents calculated technical indicators for a symbol and timeframe.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorValue {

    /**
     * Trading symbol
     */
    private String symbol;

    /**
     * Timeframe for indicators
     */
    private String timeframe;

    /**
     * Timestamp when indicators were calculated
     */
    private Instant timestamp;

    // Moving Averages
    /**
     * 20-period Simple Moving Average
     */
    private BigDecimal sma20;

    /**
     * 20-period Exponential Moving Average
     */
    private BigDecimal ema20;

    /**
     * 50-period Simple Moving Average
     */
    private BigDecimal sma50;

    /**
     * 50-period Exponential Moving Average
     */
    private BigDecimal ema50;

    /**
     * 200-period Simple Moving Average
     */
    private BigDecimal sma200;

    /**
     * 200-period Exponential Moving Average
     */
    private BigDecimal ema200;

    // Momentum Indicators
    /**
     * 14-period Relative Strength Index
     */
    private BigDecimal rsi14;

    /**
     * 9-period Relative Strength Index
     */
    private BigDecimal rsi9;

    /**
     * Stochastic oscillator %K
     */
    private BigDecimal stochasticK;

    /**
     * Stochastic oscillator %D
     */
    private BigDecimal stochasticD;

    // MACD Values
    /**
     * MACD line
     */
    private BigDecimal macdLine;

    /**
     * MACD signal line
     */
    private BigDecimal macdSignal;

    /**
     * MACD histogram
     */
    private BigDecimal macdHistogram;

    // Bollinger Bands
    /**
     * Bollinger Bands middle band (typically 20-period SMA)
     */
    private BigDecimal bollingerMiddle;

    /**
     * Bollinger Bands upper band
     */
    private BigDecimal bollingerUpper;

    /**
     * Bollinger Bands lower band
     */
    private BigDecimal bollingerLower;

    /**
     * Bollinger Bands width (upper - lower)
     */
    private BigDecimal bollingerWidth;

    /**
     * Bollinger Bands %B (price position within bands)
     */
    private BigDecimal bollingerPercentB;

    // Volume Indicators
    /**
     * Volume Weighted Average Price
     */
    private BigDecimal vwap;

    /**
     * On-Balance Volume
     */
    private BigDecimal obv;

    /**
     * Volume Rate of Change
     */
    private BigDecimal volumeROC;

    /**
     * Accumulation/Distribution Line
     */
    private BigDecimal adl;

    // Volatility Indicators
    /**
     * Average True Range
     */
    private BigDecimal atr;

    /**
     * Historical Volatility (standard deviation of returns)
     */
    private BigDecimal historicalVolatility;

    /**
     * Price Rate of Change
     */
    private BigDecimal priceROC;

    /**
     * Momentum
     */
    private BigDecimal momentum;

    // Support/Resistance Levels
    /**
     * Pivot point high
     */
    private BigDecimal pivotHigh;

    /**
     * Pivot point low
     */
    private BigDecimal pivotLow;

    /**
     * Pivot point close
     */
    private BigDecimal pivotClose;

    /**
     * First support level
     */
    private BigDecimal support1;

    /**
     * Second support level
     */
    private BigDecimal support2;

    /**
     * First resistance level
     */
    private BigDecimal resistance1;

    /**
     * Second resistance level
     */
    private BigDecimal resistance2;

    // Pattern Recognition
    /**
     * Current trend direction
     */
    private TrendDirection trendDirection;

    /**
     * Trend strength (0-100)
     */
    private BigDecimal trendStrength;

    /**
     * Pattern type detected
     */
    private PatternType patternType;

    /**
     * Pattern confidence level (0-100)
     */
    private BigDecimal patternConfidence;

    // Additional Metadata
    /**
     * Data quality indicator
     */
    private DataQuality dataQuality;

    /**
     * Number of data points used for calculation
     */
    private Integer dataPoints;

    /**
     * Calculation time in milliseconds
     */
    private Long calculationTimeMs;

    /**
     * Check if indicator value is valid
     */
    public boolean isValid() {
        return symbol != null && !symbol.trim().isEmpty() &&
               timeframe != null && !timeframe.trim().isEmpty() &&
               timestamp != null;
    }

    /**
     * Check if key indicators are available
     */
    public boolean hasKeyIndicators() {
        return sma20 != null && ema20 != null && rsi14 != null && 
               macdLine != null && macdSignal != null && macdHistogram != null;
    }

    /**
     * Check if moving averages are available
     */
    public boolean hasMovingAverages() {
        return sma20 != null && ema20 != null && sma50 != null && ema50 != null &&
               sma200 != null && ema200 != null;
    }

    /**
     * Check if momentum indicators are available
     */
    public boolean hasMomentumIndicators() {
        return rsi14 != null && rsi9 != null && stochasticK != null && stochasticD != null;
    }

    /**
     * Check if volatility indicators are available
     */
    public boolean hasVolatilityIndicators() {
        return atr != null && historicalVolatility != null && priceROC != null;
    }

    /**
     * Check if volume indicators are available
     */
    public boolean hasVolumeIndicators() {
        return vwap != null && obv != null && volumeROC != null;
    }

    /**
     * Check if Bollinger Bands are available
     */
    public boolean hasBollingerBands() {
        return bollingerMiddle != null && bollingerUpper != null && bollingerLower != null;
    }

    /**
     * Check if support/resistance levels are available
     */
    public boolean hasSupportResistance() {
        return support1 != null && support2 != null && resistance1 != null && resistance2 != null;
    }

    /**
     * Get trend direction based on moving averages
     */
    public TrendDirection getMATrendDirection() {
        if (!hasMovingAverages()) {
            return TrendDirection.NEUTRAL;
        }

        // Short-term vs long-term trend
        if (ema20.compareTo(ema50) > 0 && ema50.compareTo(ema200) > 0) {
            return TrendDirection.BULLISH;
        } else if (ema20.compareTo(ema50) < 0 && ema50.compareTo(ema200) < 0) {
            return TrendDirection.BEARISH;
        } else {
            return TrendDirection.NEUTRAL;
        }
    }

    /**
     * Check if RSI indicates overbought
     */
    public boolean isRSIOverbought() {
        return rsi14 != null && rsi14.compareTo(BigDecimal.valueOf(70)) > 0;
    }

    /**
     * Check if RSI indicates oversold
     */
    public boolean isRSIOversold() {
        return rsi14 != null && rsi14.compareTo(BigDecimal.valueOf(30)) < 0;
    }

    /**
     * Check if MACD shows bullish crossover
     */
    public boolean isMACDBullishCrossover() {
        return macdLine != null && macdSignal != null && 
               macdLine.compareTo(macdSignal) > 0;
    }

    /**
     * Check if MACD shows bearish crossover
     */
    public boolean isMACDBearishCrossover() {
        return macdLine != null && macdSignal != null && 
               macdLine.compareTo(macdSignal) < 0;
    }

    /**
     * Check if price is above Bollinger upper band
     */
    public boolean isPriceAboveBollingerUpper(BigDecimal price) {
        return hasBollingerBands() && price != null && 
               price.compareTo(bollingerUpper) > 0;
    }

    /**
     * Check if price is below Bollinger lower band
     */
    public boolean isPriceBelowBollingerLower(BigDecimal price) {
        return hasBollingerBands() && price != null && 
               price.compareTo(bollingerLower) < 0;
    }

    /**
     * Trend direction enum
     */
    public enum TrendDirection {
        BULLISH("Bullish"),
        BEARISH("Bearish"),
        NEUTRAL("Neutral"),
        SIDEWAYS("Sideways");

        private final String description;

        TrendDirection(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Pattern type enum
     */
    public enum PatternType {
        NONE("None"),
        DOJI("Doji"),
        HAMMER("Hammer"),
        SHOOTING_STAR("Shooting Star"),
        ENGULFING_BULLISH("Bullish Engulfing"),
        ENGULFING_BEARISH("Bearish Engulfing"),
        MORNING_STAR("Morning Star"),
        EVENING_STAR("Evening Star"),
        HEAD_SHOULDERS("Head and Shoulders"),
        DOUBLE_TOP("Double Top"),
        DOUBLE_BOTTOM("Double Bottom"),
        TRIANGLE("Triangle"),
        FLAG("Flag"),
        PENNANT("Pennant");

        private final String description;

        PatternType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Data quality enum
     */
    public enum DataQuality {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        FAIR("Fair"),
        POOR("Poor"),
        INSUFFICIENT_DATA("Insufficient Data");

        private final String description;

        DataQuality(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Create a copy of this indicator value
     */
    public IndicatorValue copy() {
        return IndicatorValue.builder()
                .symbol(this.symbol)
                .timeframe(this.timeframe)
                .timestamp(this.timestamp)
                .sma20(this.sma20)
                .ema20(this.ema20)
                .sma50(this.sma50)
                .ema50(this.ema50)
                .sma200(this.sma200)
                .ema200(this.ema200)
                .rsi14(this.rsi14)
                .rsi9(this.rsi9)
                .stochasticK(this.stochasticK)
                .stochasticD(this.stochasticD)
                .macdLine(this.macdLine)
                .macdSignal(this.macdSignal)
                .macdHistogram(this.macdHistogram)
                .bollingerMiddle(this.bollingerMiddle)
                .bollingerUpper(this.bollingerUpper)
                .bollingerLower(this.bollingerLower)
                .bollingerWidth(this.bollingerWidth)
                .bollingerPercentB(this.bollingerPercentB)
                .vwap(this.vwap)
                .obv(this.obv)
                .volumeROC(this.volumeROC)
                .adl(this.adl)
                .atr(this.atr)
                .historicalVolatility(this.historicalVolatility)
                .priceROC(this.priceROC)
                .momentum(this.momentum)
                .pivotHigh(this.pivotHigh)
                .pivotLow(this.pivotLow)
                .pivotClose(this.pivotClose)
                .support1(this.support1)
                .support2(this.support2)
                .resistance1(this.resistance1)
                .resistance2(this.resistance2)
                .trendDirection(this.trendDirection)
                .trendStrength(this.trendStrength)
                .patternType(this.patternType)
                .patternConfidence(this.patternConfidence)
                .dataQuality(this.dataQuality)
                .dataPoints(this.dataPoints)
                .calculationTimeMs(this.calculationTimeMs)
                .build();
    }
}