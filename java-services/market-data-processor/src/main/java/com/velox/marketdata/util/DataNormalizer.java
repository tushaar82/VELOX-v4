package com.velox.marketdata.util;

import com.velox.marketdata.model.TickData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for normalizing market data.
 * Ensures consistent data format, precision, and timezone handling.
 */
@Component
@Slf4j
public class DataNormalizer {

    // Symbol mapping for standardization
    private static final Map<String, String> SYMBOL_MAPPING = new HashMap<>();
    
    // Exchange mapping for standardization
    private static final Map<String, String> EXCHANGE_MAPPING = new HashMap<>();
    
    // Price precision for different instruments
    private static final Map<String, Integer> PRICE_PRECISION = new HashMap<>();
    
    // Volume precision for different instruments
    private static final Map<String, Integer> VOLUME_PRECISION = new HashMap<>();

    static {
        // Initialize symbol mappings
        SYMBOL_MAPPING.put("RELIANCE", "RELIANCE-EQ");
        SYMBOL_MAPPING.put("TCS", "TCS-EQ");
        SYMBOL_MAPPING.put("INFY", "INFY-EQ");
        SYMBOL_MAPPING.put("NIFTY", "NIFTY-50");
        SYMBOL_MAPPING.put("BANKNIFTY", "BANKNIFTY-50");
        
        // Initialize exchange mappings
        EXCHANGE_MAPPING.put("NSE", "NSE");
        EXCHANGE_MAPPING.put("BSE", "BSE");
        EXCHANGE_MAPPING.put("MCX", "MCX");
        EXCHANGE_MAPPING.put("NFO", "NSE"); // Normalized to NSE
        EXCHANGE_MAPPING.put("CDS", "NSE"); // Normalized to NSE
        
        // Initialize price precision mappings
        PRICE_PRECISION.put("EQUITY", 2);
        PRICE_PRECISION.put("INDEX", 2);
        PRICE_PRECISION.put("CURRENCY", 4);
        PRICE_PRECISION.put("COMMODITY", 2);
        PRICE_PRECISION.put("FUTURE", 2);
        PRICE_PRECISION.put("OPTION", 2);
        
        // Initialize volume precision mappings
        VOLUME_PRECISION.put("EQUITY", 0);
        VOLUME_PRECISION.put("INDEX", 0);
        VOLUME_PRECISION.put("CURRENCY", 0);
        VOLUME_PRECISION.put("COMMODITY", 0);
        VOLUME_PRECISION.put("FUTURE", 0);
        VOLUME_PRECISION.put("OPTION", 0);
    }

    /**
     * Normalize tick data to standard format
     */
    public TickData normalizeTick(TickData tick) {
        if (tick == null) {
            return null;
        }

        try {
            TickData normalized = TickData.builder()
                    .id(tick.getId())
                    .symbol(normalizeSymbol(tick.getSymbol()))
                    .exchange(normalizeExchange(tick.getExchange()))
                    .lastPrice(normalizePrice(tick.getLastPrice(), determineInstrumentType(tick.getSymbol())))
                    .bidPrice(normalizePrice(tick.getBidPrice(), determineInstrumentType(tick.getSymbol())))
                    .askPrice(normalizePrice(tick.getAskPrice(), determineInstrumentType(tick.getSymbol())))
                    .bidVolume(normalizeVolume(tick.getBidVolume(), determineInstrumentType(tick.getSymbol())))
                    .askVolume(normalizeVolume(tick.getAskVolume(), determineInstrumentType(tick.getSymbol())))
                    .volume(normalizeVolume(tick.getVolume(), determineInstrumentType(tick.getSymbol())))
                    .value(normalizeValue(tick.getValue()))
                    .openPrice(normalizePrice(tick.getOpenPrice(), determineInstrumentType(tick.getSymbol())))
                    .highPrice(normalizePrice(tick.getHighPrice(), determineInstrumentType(tick.getSymbol())))
                    .lowPrice(normalizePrice(tick.getLowPrice(), determineInstrumentType(tick.getSymbol())))
                    .closePrice(normalizePrice(tick.getClosePrice(), determineInstrumentType(tick.getSymbol())))
                    .tradeCount(tick.getTradeCount())
                    .timestamp(normalizeTimestamp(tick.getTimestamp()))
                    .sequenceNumber(tick.getSequenceNumber())
                    .source(normalizeSource(tick.getSource()))
                    .quality(tick.getQuality())
                    .createdAt(tick.getCreatedAt())
                    .updatedAt(tick.getUpdatedAt())
                    .build();

            // Apply cross-field normalization
            return applyCrossFieldNormalization(normalized);

        } catch (Exception e) {
            log.error("Error normalizing tick data: {}", tick, e);
            return tick; // Return original if normalization fails
        }
    }

    /**
     * Normalize symbol to standard format
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return symbol;
        }

        String normalized = symbol.trim().toUpperCase();
        
        // Apply symbol mapping if available
        return SYMBOL_MAPPING.getOrDefault(normalized, normalized);
    }

    /**
     * Normalize exchange to standard format
     */
    private String normalizeExchange(String exchange) {
        if (exchange == null || exchange.trim().isEmpty()) {
            return exchange;
        }

        String normalized = exchange.trim().toUpperCase();
        
        // Apply exchange mapping if available
        return EXCHANGE_MAPPING.getOrDefault(normalized, normalized);
    }

    /**
     * Normalize price with appropriate precision
     */
    private BigDecimal normalizePrice(BigDecimal price, String instrumentType) {
        if (price == null) {
            return null;
        }

        int precision = PRICE_PRECISION.getOrDefault(instrumentType, 2);
        return price.setScale(precision, RoundingMode.HALF_UP);
    }

