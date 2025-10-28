package com.velox.risk.strategy;

import com.velox.risk.strategy.Strategy.Signal;
import com.velox.risk.strategy.Strategy.SignalAction;
import com.velox.risk.strategy.Strategy.StrategyInfo;
import com.velox.risk.strategy.Strategy.StrategyState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Strategy execution engine.
 * Manages strategy lifecycle, executes trading signals, and handles order placement.
 */
@Service
@Slf4j
public class StrategyExecutionEngine {

    private final KafkaTemplate<String, Object> signalKafkaTemplate;
    private final Map<String, Strategy> activeStrategies;
    private final Map<String, StrategyInfo> strategyInfos;
    private final Map<String, Instant> lastSignalTimes;

    @Value("${strategy.execution.max.concurrent.orders:10}")
    private int maxConcurrentOrders;

    @Value("${strategy.execution.signal.timeout.ms:5000}")
    private long signalTimeoutMs;

    // Performance metrics
    private final AtomicLong signalsProcessed = new AtomicLong(0);
    private final AtomicLong ordersPlaced = new AtomicLong(0);
    private final AtomicLong ordersFailed = new AtomicLong(0);
    private final AtomicLong strategiesActive = new AtomicLong(0);

    @Autowired
    public StrategyExecutionEngine(KafkaTemplate<String, Object> signalKafkaTemplate) {
        this.signalKafkaTemplate = signalKafkaTemplate;
        this.activeStrategies = new ConcurrentHashMap<>();
        this.strategyInfos = new ConcurrentHashMap<>();
        this.lastSignalTimes = new ConcurrentHashMap<>();
    }

    /**
     * Register a strategy for execution
     */
    public CompletableFuture<Void> registerStrategy(Strategy strategy) {
        return CompletableFuture.runAsync(() -> {
            try {
                String strategyId = strategy.strategyId;
                
                if (activeStrategies.containsKey(strategyId)) {
                    log.warn("Strategy {} already registered", strategyId);
                    return;
                }
                
                // Validate strategy configuration
                var validationResult = strategy.validateConfiguration();
                if (!validationResult.isValid()) {
                    log.error("Strategy {} configuration validation failed: {}", strategyId, validationResult.getMessage());
                    return;
                }
                
                // Initialize strategy
                strategy.initialize().get();
                
                // Store strategy
                activeStrategies.put(strategyId, strategy);
                strategyInfos.put(strategyId, strategy.getInfo());
                strategiesActive.incrementAndGet();
                
                log.info("Strategy {} registered and initialized", strategyId);
                
            } catch (Exception e) {
                log.error("Error registering strategy {}", strategy.strategyId, e);
            }
        });
    }

