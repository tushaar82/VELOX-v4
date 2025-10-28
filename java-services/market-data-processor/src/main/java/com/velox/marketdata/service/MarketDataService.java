package com.velox.marketdata.service;

import com.velox.marketdata.model.CandleData;
import com.velox.marketdata.model.TickData;
import com.velox.marketdata.repository.CandleRepository;
import com.velox.marketdata.repository.MarketDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main service for market data operations.
 * Orchestrates tick processing, candle building, and data retrieval.
 */
@Service
@Slf4j
public class MarketDataService {

    private final SmartApiWebSocketClient webSocketClient;
    private final TickProcessor tickProcessor;
    private final CandleBuilder candleBuilder;
    private final MarketDataRepository marketDataRepository;
    private final CandleRepository candleRepository;

    @Value("${marketdata.symbols.default:NIFTY-50,BANKNIFTY-50,RELIANCE-EQ,TCS-EQ,INFY-EQ}")
    private List<String> defaultSymbols;

    @Value("${marketdata.exchanges.default:NSE}")
    private List<String> defaultExchanges;

    @Autowired
    public MarketDataService(
            SmartApiWebSocketClient webSocketClient,
            TickProcessor tickProcessor,
            CandleBuilder candleBuilder,
            MarketDataRepository marketDataRepository,
            CandleRepository candleRepository) {
        this.webSocketClient = webSocketClient;
        this.tickProcessor = tickProcessor;
        this.candleBuilder = candleBuilder;
        this.marketDataRepository = marketDataRepository;
        this.candleRepository = candleRepository;
    }

    /**
     * Initialize the market data service
     */
    public CompletableFuture<Void> initialize() {
        log.info("Initializing Market Data Service...");
        
        return webSocketClient.connect()
                .thenCompose(success -> {
                    if (success) {
                        return subscribeToDefaultSymbols();
                    } else {
                        return CompletableFuture.failedFuture(new RuntimeException("Failed to connect to WebSocket"));
                    }
                })
                .thenRun(() -> log.info("Market Data Service initialized successfully"))
                .exceptionally(throwable -> {
                    log.error("Failed to initialize Market Data Service", throwable);
                    return null;
                });
    }

    /**
     * Subscribe to default symbols
     */
    private CompletableFuture<Void> subscribeToDefaultSymbols() {
        log.info("Subscribing to default symbols: {}", defaultSymbols);
        
        List<CompletableFuture<Boolean>> subscriptionFutures = defaultSymbols.stream()
                .flatMap(symbol -> defaultExchanges.stream()
                        .map(exchange -> webSocketClient.subscribe(symbol, exchange)))
                .toList();

        return CompletableFuture.allOf(subscriptionFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> log.info("Subscribed to all default symbols"))
                .exceptionally(throwable -> {
                    log.error("Failed to subscribe to some symbols", throwable);
                    return null;
                });
    }

    /**
     * Subscribe to a symbol
     */
    public CompletableFuture<Boolean> subscribeSymbol(String symbol, String exchange) {
        log.info("Subscribing to symbol {} on exchange {}", symbol, exchange);
        return webSocketClient.subscribe(symbol, exchange);
    }

    /**
     * Unsubscribe from a symbol
     */
    public CompletableFuture<Boolean> unsubscribeSymbol(String symbol, String exchange) {
        log.info("Unsubscribing from symbol {} on exchange {}", symbol, exchange);
        return webSocketClient.unsubscribe(symbol, exchange);
    }