    /**
     * Normalize volume with appropriate precision
     */
    private Long normalizeVolume(Long volume, String instrumentType) {
        if (volume == null) {
            return null;
        }

        // Volume is typically an integer, but we ensure it's not negative
        return Math.max(0L, volume);
    }

    /**
     * Normalize value with appropriate precision
     */
    private BigDecimal normalizeValue(BigDecimal value) {
        if (value == null) {
            return null;
        }

        // Value typically has 2 decimal places
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Normalize timestamp to UTC
     */
    private Instant normalizeTimestamp(Instant timestamp) {
        if (timestamp == null) {
            return null;
        }

        // Ensure timestamp is in UTC (it should already be, but double-check)
        return timestamp;
    }

    /**
     * Normalize source
     */
    private String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            return "UNKNOWN";
        }

        return source.trim().toUpperCase();
    }

    /**
     * Apply cross-field normalization
     */
    private TickData applyCrossFieldNormalization(TickData tick) {
        // Ensure bid-ask spread is reasonable
        if (tick.getBidPrice() != null && tick.getAskPrice() != null) {
            if (tick.getBidPrice().compareTo(tick.getAskPrice()) > 0) {
                // Swap bid and ask if they're reversed
                BigDecimal temp = tick.getBidPrice();
                tick.setBidPrice(tick.getAskPrice());
                tick.setAskPrice(temp);
            }
        }

        // Ensure OHLC relationships are correct
        if (tick.getOpenPrice() != null && tick.getHighPrice() != null && 
            tick.getLowPrice() != null && tick.getClosePrice() != null) {
            
            BigDecimal high = tick.getHighPrice();
            BigDecimal low = tick.getLowPrice();
            BigDecimal open = tick.getOpenPrice();
            BigDecimal close = tick.getClosePrice();
            
            // Adjust high if needed
            BigDecimal maxPrice = open.max(close).max(high).max(low);
            if (high.compareTo(maxPrice) < 0) {
                tick.setHighPrice(maxPrice);
            }
            
            // Adjust low if needed
            BigDecimal minPrice = open.min(close).min(high).min(low);
            if (low.compareTo(minPrice) > 0) {
                tick.setLowPrice(minPrice);
            }
        }

        // Calculate derived fields if missing
        if (tick.getValue() == null && tick.getLastPrice() != null && tick.getVolume() != null) {
            BigDecimal calculatedValue = tick.getLastPrice().multiply(BigDecimal.valueOf(tick.getVolume()));
            tick.setValue(calculatedValue.setScale(2, RoundingMode.HALF_UP));
        }

        return tick;
    }

    /**
     * Determine instrument type from symbol
     */
    private String determineInstrumentType(String symbol) {
        if (symbol == null) {
            return "EQUITY";
        }

        String upperSymbol = symbol.toUpperCase();
        
        // Index detection
        if (upperSymbol.contains("NIFTY") || upperSymbol.contains("SENSEX") || 
            upperSymbol.contains("BANKNIFTY")) {
            return "INDEX";
        }
        
        // Currency detection
        if (upperSymbol.contains("USD") || upperSymbol.contains("EUR") || 
            upperSymbol.contains("GBP") || upperSymbol.contains("JPY")) {
            return "CURRENCY";
        }
        
        // Commodity detection
        if (upperSymbol.contains("GOLD") || upperSymbol.contains("SILVER") || 
            upperSymbol.contains("CRUDE") || upperSymbol.contains("NATURAL")) {
            return "COMMODITY";
        }
        
        // Future detection
        if (upperSymbol.contains("FUT") || upperSymbol.endsWith("F")) {
            return "FUTURE";
        }
        
        // Option detection
        if (upperSymbol.contains("CE") || upperSymbol.contains("PE") || 
            upperSymbol.contains("CALL") || upperSymbol.contains("PUT")) {
            return "OPTION";
        }
        
        // Default to equity
        return "EQUITY";
    }

    /**
     * Normalize timezone for timestamp
     */
    public Instant normalizeTimezone(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }

        // Convert to UTC
        return zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toInstant();
    }

    /**
     * Normalize trading session time
     */
    public Instant normalizeTradingSessionTime(Instant timestamp, String exchange) {
        if (timestamp == null || exchange == null) {
            return timestamp;
        }

        // Apply exchange-specific trading session adjustments
        // This can be enhanced based on specific exchange requirements
        return timestamp;
    }

    /**
     * Clean and normalize symbol for database storage
     */
    public String cleanSymbolForStorage(String symbol) {
        if (symbol == null) {
            return null;
        }

        // Remove special characters, convert to uppercase, trim
        return symbol.replaceAll("[^A-Z0-9\\-_]", "").toUpperCase().trim();
    }

    /**
     * Get precision for instrument type
     */
    public int getPricePrecision(String instrumentType) {
        return PRICE_PRECISION.getOrDefault(instrumentType, 2);
    }

    /**
     * Get volume precision for instrument type
     */
    public int getVolumePrecision(String instrumentType) {
        return VOLUME_PRECISION.getOrDefault(instrumentType, 0);
    }

    /**
     * Round price to appropriate precision
     */
    public BigDecimal roundPrice(BigDecimal price, String instrumentType) {
        if (price == null) {
            return null;
        }

        int precision = getPricePrecision(instrumentType);
        return price.setScale(precision, RoundingMode.HALF_UP);
    }

    /**
     * Round volume to appropriate precision
     */
    public Long roundVolume(Long volume, String instrumentType) {
        if (volume == null) {
            return null;
        }

        // Volume is typically integer, but we ensure it's properly rounded
        return Math.round(volume.doubleValue());
    }
}