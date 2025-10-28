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
 * Entity representing a single tick of market data.
 * Stores high-frequency price and volume information for trading instruments.
 */
@Entity
@Table(name = "tick_data", indexes = {
    @Index(name = "idx_symbol_timestamp", columnList = "symbol, timestamp"),
    @Index(name = "idx_timestamp", columnList = "timestamp"),
    @Index(name = "idx_symbol", columnList = "symbol")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TickData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Trading symbol (e.g., "RELIANCE-EQ", "NIFTY-50")
     */
    @Column(name = "symbol", nullable = false, length = 50)
    private String symbol;

    /**
     * Exchange where the instrument is traded
     */
    @Column(name = "exchange", nullable = false, length = 20)
    private String exchange;

    /**
     * Last traded price
     */
    @Column(name = "last_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lastPrice;

    /**
     * Best bid price
     */
    @Column(name = "bid_price", precision = 19, scale = 4)
    private BigDecimal bidPrice;

    /**
     * Best ask price
     */
    @Column(name = "ask_price", precision = 19, scale = 4)
    private BigDecimal askPrice;

    /**
     * Volume at best bid
     */
    @Column(name = "bid_volume")
    private Long bidVolume;

    /**
     * Volume at best ask
     */
    @Column(name = "ask_volume")
    private Long askVolume;

    /**
     * Total traded volume for the day
     */
    @Column(name = "volume")
    private Long volume;

    /**
     * Total traded value for the day
     */
    @Column(name = "value", precision = 19, scale = 2)
    private BigDecimal value;

    /**
     * Open price for the day
     */
    @Column(name = "open_price", precision = 19, scale = 4)
    private BigDecimal openPrice;

    /**
     * High price for the day
     */
    @Column(name = "high_price", precision = 19, scale = 4)
    private BigDecimal highPrice;

    /**
     * Low price for the day
     */
    @Column(name = "low_price", precision = 19, scale = 4)
    private BigDecimal lowPrice;

    /**
     * Previous day's closing price
     */
    @Column(name = "close_price", precision = 19, scale = 4)
    private BigDecimal closePrice;

    /**
     * Number of trades for the day
     */
    @Column(name = "trade_count")
    private Integer tradeCount;

    /**
     * Tick timestamp in UTC
     */
    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    /**
     * Tick sequence number for ordering
     */
    @Column(name = "sequence_number")
    private Long sequenceNumber;

    /**
     * Source of the tick data (e.g., "SMART_API", "NSE")
     */
    @Column(name = "source", length = 20)
    private String source;

    /**
     * Tick data quality indicator
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "quality", length = 10)
    private TickQuality quality;

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
     * Enum for tick data quality
     */
    public enum TickQuality {
        GOOD("Good quality tick"),
        DELAYED("Delayed tick"),
        OUT_OF_ORDER("Out of order tick"),
        DUPLICATE("Duplicate tick"),
        CORRUPTED("Corrupted tick data");

        private final String description;

        TickQuality(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Calculates the mid price from bid and ask prices
     */
    public BigDecimal getMidPrice() {
        if (bidPrice != null && askPrice != null) {
            return bidPrice.add(askPrice).divide(BigDecimal.valueOf(2), 4, BigDecimal.ROUND_HALF_UP);
        }
        return null;
    }

    /**
     * Calculates the spread from bid and ask prices
     */
    public BigDecimal getSpread() {
        if (bidPrice != null && askPrice != null) {
            return askPrice.subtract(bidPrice);
        }
        return null;
    }

    /**
     * Calculates the percentage change from previous close
     */
    public BigDecimal getPercentageChange() {
        if (lastPrice != null && closePrice != null && closePrice.compareTo(BigDecimal.ZERO) > 0) {
            return lastPrice.subtract(closePrice)
                    .divide(closePrice, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return null;
    }

    /**
     * Checks if this tick is valid for processing
     */
    public boolean isValid() {
        return symbol != null && !symbol.trim().isEmpty() &&
               lastPrice != null && lastPrice.compareTo(BigDecimal.ZERO) > 0 &&
               timestamp != null &&
               quality != TickQuality.CORRUPTED;
    }
}