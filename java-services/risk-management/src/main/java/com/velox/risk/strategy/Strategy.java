package com.velox.risk.strategy;

import com.velox.indicators.model.IndicatorValue;
import com.velox.marketdata.model.CandleData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract base class for trading strategies.
 * Provides common functionality and lifecycle management for all trading strategies.
 */
public abstract class Strategy {

    protected final String strategyId;
    protected final String name;
    protected final String description;
    protected final StrategyType type;
    protected final Map<String, Object> parameters;
    protected final KafkaTemplate<String, Object> kafkaTemplate;

    protected StrategyState state;
    protected Instant lastUpdateTime;
    protected Instant lastExecutionTime;
    protected int executionCount;
    protected int successCount;
    protected int failureCount;
    protected BigDecimal totalPnL;
    protected BigDecimal maxDrawdown;
    protected BigDecimal peakEquity;

    /**
     * Constructor for strategy
     */
    protected Strategy(String strategyId, String name, String description, StrategyType type,
                    Map<String, Object> parameters, KafkaTemplate<String, Object> kafkaTemplate) {
        this.strategyId = strategyId;
        this.name = name;
        this.description = description;
        this.type = type;
        this.parameters = parameters;
        this.kafkaTemplate = kafkaTemplate;
        this.state = StrategyState.INITIALIZED;
        this.lastUpdateTime = Instant.now();
        this.executionCount = 0;
        this.successCount = 0;
        this.failureCount = 0;
        this.totalPnL = BigDecimal.ZERO;
        this.maxDrawdown = BigDecimal.ZERO;
        this.peakEquity = BigDecimal.ZERO;
    }

    /**
     * Initialize the strategy
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Initializing strategy: {} ({})", name, strategyId);
                
                // Perform strategy-specific initialization
                boolean success = doInitialize();
                
                if (success) {
                    state = StrategyState.ACTIVE;
                    log.info("Strategy {} initialized successfully", name);
                } else {
                    state = StrategyState.ERROR;
                    log.error("Strategy {} initialization failed", name);
                }
                
                lastUpdateTime = Instant.now();
                publishStrategyState();
                
            } catch (Exception e) {
                state = StrategyState.ERROR;
                log.error("Error initializing strategy {}", name, e);
                lastUpdateTime = Instant.now();
                publishStrategyState();
            }
        });
    }

    /**
     * Start the strategy
     */
    public CompletableFuture<Void> start() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (state != StrategyState.INITIALIZED && state != StrategyState.STOPPED) {
                    log.warn("Cannot start strategy {}: invalid state {}", name, state);
                    return;
                }
                
                log.info("Starting strategy: {} ({})", name, strategyId);
                
                // Perform strategy-specific start
                boolean success = doStart();
                
                if (success) {
                    state = StrategyState.ACTIVE;
                    log.info("Strategy {} started successfully", name);
                } else {
                    state = StrategyState.ERROR;
                    log.error("Strategy {} start failed", name);
                }
                
