package com.velox.risk.service;

import com.velox.risk.model.RiskEvaluationResult;
import com.velox.risk.model.RiskViolation;
import com.velox.risk.model.UserRiskState;
import com.velox.risk.repository.UserRiskStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time risk management service.
 * Monitors trading positions, enforces risk limits, and provides automated risk controls.
 */
@Service
@Slf4j
public class RiskManager {

    private final UserRiskStateRepository riskStateRepository;
    private final KafkaTemplate<String, Object> riskKafkaTemplate;
    private final Map<String, RiskRule> riskRules;
    private final Map<String, UserRiskState> userRiskStates;

    @Value("${risk.daily.loss.limit.percent:2.0}")
    private BigDecimal dailyLossLimitPercent;

    @Value("${risk.max.drawdown.percent:10.0}")
    private BigDecimal maxDrawdownPercent;

    @Value("${risk.max.position.size.percent:20.0}")
    private BigDecimal maxPositionSizePercent;

    @Value("${risk.risk.per.trade.percent:1.0}")
    private BigDecimal riskPerTradePercent;

    // Performance metrics
    private final AtomicLong riskEvaluations = new AtomicLong(0);
    private final AtomicLong riskViolations = new AtomicLong(0);
    private final AtomicLong emergencyExits = new AtomicLong(0);

    @Autowired
    public RiskManager(
            UserRiskStateRepository riskStateRepository,
            KafkaTemplate<String, Object> riskKafkaTemplate) {
        this.riskStateRepository = riskStateRepository;
        this.riskKafkaTemplate = riskKafkaTemplate;
        this.riskRules = new ConcurrentHashMap<>();
        this.userRiskStates = new ConcurrentHashMap<>();
        
        initializeDefaultRiskRules();
    }

    /**
     * Evaluate risk for a new order
     */
    @Async
    public CompletableFuture<RiskEvaluationResult> evaluateOrderRisk(String userId, String symbol, 
                                                              BigDecimal quantity, BigDecimal price, 
                                                              String orderType, String orderSide) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UserRiskState riskState = getUserRiskState(userId);
                if (riskState == null) {
                    return RiskEvaluationResult.builder()
                            .allowed(false)
                            .reason("User risk state not found")
                            .build();
                }

                List<RiskViolation> violations = new ArrayList<>();
                
                // Check daily loss limit
                RiskViolation dailyLossViolation = checkDailyLossLimit(riskState);
                if (dailyLossViolation != null) {
                    violations.add(dailyLossViolation);
                }
                
                // Check maximum drawdown
                RiskViolation drawdownViolation = checkMaxDrawdown(riskState);
                if (drawdownViolation != null) {
                    violations.add(drawdownViolation);
                }
                
                // Check position size limit
                RiskViolation positionSizeViolation = checkMaxPositionSize(riskState, symbol, quantity);
                if (positionSizeViolation != null) {
                    violations.add(positionSizeViolation);
                }
                
                // Check risk per trade
                RiskViolation riskPerTradeViolation = checkRiskPerTrade(riskState, quantity, price);
                if (riskPerTradeViolation != null) {
                    violations.add(riskPerTradeViolation);
                }
                
                // Check overall exposure
                RiskViolation exposureViolation = checkOverallExposure(riskState);
                if (exposureViolation != null) {
                    violations.add(exposureViolation);
                }
                
                riskEvaluations.incrementAndGet();
                
                boolean allowed = violations.isEmpty();
                String reason = allowed ? "Order allowed" : "Order rejected due to risk violations";
                
                // Log risk evaluation
                if (!allowed) {
                    log.warn("Order rejected for user {} on symbol {}: {}", userId, symbol, reason);
                    riskViolations.incrementAndGet();
                } else {
                    log.debug("Order allowed for user {} on symbol {}", userId, symbol);
                }
                
                // Publish risk evaluation result
                publishRiskEvaluation(userId, symbol, quantity, price, violations);
                
