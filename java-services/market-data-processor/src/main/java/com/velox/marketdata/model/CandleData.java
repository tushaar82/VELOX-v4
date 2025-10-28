package com.velox.marketdata.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity representing candle/OHLCV data for different timeframes.
 * Stores aggregated market data for technical analysis and charting.
 */
@Entity
@Table(name = "candle_data", indexes = {
    @Index(name = "idx_symbol_timeframe_timestamp", columnList = "symbol, timeframe, timestamp"),
    @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_timeframe_timestamp", columnList = "timeframe, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CandleData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Trading symbol (e.g., "RELIANCE-EQ", "NIFTY-50")
     */
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    /**
     * Exchange where instrument is traded
     */
    @Column(name = "exchange", nullable = false, length = 20)
    private String exchange;

    /**
     * Timeframe for the candle (e.g., "1min", "5min", "15min", "30min", "1hour", "1day")
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "timeframe", nullable = false, length = 10)
    private Timeframe timeframe;

    /**
     * Candle start timestamp in UTC
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Opening price for the period
     */
    @Column(name = "open_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal openPrice;

    /**
     * Highest price for the period
     */
    @Column(name = "high_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal highPrice;

    /**
     * Lowest price for the period
     */
    @Column(name = "low_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lowPrice;

    /**
     * Closing price for the period
     */
    @Column(name = "close_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal closePrice;

    /**
     * Total volume traded during the period
     */
    @Column(name = "volume", nullable = false)
    private Long volume;

    /**
     * Total traded value during the period
     */
    @Column(name = "value", precision = 19, scale = 2)
    private BigDecimal value;

    /**
     * Number of trades during the period
     */
    @Column(name = "trade_count")
    private Integer tradeCount;

    /**
     * Volume weighted average price for the period
     */
    @Column(name = "vwap", precision = 19, scale = 4)
    private BigDecimal vwap;

    /**
     * Whether this candle is complete or still forming
     */
    @Column(name = "is_complete", nullable = false)
    private Boolean isComplete;

    /**
     * Number of ticks that formed this candle
     */
    @Column(name = "tick_count")
    private Integer tickCount;

    /**
     * Source of candle data (e.g., "SYSTEM", "MANUAL")
     */
    @Column(name = "source", length = 20)
    private String source;

    /**
     * Candle data quality indicator
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "quality", length = 10)
    private CandleQuality quality;

    /**
     * Record creation timestamp
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Record last update timestamp
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Enum for supported timeframes
     */
    public enum Timeframe {
        TICK("Tick"),
        ONE_MINUTE("1min"),
        FIVE_MINUTES("5min"),
        FIFTEEN_MINUTES("15min"),
        THIRTY_MINUTES("30min"),
        ONE_HOUR("1hour"),
        FOUR_HOURS("4hour"),
        ONE_DAY("1day"),
        ONE_WEEK("1week"),
        ONE_MONTH("1month");

        private final String displayName;

        Timeframe(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Get timeframe duration in seconds
         */
        public long getDurationInSeconds() {
            return switch (this) {
                case TICK -> 0;
                case ONE_MINUTE -> 60;
                case FIVE_MINUTES -> 300;
                case FIFTEEN_MINUTES -> 900;
                case THIRTY_MINUTES -> 1800;
                case ONE_HOUR -> 3600;
                case FOUR_HOURS -> 14400;
                case ONE_DAY -> 86400;
                case ONE_WEEK -> 604800;
                case ONE_MONTH -> 2592000; // Approximate
            };
        }

        /**
         * Get next higher timeframe
         */
        public Timeframe getNextHigher() {
            return switch (this) {
                case TICK -> ONE_MINUTE;
                case ONE_MINUTE -> FIVE_MINUTES;
                case FIVE_MINUTES -> FIFTEEN_MINUTES;
                case FIFTEEN_MINUTES -> THIRTY_MINUTES;
                case THIRTY_MINUTES -> ONE_HOUR;
                case ONE_HOUR -> FOUR_HOURS;
                case FOUR_HOURS -> ONE_DAY;
                case ONE_DAY -> ONE_WEEK;
                case ONE_WEEK -> ONE_MONTH;
                case ONE_MONTH -> ONE_MONTH; // Highest
            };
        }
    }

    /**
     * Enum for candle data quality
     */
    public enum CandleQuality {
        GOOD("Good quality candle"),
        INCOMPLETE("Incomplete candle"),
        LOW_VOLUME("Low volume candle"),
        GAP("Gap detected"),
        SUSPICIOUS("Suspicious price movement");

        private final String description;

        CandleQuality(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Calculates the body size of the candle
     */
    public BigDecimal getBodySize() {
        return closePrice.subtract(openPrice).abs();
    }

    /**
     * Calculates the upper shadow size
     */
    public BigDecimal getUpperShadow() {
        return highPrice.subtract(openPrice.max(closePrice));
    }

    /**
     * Calculates the lower shadow size
     */
    public BigDecimal getLowerShadow() {
        return openPrice.min(closePrice).subtract(lowPrice);
    }

    /**
     * Calculates the total range (high - low)
     */
    public BigDecimal getRange() {
        return highPrice.subtract(lowPrice);
    }

    /**
     * Calculates the percentage change for the period
     */
    public BigDecimal getPercentageChange() {
        if (openPrice.compareTo(BigDecimal.ZERO) > 0) {
            return closePrice.subtract(openPrice)
                    .divide(openPrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return null;
    }

    /**
     * Determines if this is a bullish candle (close > open)
     */
    public boolean isBullish() {
        return closePrice.compareTo(openPrice) > 0;
    }

    /**
     * Determines if this is a bearish candle (close < open)
     */
    public boolean isBearish() {
        return closePrice.compareTo(openPrice) < 0;
    }

    /**
     * Determines if this is a doji candle (open ≈ close)
     */
    public boolean isDoji() {
        BigDecimal threshold = openPrice.multiply(BigDecimal.valueOf(0.001)); // 0.1% threshold
        return closePrice.subtract(openPrice).abs().compareTo(threshold) <= 0;
    }

    /**
     * Checks if this candle is valid for processing
     */
    public boolean isValid() {
        return symbol != null && !symbol.trim().isEmpty() &&
               timeframe != null &&
               timestamp != null &&
               openPrice != null && openPrice.compareTo(BigDecimal.ZERO) > 0 &&
               highPrice != null && highPrice.compareTo(BigDecimal.ZERO) > 0 &&
               lowPrice != null && lowPrice.compareTo(BigDecimal.ZERO) > 0 &&
               closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0 &&
               volume != null && volume >= 0 &&
               highPrice.compareTo(lowPrice) >= 0 &&
               highPrice.compareTo(openPrice.max(closePrice)) >= 0 &&
               lowPrice.compareTo(openPrice.min(closePrice)) <= 0;
    }
}