    /**
     * Unregister a strategy
     */
    public CompletableFuture<Void> unregisterStrategy(String strategyId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Strategy strategy = activeStrategies.get(strategyId);
                if (strategy == null) {
                    log.warn("Strategy {} not found for unregistration", strategyId);
                    return;
                }
                
                // Stop strategy
                strategy.stop().get();
                
                // Remove from active strategies
                activeStrategies.remove(strategyId);
                strategyInfos.remove(strategyId);
                lastSignalTimes.remove(strategyId);
                strategiesActive.decrementAndGet();
                
                log.info("Strategy {} unregistered", strategyId);
                
            } catch (Exception e) {
                log.error("Error unregistering strategy {}", strategyId, e);
            }
        });
    }

    /**
     * Start a strategy
     */
    public CompletableFuture<Void> startStrategy(String strategyId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Strategy strategy = activeStrategies.get(strategyId);
                if (strategy == null) {
                    log.warn("Strategy {} not found for starting", strategyId);
                    return;
                }
                
                // Start strategy
                strategy.start().get();
                
                log.info("Strategy {} started", strategyId);
                
            } catch (Exception e) {
                log.error("Error starting strategy {}", strategyId, e);
            }
        });
    }

    /**
     * Stop a strategy
     */
    public CompletableFuture<Void> stopStrategy(String strategyId) {
        return CompletableFuture.runAsync(() -> {
            try {
                Strategy strategy = activeStrategies.get(strategyId);
                if (strategy == null) {
                    log.warn("Strategy {} not found for stopping", strategyId);
                    return;
                }
                
                // Stop strategy
                strategy.stop().get();
                
                log.info("Strategy {} stopped", strategyId);
                
            } catch (Exception e) {
                log.error("Error stopping strategy {}", strategyId, e);
            }
        });
    }

    /**
     * Process trading signal from strategy
     */
    @Async
    public CompletableFuture<Void> processSignal(Signal signal) {
        return CompletableFuture.runAsync(() -> {
            try {
                String strategyId = signal.getStrategyId();
                Strategy strategy = activeStrategies.get(strategyId);
                
                if (strategy == null) {
                    log.warn("Strategy {} not found for signal processing", strategyId);
                    return;
                }
                
                // Check if strategy is active
                if (strategy.getState() != Strategy.StrategyState.ACTIVE) {
                    log.warn("Strategy {} not active, ignoring signal", strategyId);
                    return;
                }
                
                // Check signal timeout
                Instant lastSignalTime = lastSignalTimes.get(strategyId);
                if (lastSignalTime != null) {
                    long timeSinceLastSignal = Instant.now().toEpochMilli() - lastSignalTime.toEpochMilli();
                    if (timeSinceLastSignal < signalTimeoutMs) {
                        log.warn("Signal timeout for strategy {}, ignoring signal", strategyId);
                        return;
                    }
                }
                
                // Update last signal time
                lastSignalTimes.put(strategyId, Instant.now());
                signalsProcessed.incrementAndGet();
                
                // Handle signal
                strategy.handleSignal(signal);
                
                log.debug("Processed signal from strategy {}: {}", strategyId, signal);
                
            } catch (Exception e) {
                log.error("Error processing signal from strategy {}", signal.getStrategyId(), e);
            }
        });
    }

    /**
     * Get all active strategies
     */
    public List<StrategyInfo> getActiveStrategies() {
        return new ArrayList<>(strategyInfos.values());
    }

    /**
     * Get strategy information
     */
    public StrategyInfo getStrategyInfo(String strategyId) {
        return strategyInfos.get(strategyId);
    }

    /**
     * Get execution statistics
     */
    public ExecutionStatistics getStatistics() {
        return ExecutionStatistics.builder()
                .signalsProcessed(signalsProcessed.get())
                .ordersPlaced(ordersPlaced.get())
                .ordersFailed(ordersFailed.get())
                .strategiesActive(strategiesActive.get())
                .activeStrategies(activeStrategies.size())
                .build();
    }

    /**
     * Cleanup inactive strategies
     */
    public void cleanupInactiveStrategies() {
        Instant cutoff = Instant.now().minusSeconds(300); // 5 minutes
        int cleanedCount = 0;
        
        for (Map.Entry<String, StrategyInfo> entry : strategyInfos.entrySet()) {
            if (entry.getValue().getLastUpdateTime().isBefore(cutoff)) {
                String strategyId = entry.getKey();
                Strategy strategy = activeStrategies.get(strategyId);
                
                if (strategy != null && strategy.getState() == Strategy.StrategyState.ACTIVE) {
                    // Stop inactive strategy
                    strategy.stop();
                    log.info("Stopped inactive strategy: {}", strategyId);
                    cleanedCount++;
                }
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} inactive strategies", cleanedCount);
        }
    }

    /**
     * Restart failed strategies
     */
    public void restartFailedStrategies() {
        int restartedCount = 0;
        
        for (Map.Entry<String, Strategy> entry : activeStrategies.entrySet()) {
            String strategyId = entry.getKey();
            Strategy strategy = entry.getValue();
            
            if (strategy != null && strategy.getState() == Strategy.StrategyState.ERROR) {
                try {
                    // Stop and restart strategy
                    strategy.stop().get();
                    Thread.sleep(1000); // Brief pause
                    strategy.start().get();
                    
                    log.info("Restarted failed strategy: {}", strategyId);
                    restartedCount++;
                } catch (Exception e) {
                    log.error("Error restarting strategy {}", strategyId, e);
                }
            }
        }
        
        if (restartedCount > 0) {
            log.info("Restarted {} failed strategies", restartedCount);
        }
    }

    /**
     * Handle order placement result
     */
    public void handleOrderResult(String strategyId, String orderId, boolean success, String message) {
        try {
            Strategy strategy = activeStrategies.get(strategyId);
            if (strategy == null) {
                return;
            }
            
            if (success) {
                ordersPlaced.incrementAndGet();
                log.info("Order {} placed successfully for strategy {}", orderId, strategyId);
            } else {
                ordersFailed.incrementAndGet();
                log.error("Order {} placement failed for strategy {}: {}", orderId, strategyId, message);
            }
            
        } catch (Exception e) {
            log.error("Error handling order result for strategy {}", strategyId, e);
        }
    }

    /**
     * Get strategy performance metrics
     */
    public Map<String, StrategyPerformance> getStrategyPerformance() {
        Map<String, StrategyPerformance> performance = new HashMap<>();
        
        for (Map.Entry<String, Strategy> entry : activeStrategies.entrySet()) {
            String strategyId = entry.getKey();
            Strategy strategy = entry.getValue();
            StrategyInfo info = strategy.getInfo();
            
            StrategyPerformance perf = StrategyPerformance.builder()
                    .strategyId(strategyId)
                    .name(info.getName())
                    .state(info.getState())
                    .executionCount(info.getExecutionCount())
                    .successCount(info.getSuccessCount())
                    .failureCount(info.getFailureCount())
                    .totalPnL(info.getTotalPnL())
                    .maxDrawdown(info.getMaxDrawdown())
                    .lastUpdateTime(info.getLastUpdateTime())
                    .build();
            
            performance.put(strategyId, perf);
        }
        
        return performance;
    }

    /**
     * Scheduled cleanup of inactive strategies
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void scheduledCleanup() {
        cleanupInactiveStrategies();
    }

    /**
     * Scheduled restart of failed strategies
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void scheduledRestart() {
        restartFailedStrategies();
    }

    /**
     * Execution statistics class
     */
    @lombok.Builder
    @lombok.Data
    public static class ExecutionStatistics {
        private long signalsProcessed;
        private long ordersPlaced;
        private long ordersFailed;
        private long strategiesActive;
        private int activeStrategies;
    }

    /**
     * Strategy performance class
     */
    @lombok.Builder
    @lombok.Data
    public static class StrategyPerformance {
        private String strategyId;
        private String name;
        private StrategyState state;
        private int executionCount;
        private int successCount;
        private int failureCount;
        private java.math.BigDecimal totalPnL;
        private java.math.BigDecimal maxDrawdown;
        private Instant lastUpdateTime;
    }
}