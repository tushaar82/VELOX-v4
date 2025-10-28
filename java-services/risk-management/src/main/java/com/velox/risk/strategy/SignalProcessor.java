package com.velox.risk.strategy;

import com.velox.indicators.model.IndicatorValue;
import com.velox.risk.strategy.Strategy.Signal;
import com.velox.risk.strategy.Strategy.SignalAction;
import com.velox.risk.strategy.Strategy.SignalType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Signal processing and order generation service.
 * Processes trading signals from strategies and generates orders.
 */
@Service
@Slf4j
public class SignalProcessor {

    private final KafkaTemplate<String, Object> orderKafkaTemplate;
    private final Map<String, SignalFilter> signalFilters;
    private final Map<String, Signal> lastSignals;
    private final Map<String, Instant> lastSignalTimes;

    @Value("${signal.processing.max.concurrent.orders:10}")
    private int maxConcurrentOrders;

    @Value("${signal.processing.signal.timeout.ms:5000}")
    private long signalTimeoutMs;

    // Performance metrics
    private final AtomicLong signalsProcessed = new AtomicLong(0);
    private final AtomicLong ordersGenerated = new AtomicLong(0);
    private final AtomicLong ordersRejected = new AtomicLong(0);
    private final AtomicLong signalsFiltered = new AtomicLong(0);

    @Autowired
    public SignalProcessor(KafkaTemplate<String, Object> orderKafkaTemplate) {
        this.orderKafkaTemplate = orderKafkaTemplate;
        this.signalFilters = new ConcurrentHashMap<>();
        this.lastSignals = new ConcurrentHashMap<>();
        this.lastSignalTimes = new ConcurrentHashMap<>();
        
        initializeDefaultFilters();
    }

    /**
     * Process signal from strategy
     */
    @Async
    public CompletableFuture<Void> processSignal(Signal signal) {
        return CompletableFuture.runAsync(() -> {
            try {
                String strategyId = signal.getStrategyId();
                String symbol = signal.getSymbol();
                
                log.debug("Processing signal from strategy {} for symbol {}", strategyId, symbol);
                
                // Check signal timeout
                if (isSignalTimedOut(strategyId, symbol)) {
                    log.warn("Signal timed out for strategy {} on symbol {}", strategyId, symbol);
                    return;
                }
                
                // Apply signal filters
                if (!applySignalFilters(signal)) {
                    signalsFiltered.incrementAndGet();
                    log.debug("Signal filtered for strategy {} on symbol {}", strategyId, symbol);
                    return;
                }
                
                // Generate order from signal
                OrderRequest orderRequest = generateOrderFromSignal(signal);
                if (orderRequest == null) {
                    ordersRejected.incrementAndGet();
                    log.warn("Failed to generate order from signal for strategy {} on symbol {}", strategyId, symbol);
                    return;
                }
                
                // Publish order to Kafka
                publishOrder(orderRequest);
                
                // Update tracking
                lastSignals.put(strategyId + ":" + symbol, signal);
                lastSignalTimes.put(strategyId + ":" + symbol, Instant.now());
                signalsProcessed.incrementAndGet();
                ordersGenerated.incrementAndGet();
                
                log.info("Generated order from signal for strategy {} on symbol {}: {}", strategyId, symbol, orderRequest);
                
            } catch (Exception e) {
                log.error("Error processing signal from strategy {} on symbol {}", signal.getStrategyId(), signal.getSymbol(), e);
                ordersRejected.incrementAndGet();
            }
        });
    }

    /**
     * Process multiple signals from strategy
     */
    @Async
    public CompletableFuture<Void> processSignals(List<Signal> signals) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (signals == null || signals.isEmpty()) {
                    return;
                }
                
                // Group signals by symbol
                Map<String, List<Signal>> signalsBySymbol = new HashMap<>();
                for (Signal signal : signals) {
                    signalsBySymbol.computeIfAbsent(signal.getSymbol(), k -> new ArrayList<>()).add(signal);
                }
                