                return RiskEvaluationResult.builder()
                        .allowed(allowed)
                        .reason(reason)
                        .violations(violations)
                        .timestamp(Instant.now())
                        .build();
                        
            } catch (Exception e) {
                log.error("Error evaluating order risk for user {} on symbol {}", userId, symbol, e);
                
                return RiskEvaluationResult.builder()
                        .allowed(false)
                        .reason("Risk evaluation error: " + e.getMessage())
                        .violations(new ArrayList<>())
                        .timestamp(Instant.now())
                        .build();
            }
        });
    }

    /**
     * Evaluate risk for a new position
     */
    @Async
    public CompletableFuture<RiskEvaluationResult> evaluatePositionRisk(String userId, String symbol, 
                                                               BigDecimal quantity, BigDecimal price) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UserRiskState riskState = getUserRiskState(userId);
                if (riskState == null) {
                    return RiskEvaluationResult.builder()
                            .allowed(false)
                            .reason("User risk state not found")
                            .build();
                }

                List<RiskViolation> violations = new ArrayList<>();
                
                // Check position concentration
                RiskViolation concentrationViolation = checkPositionConcentration(riskState, symbol, quantity);
                if (concentrationViolation != null) {
                    violations.add(concentrationViolation);
                }
                
                // Check sector exposure
                RiskViolation sectorViolation = checkSectorExposure(riskState, symbol);
                if (sectorViolation != null) {
                    violations.add(sectorViolation);
                }
                
                riskEvaluations.incrementAndGet();
                
                boolean allowed = violations.isEmpty();
                String reason = allowed ? "Position allowed" : "Position rejected due to risk violations";
                
                // Log risk evaluation
                if (!allowed) {
                    log.warn("Position rejected for user {} on symbol {}: {}", userId, symbol, reason);
                    riskViolations.incrementAndGet();
                } else {
                    log.debug("Position allowed for user {} on symbol {}", userId, symbol);
                }
                
                // Publish risk evaluation result
                publishRiskEvaluation(userId, symbol, quantity, price, violations);
                
                return RiskEvaluationResult.builder()
                        .allowed(allowed)
                        .reason(reason)
                        .violations(violations)
                        .timestamp(Instant.now())
                        .build();
                        
            } catch (Exception e) {
                log.error("Error evaluating position risk for user {} on symbol {}", userId, symbol, e);
                
                return RiskEvaluationResult.builder()
                        .allowed(false)
                        .reason("Risk evaluation error: " + e.getMessage())
                        .violations(new ArrayList<>())
                        .timestamp(Instant.now())
                        .build();
            }
        });
    }

    /**
     * Update user risk state with new trade
     */
    public void updateRiskStateWithTrade(String userId, String symbol, BigDecimal quantity, 
                                     BigDecimal price, String orderSide, BigDecimal pnl) {
        UserRiskState riskState = getUserRiskState(userId);
        if (riskState == null) {
            log.warn("Cannot update risk state for user {}: state not found", userId);
            return;
        }

        // Update daily P&L
        BigDecimal currentDailyPnL = riskState.getCurrentDailyPnL();
        BigDecimal newDailyPnL = currentDailyPnL.add(pnl);
        riskState.setCurrentDailyPnL(newDailyPnL);
        
        // Update maximum drawdown
        BigDecimal currentDrawdown = riskState.getCurrentDrawdown();
        BigDecimal peakEquity = riskState.getPeakEquity();
        BigDecimal currentEquity = peakEquity.add(newDailyPnL);
        BigDecimal newDrawdown = peakEquity.subtract(currentEquity);
        
        if (newDrawdown.compareTo(currentDrawdown) > 0) {
            riskState.setCurrentDrawdown(newDrawdown);
        }
        
        // Update total exposure
        BigDecimal currentExposure = riskState.getTotalExposure();
        BigDecimal tradeExposure = quantity.multiply(price);
        BigDecimal newExposure = currentExposure.add(tradeExposure);
        riskState.setTotalExposure(newExposure);
        
        // Update last activity
        riskState.setLastActivity(Instant.now());
        
        // Save updated state
        riskStateRepository.save(riskState);
        userRiskStates.put(userId, riskState);
        
        log.debug("Updated risk state for user {} with trade on {}: P&L={}, Drawdown={}, Exposure={}", 
                  userId, symbol, newDailyPnL, newDrawdown, newExposure);
    }

    /**
     * Check if emergency exit should be triggered
     */
    public boolean shouldTriggerEmergencyExit(String userId) {
        UserRiskState riskState = getUserRiskState(userId);
        if (riskState == null) {
            return false;
        }

        // Check if daily loss limit exceeded
        BigDecimal dailyLossLimit = riskState.getDailyLossLimit();
        BigDecimal currentDailyPnL = riskState.getCurrentDailyPnL();
        
        if (currentDailyPnL.compareTo(BigDecimal.ZERO) < 0 && 
            currentDailyPnL.abs().compareTo(dailyLossLimit) > 0) {
            return true;
        }
        
        // Check if maximum drawdown exceeded
        BigDecimal maxDrawdownLimit = riskState.getMaxDrawdownLimit();
        BigDecimal currentDrawdown = riskState.getCurrentDrawdown();
        
        if (currentDrawdown.compareTo(maxDrawdownLimit) > 0) {
            return true;
        }
        
        return false;
    }

    /**
     * Trigger emergency exit for a user
     */
    public void triggerEmergencyExit(String userId, String reason) {
        UserRiskState riskState = getUserRiskState(userId);
        if (riskState == null) {
            log.warn("Cannot trigger emergency exit for user {}: state not found", userId);
            return;
        }

        // Set emergency exit flag
        riskState.setEmergencyExit(true);
        riskState.setEmergencyExitReason(reason);
        riskState.setEmergencyExitTime(Instant.now());
        
        // Save updated state
        riskStateRepository.save(riskState);
        userRiskStates.put(userId, riskState);
        
        emergencyExits.incrementAndGet();
        
        log.warn("Emergency exit triggered for user {}: {}", userId, reason);
        
        // Publish emergency exit event
        publishEmergencyExit(userId, reason);
    }

    /**
     * Reset daily risk metrics (called at start of trading day)
     */
    @Scheduled(cron = "0 0 0 * * *") // Midnight every day
    public void resetDailyRiskMetrics() {
        log.info("Resetting daily risk metrics for all users");
        
        for (UserRiskState riskState : userRiskStates.values()) {
            // Reset daily P&L
            riskState.setCurrentDailyPnL(BigDecimal.ZERO);
            
            // Reset daily trade count
            riskState.setDailyTradeCount(0);
            
            // Clear emergency exit if active
            if (riskState.isEmergencyExit()) {
                riskState.setEmergencyExit(false);
                riskState.setEmergencyExitReason(null);
                riskState.setEmergencyExitTime(null);
            }
            
            // Save updated state
            riskStateRepository.save(riskState);
        }
    }

    /**
     * Get risk statistics
     */
    public RiskStatistics getStatistics() {
        return RiskStatistics.builder()
                .riskEvaluations(riskEvaluations.get())
                .riskViolations(riskViolations.get())
                .emergencyExits(emergencyExits.get())
                .activeUsers(userRiskStates.size())
                .usersInEmergencyExit((int) userRiskStates.values().stream()
                        .filter(UserRiskState::isEmergencyExit)
                        .count())
                .build();
    }

    // Private helper methods
    private UserRiskState getUserRiskState(String userId) {
        return userRiskStates.computeIfAbsent(userId, k -> {
            return riskStateRepository.findByUserId(userId).orElse(null);
        });
    }

    private RiskViolation checkDailyLossLimit(UserRiskState riskState) {
        BigDecimal dailyLossLimit = riskState.getDailyLossLimit();
        BigDecimal currentDailyPnL = riskState.getCurrentDailyPnL();
        
        if (currentDailyPnL.compareTo(BigDecimal.ZERO) < 0 && 
            currentDailyPnL.abs().compareTo(dailyLossLimit) > 0) {
            return RiskViolation.builder()
                    .ruleType("DAILY_LOSS_LIMIT")
                    .severity("HIGH")
                    .message(String.format("Daily loss limit exceeded: %.2f/%.2f", 
                                         currentDailyPnL.abs(), dailyLossLimit))
                    .currentValue(currentDailyPnL.abs())
                    .limitValue(dailyLossLimit)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return null;
    }

    private RiskViolation checkMaxDrawdown(UserRiskState riskState) {
        BigDecimal maxDrawdownLimit = riskState.getMaxDrawdownLimit();
        BigDecimal currentDrawdown = riskState.getCurrentDrawdown();
        
        if (currentDrawdown.compareTo(maxDrawdownLimit) > 0) {
            return RiskViolation.builder()
                    .ruleType("MAX_DRAWDOWN")
                    .severity("HIGH")
                    .message(String.format("Maximum drawdown exceeded: %.2f/%.2f", 
                                         currentDrawdown, maxDrawdownLimit))
                    .currentValue(currentDrawdown)
                    .limitValue(maxDrawdownLimit)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return null;
    }

    private RiskViolation checkMaxPositionSize(UserRiskState riskState, String symbol, BigDecimal quantity) {
        BigDecimal maxPositionSize = riskState.getMaxPositionSize();
        BigDecimal currentPosition = riskState.getPositions().getOrDefault(symbol, BigDecimal.ZERO);
        BigDecimal newPosition = currentPosition.add(quantity);
        
        if (newPosition.abs().compareTo(maxPositionSize) > 0) {
            return RiskViolation.builder()
                    .ruleType("MAX_POSITION_SIZE")
                    .severity("MEDIUM")
                    .message(String.format("Maximum position size exceeded for %s: %.2f/%.2f", 
                                         symbol, newPosition.abs(), maxPositionSize))
                    .currentValue(newPosition.abs())
                    .limitValue(maxPositionSize)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return null;
    }

    private RiskViolation checkRiskPerTrade(UserRiskState riskState, BigDecimal quantity, BigDecimal price) {
        BigDecimal maxRiskPerTrade = riskState.getMaxRiskPerTrade();
        BigDecimal tradeRisk = quantity.multiply(price);
        
        if (tradeRisk.compareTo(maxRiskPerTrade) > 0) {
            return RiskViolation.builder()
                    .ruleType("RISK_PER_TRADE")
                    .severity("MEDIUM")
                    .message(String.format("Risk per trade exceeded: %.2f/%.2f", 
                                         tradeRisk, maxRiskPerTrade))
                    .currentValue(tradeRisk)
                    .limitValue(maxRiskPerTrade)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return null;
    }

    private RiskViolation checkOverallExposure(UserRiskState riskState) {
        BigDecimal maxExposure = riskState.getMaxExposure();
        BigDecimal currentExposure = riskState.getTotalExposure();
        
        if (currentExposure.compareTo(maxExposure) > 0) {
            return RiskViolation.builder()
                    .ruleType("MAX_EXPOSURE")
                    .severity("HIGH")
                    .message(String.format("Maximum exposure exceeded: %.2f/%.2f", 
                                         currentExposure, maxExposure))
                    .currentValue(currentExposure)
                    .limitValue(maxExposure)
                    .timestamp(Instant.now())
                    .build();
        }
        
        return null;
    }

    private RiskViolation checkPositionConcentration(UserRiskState riskState, String symbol, BigDecimal quantity) {
        // Simple implementation - check if position in one symbol exceeds 50% of total exposure
        BigDecimal tradeExposure = quantity.multiply(getCurrentPrice(symbol));
        BigDecimal totalExposure = riskState.getTotalExposure();
        
        if (totalExposure.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal concentrationRatio = tradeExposure.divide(totalExposure, 4, BigDecimal.ROUND_HALF_UP);
            
            if (concentrationRatio.compareTo(BigDecimal.valueOf(0.5)) > 0) {
                return RiskViolation.builder()
                        .ruleType("POSITION_CONCENTRATION")
                        .severity("MEDIUM")
                        .message(String.format("Position concentration exceeded for %s: %.2f%%", 
                                             symbol, concentrationRatio.multiply(BigDecimal.valueOf(100))))
                        .currentValue(concentrationRatio)
                        .limitValue(BigDecimal.valueOf(0.5))
                        .timestamp(Instant.now())
                        .build();
            }
        }
        
        return null;
    }

    private RiskViolation checkSectorExposure(UserRiskState riskState, String symbol) {
        // Simple implementation - could be enhanced with sector mapping
        // For now, just return null (no sector limit)
        return null;
    }

    private BigDecimal getCurrentPrice(String symbol) {
        // In a real implementation, this would get the current market price
        // For now, return a placeholder value
        return BigDecimal.valueOf(100);
    }

    private void initializeDefaultRiskRules() {
        // Initialize default risk rules
        riskRules.put("DAILY_LOSS_LIMIT", new DailyLossLimitRule());
        riskRules.put("MAX_DRAWDOWN", new MaxDrawdownRule());
        riskRules.put("MAX_POSITION_SIZE", new MaxPositionSizeRule());
        riskRules.put("RISK_PER_TRADE", new RiskPerTradeRule());
        riskRules.put("MAX_EXPOSURE", new MaxExposureRule());
    }

    private void publishRiskEvaluation(String userId, String symbol, BigDecimal quantity, 
                                  BigDecimal price, List<RiskViolation> violations) {
        try {
            // Publish to Kafka for downstream processing
            Map<String, Object> riskEvent = Map.of(
                    "userId", userId,
                    "symbol", symbol,
                    "quantity", quantity,
                    "price", price,
                    "violations", violations,
                    "timestamp", Instant.now()
            );
            
            riskKafkaTemplate.send("risk-evaluations", userId, riskEvent);
        } catch (Exception e) {
            log.error("Error publishing risk evaluation", e);
        }
    }

    private void publishEmergencyExit(String userId, String reason) {
        try {
            // Publish to Kafka for downstream processing
            Map<String, Object> emergencyEvent = Map.of(
                    "userId", userId,
                    "reason", reason,
                    "timestamp", Instant.now()
            );
            
            riskKafkaTemplate.send("emergency-exits", userId, emergencyEvent);
        } catch (Exception e) {
            log.error("Error publishing emergency exit", e);
        }
    }

    /**
     * Statistics for risk management
     */
    @lombok.Builder
    @lombok.Data
    public static class RiskStatistics {
        private long riskEvaluations;
        private long riskViolations;
        private long emergencyExits;
        private int activeUsers;
        private int usersInEmergencyExit;
    }

    /**
     * Interface for risk rules
     */
    private interface RiskRule {
        String getName();
        String getDescription();
    }

    /**
     * Daily loss limit rule
     */
    private static class DailyLossLimitRule implements RiskRule {
        @Override
        public String getName() {
            return "Daily Loss Limit";
        }

        @Override
        public String getDescription() {
            return "Limits the maximum loss per trading day";
        }
    }

    /**
     * Maximum drawdown rule
     */
    private static class MaxDrawdownRule implements RiskRule {
        @Override
        public String getName() {
            return "Maximum Drawdown";
        }

        @Override
        public String getDescription() {
            return "Limits the maximum drawdown from peak equity";
        }
    }

    /**
     * Maximum position size rule
     */
    private static class MaxPositionSizeRule implements RiskRule {
        @Override
        public String getName() {
            return "Maximum Position Size";
        }

        @Override
        public String getDescription() {
            return "Limits the maximum position size per symbol";
        }
    }

    /**
     * Risk per trade rule
     */
    private static class RiskPerTradeRule implements RiskRule {
        @Override
        public String getName() {
            return "Risk Per Trade";
        }

        @Override
        public String getDescription() {
            return "Limits the maximum risk per individual trade";
        }
    }

    /**
     * Maximum exposure rule
     */
    private static class MaxExposureRule implements RiskRule {
        @Override
        public String getName() {
            return "Maximum Exposure";
        }

        @Override
        public String getDescription() {
            return "Limits the maximum total exposure across all positions";
        }
    }
}