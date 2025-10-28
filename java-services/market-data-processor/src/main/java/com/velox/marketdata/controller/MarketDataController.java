package com.velox.marketdata.controller;

import com.velox.marketdata.model.CandleData;
import com.velox.marketdata.model.TickData;
import com.velox.marketdata.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for market data operations.
 * Provides endpoints for accessing market data and managing subscriptions.
 */
@RestController
@RequestMapping("/api/v1/marketdata")
@Slf4j
public class MarketDataController {

    private final MarketDataService marketDataService;

    @Autowired
    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * Get service status
     */
    @GetMapping("/status")
    public ResponseEntity<MarketDataService.MarketDataStatus> getStatus() {
        return ResponseEntity.ok(marketDataService.getStatus());
    }

    /**
     * Get market data statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<MarketDataService.MarketDataStatistics> getStatistics() {
        return ResponseEntity.ok(marketDataService.getStatistics());
    }

    /**
     * Get latest tick for a symbol
     */
    @GetMapping("/ticks/{symbol}/latest")
    public ResponseEntity<TickData> getLatestTick(@PathVariable String symbol) {
        TickData tick = marketDataService.getLatestTick(symbol);
        return tick != null ? ResponseEntity.ok(tick) : ResponseEntity.notFound().build();
    }

    /**
     * Get latest tick for a symbol and exchange
     */
    @GetMapping("/ticks/{symbol}/{exchange}/latest")
    public ResponseEntity<TickData> getLatestTick(
            @PathVariable String symbol, 
            @PathVariable String exchange) {
        TickData tick = marketDataService.getLatestTick(symbol, exchange);
        return tick != null ? ResponseEntity.ok(tick) : ResponseEntity.notFound().build();
    }

    /**
     * Get tick data for time range
     */
    @GetMapping("/ticks/{symbol}")
    public ResponseEntity<List<TickData>> getTickData(
            @PathVariable String symbol,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        
        List<TickData> ticks = marketDataService.getTickData(symbol, startTime, endTime, limit);
        return ResponseEntity.ok(ticks);
    }

    /**
     * Get latest candle for a symbol and timeframe
     */
    @GetMapping("/candles/{symbol}/{timeframe}/latest")
    public ResponseEntity<CandleData> getLatestCandle(
            @PathVariable String symbol, 
            @PathVariable CandleData.Timeframe timeframe) {
        CandleData candle = marketDataService.getLatestCandle(symbol, timeframe);
        return candle != null ? ResponseEntity.ok(candle) : ResponseEntity.notFound().build();
    }

    /**
     * Get latest candle for a symbol, exchange, and timeframe
     */
    @GetMapping("/candles/{symbol}/{exchange}/{timeframe}/latest")
    public ResponseEntity<CandleData> getLatestCandle(
            @PathVariable String symbol,
            @PathVariable String exchange, 
            @PathVariable CandleData.Timeframe timeframe) {
        CandleData candle = marketDataService.getLatestCandle(symbol, exchange, timeframe);
        return candle != null ? ResponseEntity.ok(candle) : ResponseEntity.notFound().build();
    }

    /**
     * Get candle data for time range
     */
    @GetMapping("/candles/{symbol}/{timeframe}")
    public ResponseEntity<List<CandleData>> getCandleData(
            @PathVariable String symbol,
            @PathVariable CandleData.Timeframe timeframe,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
        
        List<CandleData> candles = marketDataService.getCandleData(symbol, timeframe, startTime, endTime);
        return ResponseEntity.ok(candles);
    }

    /**
     * Get latest N candles for a symbol and timeframe
     */
    @GetMapping("/candles/{symbol}/{timeframe}/latest/{limit}")
    public ResponseEntity<List<CandleData>> getLatestCandles(
            @PathVariable String symbol,
            @PathVariable CandleData.Timeframe timeframe,
            @PathVariable int limit) {
        
        List<CandleData> candles = marketDataService.getLatestCandles(symbol, timeframe, limit);
        return ResponseEntity.ok(candles);
    }