                // Process signals for each symbol
                for (Map.Entry<String, List<Signal>> entry : signalsBySymbol.entrySet()) {
                    String symbol = entry.getKey();
                    List<Signal> symbolSignals = entry.getValue();
                    
                    // Sort signals by timestamp
                    symbolSignals.sort(Comparator.comparing(Signal::getTimestamp).reversed());
                    
                    // Process only the latest signal for each symbol
                    if (!symbolSignals.isEmpty()) {
                        processSignal(symbolSignals.get(0));
                    }
                }
                
            } catch (Exception e) {
                log.error("Error processing multiple signals", e);
            }
        });
    }

    /**
     * Process indicator-based signals
     */
    @Async
    public CompletableFuture<Void> processIndicatorSignals(String strategyId, String symbol, IndicatorValue indicators) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing indicator signals for strategy {} on symbol {}", strategyId, symbol);
                
                // Generate signals based on indicators
                List<Signal> generatedSignals = generateSignalsFromIndicators(strategyId, symbol, indicators);
                
                // Process generated signals
                for (Signal signal : generatedSignals) {
                    processSignal(signal);
                }
                
            } catch (Exception e) {
                log.error("Error processing indicator signals for strategy {} on symbol {}", strategyId, symbol, e);
            }
        });
    }

    /**
     * Add signal filter
     */
    public void addSignalFilter(String name, SignalFilter filter) {
        signalFilters.put(name, filter);
        log.info("Added signal filter: {}", name);
    }

    /**
     * Remove signal filter
     */
    public void removeSignalFilter(String name) {
        signalFilters.remove(name);
        log.info("Removed signal filter: {}", name);
    }

    /**
     * Get signal processing statistics
     */
    public SignalProcessingStats getStatistics() {
        return SignalProcessingStats.builder()
                .signalsProcessed(signalsProcessed.get())
                .ordersGenerated(ordersGenerated.get())
                .ordersRejected(ordersRejected.get())
                .signalsFiltered(signalsFiltered.get())
                .activeFilters(signalFilters.size())
                .build();
    }

    // Private helper methods
    private boolean isSignalTimedOut(String strategyId, String symbol) {
        Instant lastSignalTime = lastSignalTimes.get(strategyId + ":" + symbol);
        if (lastSignalTime == null) {
            return false;
        }
        
        long timeSinceLastSignal = Instant.now().toEpochMilli() - lastSignalTime.toEpochMilli();
        return timeSinceLastSignal < signalTimeoutMs;
    }

    private boolean applySignalFilters(Signal signal) {
        for (SignalFilter filter : signalFilters.values()) {
            if (!filter.accept(signal)) {
                log.debug("Signal rejected by filter {}: {}", filter.getName(), signal);
                return false;
            }
        }
        return true;
    }

    private OrderRequest generateOrderFromSignal(Signal signal) {
        try {
            return OrderRequest.builder()
                    .strategyId(signal.getStrategyId())
                    .symbol(signal.getSymbol())
                    .action(signal.getAction())
                    .orderType(signal.getOrderType())
                    .quantity(signal.getQuantity())
                    .price(signal.getPrice())
                    .stopLoss(signal.getStopLoss())
                    .takeProfit(signal.getTakeProfit())
                    .metadata(signal.getMetadata())
                    .timestamp(Instant.now())
                    .build();
        } catch (Exception e) {
            log.error("Error generating order from signal", e);
            return null;
        }
    }

    private void publishOrder(OrderRequest orderRequest) {
        try {
            orderKafkaTemplate.send("order-requests", orderRequest.getSymbol(), orderRequest);
        } catch (Exception e) {
            log.error("Error publishing order request", e);
        }
    }

    private List<Signal> generateSignalsFromIndicators(String strategyId, String symbol, IndicatorValue indicators) {
        List<Signal> signals = new ArrayList<>();
        
        // RSI-based signals
        if (indicators.getRsi14() != null) {
            BigDecimal rsi = indicators.getRsi14();
            
            // RSI oversold signal
            if (rsi.compareTo(BigDecimal.valueOf(30)) < 0) {
                signals.add(Signal.builder()
                        .strategyId(strategyId)
                        .symbol(symbol)
                        .action(SignalAction.BUY)
                        .orderType(SignalType.MARKET)
                        .quantity(100)
                        .price(null)
                        .stopLoss(calculateStopLoss(indicators, SignalAction.BUY))
                        .takeProfit(calculateTakeProfit(indicators, SignalAction.BUY))
                        .metadata(Map.of("indicator", "RSI", "value", rsi, "condition", "OVERSOLD"))
                        .timestamp(Instant.now())
                        .build());
            }
            
            // RSI overbought signal
            if (rsi.compareTo(BigDecimal.valueOf(70)) > 0) {
                signals.add(Signal.builder()
                        .strategyId(strategyId)
                        .symbol(symbol)
                        .action(SignalAction.SELL)
                        .orderType(SignalType.MARKET)
                        .quantity(100)
                        .price(null)
                        .stopLoss(calculateStopLoss(indicators, SignalAction.SELL))
                        .takeProfit(calculateTakeProfit(indicators, SignalAction.SELL))
                        .metadata(Map.of("indicator", "RSI", "value", rsi, "condition", "OVERBOUGHT"))
                        .timestamp(Instant.now())
                        .build());
            }
        }
        
        // MACD-based signals
        if (indicators.getMacdLine() != null && indicators.getMacdSignal() != null) {
            BigDecimal macdLine = indicators.getMacdLine();
            BigDecimal macdSignal = indicators.getMacdSignal();
            
            // MACD bullish crossover
            if (macdLine.compareTo(macdSignal) > 0 && 
                lastSignals.containsKey(strategyId + ":" + symbol)) {
                Signal lastSignal = lastSignals.get(strategyId + ":" + symbol);
                if (lastSignal != null && lastSignal.getMetadata().containsKey("indicator") && 
                    "MACD".equals(lastSignal.getMetadata().get("indicator")) &&
                    "BEARISH".equals(lastSignal.getMetadata().get("condition"))) {
                    
                    signals.add(Signal.builder()
                            .strategyId(strategyId)
                            .symbol(symbol)
                            .action(SignalAction.BUY)
                            .orderType(SignalType.MARKET)
                            .quantity(100)
                            .price(null)
                            .stopLoss(calculateStopLoss(indicators, SignalAction.BUY))
                            .takeProfit(calculateTakeProfit(indicators, SignalAction.BUY))
                            .metadata(Map.of("indicator", "MACD", "condition", "BULLISH_CROSSOVER"))
                            .timestamp(Instant.now())
                            .build());
                }
            }
            
            // MACD bearish crossover
            if (macdLine.compareTo(macdSignal) < 0 && 
                lastSignals.containsKey(strategyId + ":" + symbol)) {
                Signal lastSignal = lastSignals.get(strategyId + ":" + symbol);
                if (lastSignal != null && lastSignal.getMetadata().containsKey("indicator") && 
                    "MACD".equals(lastSignal.getMetadata().get("indicator")) &&
                    "BULLISH".equals(lastSignal.getMetadata().get("condition"))) {
                    
                    signals.add(Signal.builder()
                            .strategyId(strategyId)
                            .symbol(symbol)
                            .action(SignalAction.SELL)
                            .orderType(SignalType.MARKET)
                            .quantity(100)
                            .price(null)
                            .stopLoss(calculateStopLoss(indicators, SignalAction.SELL))
                            .takeProfit(calculateTakeProfit(indicators, SignalAction.SELL))
                            .metadata(Map.of("indicator", "MACD", "condition", "BEARISH_CROSSOVER"))
                            .timestamp(Instant.now())
                            .build());
                }
            }
        }
        
        // Moving average crossover signals
        if (indicators.getEma20() != null && indicators.getSma20() != null) {
            BigDecimal ema20 = indicators.getEma20();
            BigDecimal sma20 = indicators.getSma20();
            
            // EMA above SMA (bullish)
            if (ema20.compareTo(sma20) > 0 && 
                lastSignals.containsKey(strategyId + ":" + symbol)) {
                Signal lastSignal = lastSignals.get(strategyId + ":" + symbol);
                if (lastSignal != null && lastSignal.getMetadata().containsKey("indicator") && 
                    "MA".equals(lastSignal.getMetadata().get("indicator")) &&
                    "BEARISH".equals(lastSignal.getMetadata().get("condition"))) {
                    
                    signals.add(Signal.builder()
                            .strategyId(strategyId)
                            .symbol(symbol)
                            .action(SignalAction.BUY)
                            .orderType(SignalType.MARKET)
                            .quantity(100)
                            .price(null)
                            .stopLoss(calculateStopLoss(indicators, SignalAction.BUY))
                            .takeProfit(calculateTakeProfit(indicators, SignalAction.BUY))
                            .metadata(Map.of("indicator", "MA", "condition", "EMA_ABOVE_SMA"))
                            .timestamp(Instant.now())
                            .build());
                }
            }
            
            // EMA below SMA (bearish)
            if (ema20.compareTo(sma20) < 0 && 
                lastSignals.containsKey(strategyId + ":" + symbol)) {
                Signal lastSignal = lastSignals.get(strategyId + ":" + symbol);
                if (lastSignal != null && lastSignal.getMetadata().containsKey("indicator") && 
                    "MA".equals(lastSignal.getMetadata().get("indicator")) &&
                    "BULLISH".equals(lastSignal.getMetadata().get("condition"))) {
                    
                    signals.add(Signal.builder()
                            .strategyId(strategyId)
                            .symbol(symbol)
                            .action(SignalAction.SELL)
                            .orderType(SignalType.MARKET)
                            .quantity(100)
                            .price(null)
                            .stopLoss(calculateStopLoss(indicators, SignalAction.SELL))
                            .takeProfit(calculateTakeProfit(indicators, SignalAction.SELL))
                            .metadata(Map.of("indicator", "MA", "condition", "EMA_BELOW_SMA"))
                            .timestamp(Instant.now())
                            .build());
                }
            }
        }
        
        return signals;
    }

    private BigDecimal calculateStopLoss(IndicatorValue indicators, SignalAction action) {
        // Simple stop loss calculation based on ATR
        if (indicators.getAtr() != null) {
            BigDecimal atr = indicators.getAtr();
            BigDecimal multiplier = action == SignalAction.BUY ? BigDecimal.valueOf(2.0) : BigDecimal.valueOf(2.0);
            return atr.multiply(multiplier);
        }
        return null;
    }

    private BigDecimal calculateTakeProfit(IndicatorValue indicators, SignalAction action) {
        // Simple take profit calculation based on ATR
        if (indicators.getAtr() != null) {
            BigDecimal atr = indicators.getAtr();
            BigDecimal multiplier = action == SignalAction.BUY ? BigDecimal.valueOf(3.0) : BigDecimal.valueOf(3.0);
            return atr.multiply(multiplier);
        }
        return null;
    }

    private void initializeDefaultFilters() {
        // Add default signal filters
        signalFilters.put("marketHours", new MarketHoursFilter());
        signalFilters.put("volatility", new VolatilityFilter());
        signalFilters.put("frequency", new FrequencyFilter());
    }

    /**
     * Signal processing statistics class
     */
    @lombok.Builder
    @lombok.Data
    public static class SignalProcessingStats {
        private long signalsProcessed;
        private long ordersGenerated;
        private long ordersRejected;
        private long signalsFiltered;
        private int activeFilters;
    }

    /**
     * Order request class
     */
    @lombok.Builder
    @lombok.Data
    public static class OrderRequest {
        private String strategyId;
        private String symbol;
        private SignalAction action;
        private SignalType orderType;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        private Map<String, Object> metadata;
        private Instant timestamp;
    }

    /**
     * Signal type enum
     */
    public enum SignalType {
        MARKET("MARKET"),
        LIMIT("LIMIT"),
        STOP("STOP"),
        STOP_LIMIT("STOP_LIMIT");

        private final String description;

        SignalType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Signal filter interface
     */
    public interface SignalFilter {
        String getName();
        boolean accept(Signal signal);
    }

    /**
     * Market hours filter
     */
    public static class MarketHoursFilter implements SignalFilter {
        @Override
        public String getName() {
            return "Market Hours";
        }

        @Override
        public boolean accept(Signal signal) {
            // Simple implementation - allow all signals during market hours
            // In a real implementation, this would check if market is open
            return true;
        }
    }

    /**
     * Volatility filter
     */
    public static class VolatilityFilter implements SignalFilter {
        @Override
        public String getName() {
            return "Volatility";
        }

        @Override
        public boolean accept(Signal signal) {
            // Simple implementation - allow all signals regardless of volatility
            // In a real implementation, this might filter signals during high volatility
            return true;
        }
    }

    /**
     * Frequency filter
     */
    public static class FrequencyFilter implements SignalFilter {
        private final Map<String, Instant> lastSignalTimes = new HashMap<>();
        private final long minIntervalMs = 60000; // 1 minute

        @Override
        public String getName() {
            return "Frequency";
        }

        @Override
        public boolean accept(Signal signal) {
            String key = signal.getStrategyId() + ":" + signal.getSymbol();
            Instant lastSignalTime = lastSignalTimes.get(key);
            
            if (lastSignalTime == null) {
                lastSignalTimes.put(key, signal.getTimestamp());
                return true;
            }
            
            long timeSinceLastSignal = signal.getTimestamp().toEpochMilli() - lastSignalTime.toEpochMilli();
            
            if (timeSinceLastSignal < minIntervalMs) {
                return false; // Reject signal if too frequent
            }
            
            lastSignalTimes.put(key, signal.getTimestamp());
            return true;
        }
    }
}