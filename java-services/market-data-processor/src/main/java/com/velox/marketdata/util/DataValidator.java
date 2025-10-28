package com.velox.marketdata.util;

import com.velox.marketdata.model.TickData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for validating market data.
 * Performs comprehensive validation of tick data to ensure data quality.
 */
@Component
@Slf4j
public class DataValidator {

    // Valid exchanges
    private static final Set<String> VALID_EXCHANGES = Set.of(
            "NSE", "BSE", "MCX", "NCDEX", "CDS", "FO"
    );

    // Price validation thresholds
    private static final BigDecimal MIN_PRICE = BigDecimal.valueOf(0.01);
    private static final BigDecimal MAX_PRICE = BigDecimal.valueOf(1000000);
    private static final BigDecimal MAX_PRICE_CHANGE_PERCENT = BigDecimal.valueOf(20);

    // Volume validation thresholds
    private static final long MAX_VOLUME = 10_000_000_000L; // 10 billion shares
    private static final long MAX_TRADE_COUNT = 10_000_000L; // 10 million trades

    // Time validation
    private static final long MAX_FUTURE_SECONDS = 60; // Max 1 minute in future
    private static final long MAX_PAST_SECONDS = 86400; // Max 24 hours in past

    /**
     * Validate tick data comprehensively
     */
    public ValidationResult validateTick(TickData tick) {
        if (tick == null) {
            return ValidationResult.invalid("Tick data is null");
        }

        // Basic field validation
        ValidationResult basicValidation = validateBasicFields(tick);
        if (!basicValidation.isValid()) {
            return basicValidation;
        }

        // Price validation
        ValidationResult priceValidation = validatePrices(tick);
        if (!priceValidation.isValid()) {
            return priceValidation;
        }

        // Volume validation
        ValidationResult volumeValidation = validateVolumes(tick);
        if (!volumeValidation.isValid()) {
            return volumeValidation;
        }

        // Time validation
        ValidationResult timeValidation = validateTimestamp(tick);
        if (!timeValidation.isValid()) {
            return timeValidation;
        }

        // Cross-field validation
        ValidationResult crossFieldValidation = validateCrossFields(tick);
        if (!crossFieldValidation.isValid()) {
            return crossFieldValidation;
        }

        return ValidationResult.valid();
    }

    /**
     * Validate basic required fields
     */
    private ValidationResult validateBasicFields(TickData tick) {
        if (tick.getSymbol() == null || tick.getSymbol().trim().isEmpty()) {
            return ValidationResult.invalid("Symbol is null or empty");
        }

        if (tick.getExchange() == null || tick.getExchange().trim().isEmpty()) {
            return ValidationResult.invalid("Exchange is null or empty");
        }

        if (!VALID_EXCHANGES.contains(tick.getExchange().toUpperCase())) {
            return ValidationResult.invalid("Invalid exchange: " + tick.getExchange());
        }

        if (tick.getLastPrice() == null) {
            return ValidationResult.invalid("Last price is null");
        }

        if (tick.getTimestamp() == null) {
            return ValidationResult.invalid("Timestamp is null");
        }

        return ValidationResult.valid();
    }