    /**
     * Subscribe to a symbol
     */
    @PostMapping("/subscribe/{symbol}/{exchange}")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> subscribeSymbol(
            @PathVariable String symbol, 
            @PathVariable String exchange) {
        
        if (!marketDataService.isValidSymbolExchange(symbol, exchange)) {
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(
                    SubscriptionResponse.builder()
                            .success(false)
                            .message("Invalid symbol or exchange")
                            .build()));
        }

        return marketDataService.subscribeSymbol(symbol, exchange)
                .thenApply(success -> ResponseEntity.ok(
                        SubscriptionResponse.builder()
                                .success(success)
                                .symbol(symbol)
                                .exchange(exchange)
                                .message(success ? "Subscription successful" : "Subscription failed")
                                .build()))
                .exceptionally(throwable -> ResponseEntity.internalServerError().body(
                        SubscriptionResponse.builder()
                                .success(false)
                                .message("Subscription error: " + throwable.getMessage())
                                .build()));
    }

    /**
     * Unsubscribe from a symbol
     */
    @PostMapping("/unsubscribe/{symbol}/{exchange}")
    public CompletableFuture<ResponseEntity<SubscriptionResponse>> unsubscribeSymbol(
            @PathVariable String symbol, 
            @PathVariable String exchange) {
        
        return marketDataService.unsubscribeSymbol(symbol, exchange)
                .thenApply(success -> ResponseEntity.ok(
                        SubscriptionResponse.builder()
                                .success(success)
                                .symbol(symbol)
                                .exchange(exchange)
                                .message(success ? "Unsubscription successful" : "Unsubscription failed")
                                .build()))
                .exceptionally(throwable -> ResponseEntity.internalServerError().body(
                        SubscriptionResponse.builder()
                                .success(false)
                                .message("Unsubscription error: " + throwable.getMessage())
                                .build()));
    }

    /**
     * Get all subscribed symbols
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<java.util.concurrent.ConcurrentHashMap<String, String>> getSubscriptions() {
        return ResponseEntity.ok(marketDataService.getSubscribedSymbols());
    }

    /**
     * Get supported timeframes
     */
    @GetMapping("/timeframes")
    public ResponseEntity<List<CandleData.Timeframe>> getSupportedTimeframes() {
        return ResponseEntity.ok(marketDataService.getSupportedTimeframes());
    }

    /**
     * Force flush all pending data
     */
    @PostMapping("/flush")
    public ResponseEntity<FlushResponse> flushData() {
        try {
            marketDataService.flushAllData();
            return ResponseEntity.ok(FlushResponse.builder()
                    .success(true)
                    .message("Data flushed successfully")
                    .build());
        } catch (Exception e) {
            log.error("Error flushing data", e);
            return ResponseEntity.internalServerError().body(FlushResponse.builder()
                    .success(false)
                    .message("Flush failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Cleanup old data
     */
    @PostMapping("/cleanup")
    public ResponseEntity<CleanupResponse> cleanupData() {
        try {
            marketDataService.cleanupOldData();
            return ResponseEntity.ok(CleanupResponse.builder()
                    .success(true)
                    .message("Data cleanup completed")
                    .build());
        } catch (Exception e) {
            log.error("Error cleaning up data", e);
            return ResponseEntity.internalServerError().body(CleanupResponse.builder()
                    .success(false)
                    .message("Cleanup failed: " + e.getMessage())
                    .build());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        boolean isHealthy = marketDataService.isConnected();
        return ResponseEntity.ok(HealthResponse.builder()
                .healthy(isHealthy)
                .status(isHealthy ? "UP" : "DOWN")
                .timestamp(Instant.now())
                .build());
    }

    /**
     * Response for subscription operations
     */
    @lombok.Builder
    @lombok.Data
    public static class SubscriptionResponse {
        private boolean success;
        private String symbol;
        private String exchange;
        private String message;
    }

    /**
     * Response for flush operations
     */
    @lombok.Builder
    @lombok.Data
    public static class FlushResponse {
        private boolean success;
        private String message;
    }

    /**
     * Response for cleanup operations
     */
    @lombok.Builder
    @lombok.Data
    public static class CleanupResponse {
        private boolean success;
        private String message;
    }

    /**
     * Response for health checks
     */
    @lombok.Builder
    @lombok.Data
    public static class HealthResponse {
        private boolean healthy;
        private String status;
        private Instant timestamp;
    }
}