                lastUpdateTime = Instant.now();
                publishStrategyState();
                
            } catch (Exception e) {
                state = StrategyState.ERROR;
                log.error("Error starting strategy {}", name, e);
                lastUpdateTime = Instant.now();
                publishStrategyState();
            }
        });
    }

    /**
     * Stop the strategy
     */
    public CompletableFuture<Void> stop() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (state != StrategyState.ACTIVE) {
                    log.warn("Cannot stop strategy {}: not active", name);
                    return;
                }
                
                log.info("Stopping strategy: {} ({})", name, strategyId);
                
                // Perform strategy-specific stop
                boolean success = doStop();
                
                if (success) {
                    state = StrategyState.STOPPED;
                    log.info("Strategy {} stopped successfully", name);
                } else {
                    state = StrategyState.ERROR;
                    log.error("Strategy {} stop failed", name);
                }
                
                lastUpdateTime = Instant.now();
                publishStrategyState();
                
            } catch (Exception e) {
                state = StrategyState.ERROR;
                log.error("Error stopping strategy {}", name, e);
                lastUpdateTime = Instant.now();
                publishStrategyState();
            }
        });
    }

    /**
     * Process new candle data
     */
    public CompletableFuture<Void> processCandle(CandleData candle) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (state != StrategyState.ACTIVE) {
                    return;
                }
                
                // Perform strategy-specific candle processing
                List<Signal> signals = doProcessCandle(candle);
                
                // Handle generated signals
                if (signals != null && !signals.isEmpty()) {
                    for (Signal signal : signals) {
                        handleSignal(signal);
                    }
                }
                
                lastUpdateTime = Instant.now();
                
            } catch (Exception e) {
                log.error("Error processing candle in strategy {}", name, e);
                lastUpdateTime = Instant.now();
            }
        });
    }

    /**
     * Process new indicator data
     */
    public CompletableFuture<Void> processIndicators(IndicatorValue indicators) {
        return CompletableFuture.runAsync(() -> {
            try {
                if (state != StrategyState.ACTIVE) {
                    return;
                }
                
                // Perform strategy-specific indicator processing
                List<Signal> signals = doProcessIndicators(indicators);
                
                // Handle generated signals
                if (signals != null && !signals.isEmpty()) {
                    for (Signal signal : signals) {
                        handleSignal(signal);
                    }
                }
                
                lastUpdateTime = Instant.now();
                
            } catch (Exception e) {
                log.error("Error processing indicators in strategy {}", name, e);
                lastUpdateTime = Instant.now();
            }
        });
    }

    /**
     * Handle a trading signal
     */
    protected void handleSignal(Signal signal) {
        try {
            // Validate signal
            if (!validateSignal(signal)) {
                log.warn("Invalid signal rejected by strategy {}: {}", name, signal);
                return;
            }
            
            // Publish signal to Kafka for order execution
            publishSignal(signal);
            
            // Update statistics
            executionCount++;
            
            log.info("Signal generated by strategy {}: {}", name, signal);
            
        } catch (Exception e) {
            log.error("Error handling signal in strategy {}", name, e);
            failureCount++;
        }
    }

    /**
     * Update strategy performance metrics
     */
    public void updatePerformanceMetrics(BigDecimal pnl, BigDecimal equity) {
        totalPnL = totalPnL.add(pnl);
        
        // Update peak equity
        if (equity.compareTo(peakEquity) > 0) {
            peakEquity = equity;
        }
        
        // Calculate current drawdown
        BigDecimal currentDrawdown = peakEquity.subtract(equity);
        if (currentDrawdown.compareTo(maxDrawdown) > 0) {
            maxDrawdown = currentDrawdown;
        }
        
        lastUpdateTime = Instant.now();
        
        log.debug("Updated performance metrics for strategy {}: P&L={}, Equity={}, Drawdown={}", 
                  name, totalPnL, equity, maxDrawdown);
    }

    /**
     * Get strategy information
     */
    public StrategyInfo getInfo() {
        return StrategyInfo.builder()
                .strategyId(strategyId)
                .name(name)
                .description(description)
                .type(type)
                .state(state)
                .parameters(parameters)
                .lastUpdateTime(lastUpdateTime)
                .lastExecutionTime(lastExecutionTime)
                .executionCount(executionCount)
                .successCount(successCount)
                .failureCount(failureCount)
                .totalPnL(totalPnL)
                .maxDrawdown(maxDrawdown)
                .peakEquity(peakEquity)
                .build();
    }

    /**
     * Get strategy state
     */
    public StrategyState getState() {
        return state;
    }

    /**
     * Get strategy parameters
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Update strategy parameter
     */
    public void updateParameter(String key, Object value) {
        parameters.put(key, value);
        lastUpdateTime = Instant.now();
        log.info("Updated parameter {} for strategy {}: {}", key, name, value);
    }

    /**
     * Validate strategy configuration
     */
    public ValidationResult validateConfiguration() {
        try {
            // Perform basic validation
            if (strategyId == null || strategyId.trim().isEmpty()) {
                return ValidationResult.invalid("Strategy ID is required");
            }
            
            if (name == null || name.trim().isEmpty()) {
                return ValidationResult.invalid("Strategy name is required");
            }
            
            // Perform strategy-specific validation
            return doValidateConfiguration();
            
        } catch (Exception e) {
            return ValidationResult.invalid("Configuration validation error: " + e.getMessage());
        }
    }

    // Abstract methods to be implemented by concrete strategies
    protected abstract boolean doInitialize();
    protected abstract boolean doStart();
    protected abstract boolean doStop();
    protected abstract List<Signal> doProcessCandle(CandleData candle);
    protected abstract List<Signal> doProcessIndicators(IndicatorValue indicators);
    protected abstract ValidationResult doValidateConfiguration();

    // Helper methods
    protected boolean validateSignal(Signal signal) {
        return signal != null && 
               signal.getSymbol() != null && !signal.getSymbol().trim().isEmpty() &&
               signal.getAction() != null &&
               signal.getOrderType() != null &&
               signal.getQuantity() != null && signal.getQuantity() > 0 &&
               (signal.getPrice() == null || signal.getPrice().compareTo(BigDecimal.ZERO) > 0);
    }

    protected void publishSignal(Signal signal) {
        try {
            kafkaTemplate.send("trading-signals", signal.getSymbol(), signal);
        } catch (Exception e) {
            log.error("Error publishing signal from strategy {}", name, e);
        }
    }

    protected void publishStrategyState() {
        try {
            kafkaTemplate.send("strategy-states", strategyId, getInfo());
        } catch (Exception e) {
            log.error("Error publishing state for strategy {}", name, e);
        }
    }

    /**
     * Strategy state enum
     */
    public enum StrategyState {
        INITIALIZED("Initialized"),
        ACTIVE("Active"),
        STOPPED("Stopped"),
        ERROR("Error"),
        PAUSED("Paused");

        private final String description;

        StrategyState(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Strategy type enum
     */
    public enum StrategyType {
        MOMENTUM("Momentum"),
        MEAN_REVERSION("Mean Reversion"),
        TREND_FOLLOWING("Trend Following"),
        VOLATILITY("Volatility"),
        ARBITRAGE("Arbitrage"),
        CUSTOM("Custom");

        private final String description;

        StrategyType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Signal class for trading signals
     */
    public static class Signal {
        private final String strategyId;
        private final String symbol;
        private final SignalAction action;
        private final OrderType orderType;
        private final BigDecimal price;
        private final Integer quantity;
        private final BigDecimal stopLoss;
        private final BigDecimal takeProfit;
        private final Instant timestamp;
        private final Map<String, Object> metadata;

        public Signal(String strategyId, String symbol, SignalAction action, OrderType orderType,
                  BigDecimal price, Integer quantity, BigDecimal stopLoss, BigDecimal takeProfit,
                  Map<String, Object> metadata) {
            this.strategyId = strategyId;
            this.symbol = symbol;
            this.action = action;
            this.orderType = orderType;
            this.price = price;
            this.quantity = quantity;
            this.stopLoss = stopLoss;
            this.takeProfit = takeProfit;
            this.timestamp = Instant.now();
            this.metadata = metadata != null ? metadata : Map.of();
        }

        // Getters
        public String getStrategyId() { return strategyId; }
        public String getSymbol() { return symbol; }
        public SignalAction getAction() { return action; }
        public OrderType getOrderType() { return orderType; }
        public BigDecimal getPrice() { return price; }
        public Integer getQuantity() { return quantity; }
        public BigDecimal getStopLoss() { return stopLoss; }
        public BigDecimal getTakeProfit() { return takeProfit; }
        public Instant getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return metadata; }

        @Override
        public String toString() {
            return String.format("Signal[%s %s %s %s@%.2f qty=%d SL=%.2f TP=%.2f]", 
                             strategyId, symbol, action, orderType, price, quantity, stopLoss, takeProfit);
        }
    }

    /**
     * Signal action enum
     */
    public enum SignalAction {
        BUY("Buy"),
        SELL("Sell"),
        CLOSE("Close"),
        MODIFY("Modify"),
        CANCEL("Cancel");

        private final String description;

        SignalAction(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Order type enum
     */
    public enum OrderType {
        MARKET("Market"),
        LIMIT("Limit"),
        STOP("Stop"),
        STOP_LIMIT("Stop Limit");

        private final String description;

        OrderType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }

    /**
     * Strategy information class
     */
    public static class StrategyInfo {
        private final String strategyId;
        private final String name;
        private final String description;
        private final StrategyType type;
        private final StrategyState state;
        private final Map<String, Object> parameters;
        private final Instant lastUpdateTime;
        private final Instant lastExecutionTime;
        private final int executionCount;
        private final int successCount;
        private final int failureCount;
        private final BigDecimal totalPnL;
        private final BigDecimal maxDrawdown;
        private final BigDecimal peakEquity;

        private StrategyInfo(String strategyId, String name, String description, StrategyType type,
                        StrategyState state, Map<String, Object> parameters, Instant lastUpdateTime,
                        Instant lastExecutionTime, int executionCount, int successCount, int failureCount,
                        BigDecimal totalPnL, BigDecimal maxDrawdown, BigDecimal peakEquity) {
            this.strategyId = strategyId;
            this.name = name;
            this.description = description;
            this.type = type;
            this.state = state;
            this.parameters = parameters;
            this.lastUpdateTime = lastUpdateTime;
            this.lastExecutionTime = lastExecutionTime;
            this.executionCount = executionCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.totalPnL = totalPnL;
            this.maxDrawdown = maxDrawdown;
            this.peakEquity = peakEquity;
        }

        // Getters
        public String getStrategyId() { return strategyId; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public StrategyType getType() { return type; }
        public StrategyState getState() { return state; }
        public Map<String, Object> getParameters() { return parameters; }
        public Instant getLastUpdateTime() { return lastUpdateTime; }
        public Instant getLastExecutionTime() { return lastExecutionTime; }
        public int getExecutionCount() { return executionCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailureCount() { return failureCount; }
        public BigDecimal getTotalPnL() { return totalPnL; }
        public BigDecimal getMaxDrawdown() { return maxDrawdown; }
        public BigDecimal getPeakEquity() { return peakEquity; }
    }
}