    /**
     * Validate price fields
     */
    private ValidationResult validatePrices(TickData tick) {
        BigDecimal lastPrice = tick.getLastPrice();

        // Price range validation
        if (lastPrice.compareTo(MIN_PRICE) < 0) {
            return ValidationResult.invalid("Price too low: " + lastPrice);
        }

        if (lastPrice.compareTo(MAX_PRICE) > 0) {
            return ValidationResult.invalid("Price too high: " + lastPrice);
        }

        // Bid-Ask spread validation
        if (tick.getBidPrice() != null && tick.getAskPrice() != null) {
            if (tick.getBidPrice().compareTo(tick.getAskPrice()) > 0) {
                return ValidationResult.invalid("Bid price greater than ask price");
            }

            BigDecimal spread = tick.getAskPrice().subtract(tick.getBidPrice());
            BigDecimal spreadPercent = spread.divide(lastPrice, 4, BigDecimal.ROUND_HALF_UP)
                                       .multiply(BigDecimal.valueOf(100));

            if (spreadPercent.compareTo(BigDecimal.valueOf(50)) > 0) {
                return ValidationResult.invalid("Bid-ask spread too wide: " + spreadPercent + "%");
            }
        }

        // OHLC validation
        if (tick.getOpenPrice() != null && tick.getHighPrice() != null && 
            tick.getLowPrice() != null && tick.getClosePrice() != null) {
            
            if (tick.getHighPrice().compareTo(tick.getLowPrice()) < 0) {
                return ValidationResult.invalid("High price less than low price");
            }

            if (tick.getHighPrice().compareTo(tick.getOpenPrice()) < 0) {
                return ValidationResult.invalid("High price less than open price");
            }

            if (tick.getHighPrice().compareTo(tick.getClosePrice()) < 0) {
                return ValidationResult.invalid("High price less than close price");
            }

            if (tick.getLowPrice().compareTo(tick.getOpenPrice()) > 0) {
                return ValidationResult.invalid("Low price greater than open price");
            }

            if (tick.getLowPrice().compareTo(tick.getClosePrice()) > 0) {
                return ValidationResult.invalid("Low price greater than close price");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate volume fields
     */
    private ValidationResult validateVolumes(TickData tick) {
        if (tick.getVolume() != null && tick.getVolume() < 0) {
            return ValidationResult.invalid("Volume is negative: " + tick.getVolume());
        }

        if (tick.getVolume() != null && tick.getVolume() > MAX_VOLUME) {
            return ValidationResult.invalid("Volume too high: " + tick.getVolume());
        }

        if (tick.getBidVolume() != null && tick.getBidVolume() < 0) {
            return ValidationResult.invalid("Bid volume is negative: " + tick.getBidVolume());
        }

        if (tick.getAskVolume() != null && tick.getAskVolume() < 0) {
            return ValidationResult.invalid("Ask volume is negative: " + tick.getAskVolume());
        }

        if (tick.getTradeCount() != null && tick.getTradeCount() < 0) {
            return ValidationResult.invalid("Trade count is negative: " + tick.getTradeCount());
        }

        if (tick.getTradeCount() != null && tick.getTradeCount() > MAX_TRADE_COUNT) {
            return ValidationResult.invalid("Trade count too high: " + tick.getTradeCount());
        }

        return ValidationResult.valid();
    }

    /**
     * Validate timestamp
     */
    private ValidationResult validateTimestamp(TickData tick) {
        Instant now = Instant.now();
        Instant tickTime = tick.getTimestamp();

        // Check if timestamp is too far in future
        if (tickTime.isAfter(now.plusSeconds(MAX_FUTURE_SECONDS))) {
            return ValidationResult.invalid("Timestamp too far in future: " + tickTime);
        }

        // Check if timestamp is too far in past
        if (tickTime.isBefore(now.minusSeconds(MAX_PAST_SECONDS))) {
            return ValidationResult.invalid("Timestamp too far in past: " + tickTime);
        }

        return ValidationResult.valid();
    }

    /**
     * Validate cross-field relationships
     */
    private ValidationResult validateCrossFields(TickData tick) {
        // Value validation
        if (tick.getValue() != null && tick.getVolume() != null && 
            tick.getVolume() > 0 && tick.getLastPrice() != null) {
            
            BigDecimal expectedValue = tick.getLastPrice().multiply(BigDecimal.valueOf(tick.getVolume()));
            BigDecimal actualValue = tick.getValue();
            
            // Allow 10% tolerance due to price averaging
            BigDecimal tolerance = expectedValue.multiply(BigDecimal.valueOf(0.1));
            BigDecimal difference = expectedValue.subtract(actualValue).abs();
            
            if (difference.compareTo(tolerance) > 0) {
                return ValidationResult.invalid("Value inconsistent with price and volume");
            }
        }

        // Previous close validation
        if (tick.getClosePrice() != null && tick.getLastPrice() != null) {
            BigDecimal priceChange = tick.getLastPrice().subtract(tick.getClosePrice());
            BigDecimal percentChange = priceChange.divide(tick.getClosePrice(), 4, BigDecimal.ROUND_HALF_UP)
                                           .multiply(BigDecimal.valueOf(100));
            
            if (percentChange.abs().compareTo(MAX_PRICE_CHANGE_PERCENT) > 0) {
                return ValidationResult.invalid("Price change too extreme: " + percentChange + "%");
            }
        }

        return ValidationResult.valid();
    }

    /**
     * Validate symbol format
     */
    public boolean isValidSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return false;
        }

        // Basic symbol validation - can be enhanced based on exchange requirements
        String trimmedSymbol = symbol.trim().toUpperCase();
        
        // Check length
        if (trimmedSymbol.length() < 1 || trimmedSymbol.length() > 20) {
            return false;
        }

        // Check allowed characters (letters, numbers, hyphen, underscore)
        return trimmedSymbol.matches("^[A-Z0-9\\-_]+$");
    }

    /**
     * Validate exchange code
     */
    public boolean isValidExchange(String exchange) {
        return exchange != null && VALID_EXCHANGES.contains(exchange.toUpperCase());
    }

    /**
     * Result of validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }
    }
}