    /**
     * Get latest tick for a symbol
     */
    public TickData getLatestTick(String symbol) {
        return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol).orElse(null);
    }

    /**
     * Get latest tick for a symbol and exchange
     */
    public TickData getLatestTick(String symbol, String exchange) {
        return marketDataRepository.findTopBySymbolAndExchangeOrderByTimestampDesc(symbol, exchange).orElse(null);
    }

    /**
     * Get tick data for time range
     */
    public List<TickData> getTickData(String symbol, Instant startTime, Instant endTime) {
        return marketDataRepository.findBySymbolAndTimestampBetween(symbol, startTime, endTime);
    }

    /**
     * Get tick data for time range with pagination
     */
    public List<TickData> getTickData(String symbol, Instant startTime, Instant endTime, int limit) {
        return marketDataRepository.findBySymbolAndTimestampAfterWithLimit(symbol, startTime, limit);
    }

    /**
     * Get latest candle for a symbol and timeframe
     */
    public CandleData getLatestCandle(String symbol, CandleData.Timeframe timeframe) {
        return candleRepository.findTopBySymbolAndTimeframeOrderByTimestampDesc(symbol, timeframe).orElse(null);
    }

    /**
     * Get latest candle for a symbol, exchange, and timeframe
     */
    public CandleData getLatestCandle(String symbol, String exchange, CandleData.Timeframe timeframe) {
        return candleRepository.findTopBySymbolAndExchangeAndTimeframeOrderByTimestampDesc(symbol, exchange, timeframe).orElse(null);
    }

    /**
     * Get candle data for time range
     */
    public List<CandleData> getCandleData(String symbol, CandleData.Timeframe timeframe, Instant startTime, Instant endTime) {
        return candleRepository.findBySymbolAndTimeframeAndTimestampBetween(symbol, timeframe, startTime, endTime);
    }

    /**
     * Get latest N candles for a symbol and timeframe
     */
    public List<CandleData> getLatestCandles(String symbol, CandleData.Timeframe timeframe, int limit) {
        return candleRepository.findLatestBySymbolAndTimeframe(symbol, timeframe, limit);
    }

    /**
     * Get all subscribed symbols
     */
    public java.util.concurrent.ConcurrentHashMap<String, String> getSubscribedSymbols() {
        return webSocketClient.getSubscribedSymbols();
    }

    /**
     * Check if service is connected to market data source
     */
    public boolean isConnected() {
        return webSocketClient.isConnected();
    }

    /**
     * Get connection status and statistics
     */
    public MarketDataStatus getStatus() {
        return MarketDataStatus.builder()
                .connected(webSocketClient.isConnected())
                .subscribedSymbols(webSocketClient.getSubscribedSymbols().size())
                .tickProcessingStats(tickProcessor.getStats())
                .candleBuildingStats(candleBuilder.getStats())
                .build();
    }

    /**
     * Force flush all pending data
     */
    public void flushAllData() {
        log.info("Flushing all pending market data...");
        tickProcessor.flushBatchNow();
        candleBuilder.flushCandles();
        log.info("All pending data flushed");
    }

    /**
     * Cleanup old data
     */
    public void cleanupOldData() {
        log.info("Cleaning up old market data...");
        tickProcessor.cleanupOldTicks();
        candleBuilder.cleanupStaleCandles();
        log.info("Old data cleanup completed");
    }

    /**
     * Shutdown the market data service gracefully
     */
    public CompletableFuture<Void> shutdown() {
        log.info("Shutting down Market Data Service...");
        
        // Complete all forming candles
        candleBuilder.completeAllActiveCandles();
        
        // Flush all pending data
        flushAllData();
        
        // Disconnect from WebSocket
        webSocketClient.disconnect();
        
        log.info("Market Data Service shutdown completed");
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get market data statistics
     */
    public MarketDataStatistics getStatistics() {
        // Get tick statistics
        long totalTicks = marketDataRepository.count();
        List<String> symbols = marketDataRepository.findAllDistinctSymbols();
        
        // Get candle statistics
        long totalCandles = candleRepository.count();
        List<CandleData.Timeframe> timeframes = candleRepository.findDistinctTimeframesBySymbol(symbols.get(0));
        
        return MarketDataStatistics.builder()
                .totalTicks(totalTicks)
                .totalCandles(totalCandles)
                .symbolsCount(symbols.size())
                .symbols(symbols)
                .timeframes(timeframes)
                .connected(webSocketClient.isConnected())
                .subscribedSymbols(webSocketClient.getSubscribedSymbols().size())
                .tickProcessingStats(tickProcessor.getStats())
                .candleBuildingStats(candleBuilder.getStats())
                .build();
    }

    /**
     * Validate symbol and exchange
     */
    public boolean isValidSymbolExchange(String symbol, String exchange) {
        return symbol != null && !symbol.trim().isEmpty() &&
               exchange != null && !exchange.trim().isEmpty();
    }

    /**
     * Get supported timeframes
     */
    public List<CandleData.Timeframe> getSupportedTimeframes() {
        return candleBuilder.getSupportedTimeframes();
    }

    /**
     * Market data status information
     */
    @lombok.Builder
    @lombok.Data
    public static class MarketDataStatus {
        private boolean connected;
        private int subscribedSymbols;
        private TickProcessor.TickProcessingStats tickProcessingStats;
        private CandleBuilder.CandleBuildingStats candleBuildingStats;
    }

    /**
     * Market data statistics
     */
    @lombok.Builder
    @lombok.Data
    public static class MarketDataStatistics {
        private long totalTicks;
        private long totalCandles;
        private int symbolsCount;
        private List<String> symbols;
        private List<CandleData.Timeframe> timeframes;
        private boolean connected;
        private int subscribedSymbols;
        private TickProcessor.TickProcessingStats tickProcessingStats;
        private CandleBuilder.CandleBuildingStats candleBuildingStats;
    }
}