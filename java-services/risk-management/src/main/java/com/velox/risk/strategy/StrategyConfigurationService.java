package com.velox.risk.strategy;

import com.velox.risk.strategy.Strategy.SignalAction;
import com.velox.risk.strategy.Strategy.StrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Strategy configuration and validation service.
 * Manages strategy configurations, validates parameters, and ensures consistency.
 */
@Service
@Slf4j
public class StrategyConfigurationService {

    private final Map<String, StrategyConfiguration> strategyConfigs;
    private final Map<String, Set<String>> requiredParameters;

    @Autowired
    public StrategyConfigurationService() {
        this.strategyConfigs = new HashMap<>();
        this.requiredParameters = new HashMap<>();
        
        initializeDefaultConfigurations();
    }

    /**
     * Get strategy configuration
     */
    public StrategyConfiguration getConfiguration(String strategyId) {
        return strategyConfigs.get(strategyId);
    }

    /**
     * Get all strategy configurations
     */
    public Map<String, StrategyConfiguration> getAllConfigurations() {
        return new HashMap<>(strategyConfigs);
    }

    /**
     * Save strategy configuration
     */
    public CompletableFuture<Boolean> saveConfiguration(String strategyId, StrategyConfiguration config) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate configuration
                ValidationResult validation = validateConfiguration(strategyId, config);
                if (!validation.isValid()) {
                    log.error("Strategy configuration validation failed for {}: {}", strategyId, validation.getErrors());
                    return false;
                }
                
                // Save configuration
                strategyConfigs.put(strategyId, config);
                
                log.info("Saved configuration for strategy {}", strategyId);
                return true;
                
            } catch (Exception e) {
                log.error("Error saving configuration for strategy {}", strategyId, e);
                return false;
            }
        });
    }

    /**
     * Update strategy configuration
     */
    public CompletableFuture<Boolean> updateConfiguration(String strategyId, Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StrategyConfiguration existingConfig = strategyConfigs.get(strategyId);
                if (existingConfig == null) {
                    log.error("Strategy configuration not found for {}", strategyId);
                    return false;
                }
                
                // Update parameters
                Map<String, Object> updatedParameters = new HashMap<>(existingConfig.getParameters());
                updatedParameters.putAll(parameters);
                
                // Create updated configuration
                StrategyConfiguration updatedConfig = StrategyConfiguration.builder()
                        .strategyId(strategyId)
                        .name(existingConfig.getName())
                        .type(existingConfig.getType())
                        .description(existingConfig.getDescription())
                        .parameters(updatedParameters)
                        .enabled(existingConfig.isEnabled())
                        .build();
                
                // Validate updated configuration
                ValidationResult validation = validateConfiguration(strategyId, updatedConfig);
                if (!validation.isValid()) {
                    log.error("Updated strategy configuration validation failed for {}: {}", strategyId, validation.getErrors());
                    return false;
                }
                
                // Save updated configuration
                strategyConfigs.put(strategyId, updatedConfig);
                
                log.info("Updated configuration for strategy {}", strategyId);
                return true;
                
            } catch (Exception e) {
                log.error("Error updating configuration for strategy {}", strategyId, e);
                return false;
            }
        });
    }

    /**
     * Delete strategy configuration
     */
    public CompletableFuture<Boolean> deleteConfiguration(String strategyId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!strategyConfigs.containsKey(strategyId)) {
                    log.error("Strategy configuration not found for {}", strategyId);
                    return false;
                }
                
                // Remove configuration
                strategyConfigs.remove(strategyId);
                
                log.info("Deleted configuration for strategy {}", strategyId);
                return true;
                
            } catch (Exception e) {
                log.error("Error deleting configuration for strategy {}", strategyId, e);
                return false;
            }
        });
    }

    /**
     * Enable/disable strategy
     */
    public CompletableFuture<Boolean> setStrategyEnabled(String strategyId, boolean enabled) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StrategyConfiguration config = strategyConfigs.get(strategyId);
                if (config == null) {
                    log.error("Strategy configuration not found for {}", strategyId);
                    return false;
                }
                
                // Update enabled status
                StrategyConfiguration updatedConfig = StrategyConfiguration.builder()
                        .strategyId(strategyId)
                        .name(config.getName())
                        .type(config.getType())
                        .description(config.getDescription())
                        .parameters(config.getParameters())
                        .enabled(enabled)
                        .build();
                
                // Save updated configuration
                strategyConfigs.put(strategyId, updatedConfig);
                
                log.info("{} strategy {}", enabled ? "Enabled" : "Disabled", strategyId);
                return true;
                
            } catch (Exception e) {
                log.error("Error {} strategy {}: {}", enabled ? "enabling" : "disabling", strategyId, e);
                return false;
            }
        });
    }

    /**
     * Validate strategy configuration
     */
    public ValidationResult validateConfiguration(String strategyId, StrategyConfiguration config) {
        List<String> errors = new ArrayList<>();
        
        // Basic validation
        if (config.getStrategyId() == null || config.getStrategyId().trim().isEmpty()) {
            errors.add("Strategy ID is required");
        }
        
        if (config.getName() == null || config.getName().trim().isEmpty()) {
            errors.add("Strategy name is required");
        }
        
        if (config.getType() == null) {
            errors.add("Strategy type is required");
        }
        
        // Parameter validation
        Set<String> requiredParams = requiredParameters.get(config.getType().name());
        if (requiredParams != null) {
            for (String param : requiredParams) {
                if (!config.getParameters().containsKey(param)) {
                    errors.add("Required parameter missing: " + param);
                }
            }
        }
        
        // Type-specific validation
        switch (config.getType()) {
            case MOMENTUM:
                validateMomentumStrategy(config, errors);
                break;
            case MEAN_REVERSION:
                validateMeanReversionStrategy(config, errors);
                break;
            case TREND_FOLLOWING:
                validateTrendFollowingStrategy(config, errors);
                break;
            case VOLATILITY:
                validateVolatilityStrategy(config, errors);
                break;
            case ARBITRAGE:
                validateArbitrageStrategy(config, errors);
                break;
            case CUSTOM:
                validateCustomStrategy(config, errors);
                break;
        }
        
        return ValidationResult.builder()
                .valid(errors.isEmpty())
                .errors(errors)
                .build();
    }

    /**
     * Validate momentum strategy parameters
     */
    private void validateMomentumStrategy(StrategyConfiguration config, List<String> errors) {
        // Check required parameters
        Map<String, Object> params = config.getParameters();
        
        // RSI period
        if (params.containsKey("rsiPeriod")) {
            Object rsiPeriod = params.get("rsiPeriod");
            if (rsiPeriod == null || !(rsiPeriod instanceof Integer) || (Integer) rsiPeriod < 2 || (Integer) rsiPeriod > 50) {
                errors.add("RSI period must be between 2 and 50");
            }
        }
        
        // MACD parameters
        if (params.containsKey("macdFast")) {
            Object macdFast = params.get("macdFast");
            if (macdFast == null || !(macdFast instanceof Integer) || (Integer) macdFast < 5 || (Integer) macdFast > 20) {
                errors.add("MACD fast period must be between 5 and 20");
            }
        }
        
        if (params.containsKey("macdSlow")) {
            Object macdSlow = params.get("macdSlow");
            if (macdSlow == null || !(macdSlow instanceof Integer) || (Integer) macdSlow < 10 || (Integer) macdSlow > 50) {
                errors.add("MACD slow period must be between 10 and 50");
            }
        }
        
        if (params.containsKey("macdSignal")) {
            Object macdSignal = params.get("macdSignal");
            if (macdSignal == null || !(macdSignal instanceof Integer) || (Integer) macdSignal < 2 || (Integer) macdSignal > 20) {
                errors.add("MACD signal period must be between 2 and 20");
            }
        }
    }

    /**
     * Validate mean reversion strategy parameters
     */
    private void validateMeanReversionStrategy(StrategyConfiguration config, List<String> errors) {
        // Check required parameters
        Map<String, Object> params = config.getParameters();
        
        // Bollinger Bands parameters
        if (params.containsKey("bbPeriod")) {
            Object bbPeriod = params.get("bbPeriod");
            if (bbPeriod == null || !(bbPeriod instanceof Integer) || (Integer) bbPeriod < 5 || (Integer) bbPeriod > 50) {
                errors.add("Bollinger Bands period must be between 5 and 50");
            }
        }
        
        if (params.containsKey("bbStdDev")) {
            Object bbStdDev = params.get("bbStdDev");
            if (bbStdDev == null || !(bbStdDev instanceof Double) || (Double) bbStdDev < 0.5 || (Double) bbStdDev > 3.0) {
                errors.add("Bollinger Bands standard deviation must be between 0.5 and 3.0");
            }
        }
    }

    /**
     * Validate trend following strategy parameters
     */
    private void validateTrendFollowingStrategy(StrategyConfiguration config, List<String> errors) {
        // Check required parameters
        Map<String, Object> params = config.getParameters();
        
        // Moving average parameters
        if (params.containsKey("maPeriod")) {
            Object maPeriod = params.get("maPeriod");
            if (maPeriod == null || !(maPeriod instanceof Integer) || (Integer) maPeriod < 5 || (Integer) maPeriod > 200) {
                errors.add("Moving average period must be between 5 and 200");
            }
        }
        
        // EMA parameters
        if (params.containsKey("emaPeriod")) {
            Object emaPeriod = params.get("emaPeriod");
            if (emaPeriod == null || !(emaPeriod instanceof Integer) || (Integer) emaPeriod < 2 || (Integer) emaPeriod > 100) {
                errors.add("EMA period must be between 2 and 100");
            }
        }
    }

    /**
     * Validate volatility strategy parameters
     */
    private void validateVolatilityStrategy(StrategyConfiguration config, List<String> errors) {
        // Check required parameters
        Map<String, Object> params = config.getParameters();
        
        // ATR parameters
        if (params.containsKey("atrPeriod")) {
            Object atrPeriod = params.get("atrPeriod");
            if (atrPeriod == null || !(atrPeriod instanceof Integer) || (Integer) atrPeriod < 5 || (Integer) atrPeriod > 50) {
                errors.add("ATR period must be between 5 and 50");
            }
        }
    }

    /**
     * Validate arbitrage strategy parameters
     */
    private void validateArbitrageStrategy(StrategyConfiguration config, List<String> errors) {
        // Check required parameters
        Map<String, Object> params = config.getParameters();
        
        // Minimum profit margin
        if (params.containsKey("minProfitMargin")) {
            Object minProfitMargin = params.get("minProfitMargin");
            if (minProfitMargin == null || !(minProfitMargin instanceof Double) || (Double) minProfitMargin < 0.001 || (Double) minProfitMargin > 0.1) {
                errors.add("Minimum profit margin must be between 0.001 and 0.1");
            }
        }
        
        // Maximum position size
        if (params.containsKey("maxPositionSize")) {
            Object maxPositionSize = params.get("maxPositionSize");
            if (maxPositionSize == null || !(maxPositionSize instanceof Double) || (Double) maxPositionSize <= 0) {
                errors.add("Maximum position size must be positive");
            }
        }
    }

    /**
     * Validate custom strategy parameters
     */
    private void validateCustomStrategy(StrategyConfiguration config, List<String> errors) {
        // Custom strategies have flexible parameters
        // Just check for basic parameter constraints
        Map<String, Object> params = config.getParameters();
        
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // Check for null values
            if (value == null) {
                errors.add("Parameter " + key + " cannot be null");
            }
            
            // Check for empty strings
            if (value instanceof String && ((String) value).trim().isEmpty()) {
                errors.add("Parameter " + key + " cannot be empty");
            }
            
            // Check for negative numbers
            if (value instanceof Number && ((Number) value).doubleValue() < 0) {
                // Allow negative values for specific parameters
                if (!key.toLowerCase().contains("stoploss") && !key.toLowerCase().contains("pnl")) {
                    errors.add("Parameter " + key + " cannot be negative");
                }
            }
        }
    }

    /**
     * Initialize default strategy configurations
     */
    private void initializeDefaultConfigurations() {
        // Initialize required parameters for each strategy type
        requiredParameters.put("MOMENTUM", Set.of("rsiPeriod", "macdFast", "macdSlow", "macdSignal"));
        requiredParameters.put("MEAN_REVERSION", Set.of("bbPeriod", "bbStdDev"));
        requiredParameters.put("TREND_FOLLOWING", Set.of("maPeriod", "emaPeriod"));
        requiredParameters.put("VOLATILITY", Set.of("atrPeriod"));
        requiredParameters.put("ARBITRAGE", Set.of("minProfitMargin", "maxPositionSize"));
        requiredParameters.put("CUSTOM", Set.of());
        
        // Create default configurations
        createDefaultMomentumStrategy();
        createDefaultMeanReversionStrategy();
        createDefaultTrendFollowingStrategy();
    }

    /**
     * Create default momentum strategy configuration
     */
    private void createDefaultMomentumStrategy() {
        Map<String, Object> params = Map.of(
                "rsiPeriod", 14,
                "macdFast", 12,
                "macdSlow", 26,
                "macdSignal", 9,
                "signalAction", "BUY",
                "quantity", 100,
                "stopLoss", 2.0,
                "takeProfit", 5.0
        );
        
        StrategyConfiguration config = StrategyConfiguration.builder()
                .strategyId("momentum-rsi-macd")
                .name("RSI-MACD Momentum Strategy")
                .type(StrategyType.MOMENTUM)
                .description("Combines RSI and MACD indicators for momentum trading")
                .parameters(params)
                .enabled(false)
                .build();
        
        strategyConfigs.put(config.getStrategyId(), config);
    }

    /**
     * Create default mean reversion strategy configuration
     */
    private void createDefaultMeanReversionStrategy() {
        Map<String, Object> params = Map.of(
                "bbPeriod", 20,
                "bbStdDev", 2.0,
                "signalAction", "BUY",
                "quantity", 100,
                "stopLoss", 2.0,
                "takeProfit", 5.0
        );
        
        StrategyConfiguration config = StrategyConfiguration.builder()
                .strategyId("mean-reversion-bb")
                .name("Bollinger Bands Mean Reversion Strategy")
                .type(StrategyType.MEAN_REVERSION)
                .description("Uses Bollinger Bands for mean reversion trading")
                .parameters(params)
                .enabled(false)
                .build();
        
        strategyConfigs.put(config.getStrategyId(), config);
    }

    /**
     * Create default trend following strategy configuration
     */
    private void createDefaultTrendFollowingStrategy() {
        Map<String, Object> params = Map.of(
                "maPeriod", 20,
                "emaPeriod", 20,
                "signalAction", "BUY",
                "quantity", 100,
                "stopLoss", 2.0,
                "takeProfit", 5.0
        );
        
        StrategyConfiguration config = StrategyConfiguration.builder()
                .strategyId("trend-following-ma")
                .name("Moving Average Trend Following Strategy")
                .type(StrategyType.TREND_FOLLOWING)
                .description("Uses moving averages for trend following trading")
                .parameters(params)
                .enabled(false)
                .build();
        
        strategyConfigs.put(config.getStrategyId(), config);
    }

    /**
     * Strategy configuration class
     */
    @lombok.Builder
    @lombok.Data
    public static class StrategyConfiguration {
        private String strategyId;
        private String name;
        private String description;
        private StrategyType type;
        private Map<String, Object> parameters;
        private boolean enabled;
    }

    /**
     * Validation result class
     */
    @lombok.Builder
    @lombok.Data
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
    }
}