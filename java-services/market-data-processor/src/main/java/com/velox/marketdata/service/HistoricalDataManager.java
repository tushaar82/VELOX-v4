package com.velox.marketdata.service;

import com.velox.marketdata.model.CandleData;
import com.velox.marketdata.model.TickData;
import com.velox.marketdata.repository.CandleRepository;
import com.velox.marketdata.repository.MarketDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Historical data management service.
 * Provides efficient access to historical market data with caching and optimization.
 */
@Service
@Slf4j
public class HistoricalDataManager {

    private final MarketDataRepository marketDataRepository;
    private final CandleRepository candleRepository;

    @Value("${historical.data.cache.ttl.seconds:300}")
    private long cacheTTLSeconds;

    @Value("${historical.data.max.records.per.query:10000}")
    private int maxRecordsPerQuery;

    @Autowired
    public HistoricalDataManager(
            MarketDataRepository marketDataRepository,
            CandleRepository candleRepository) {
        this.marketDataRepository = marketDataRepository;
        this.candleRepository = candleRepository;
    }

    /**
     * Get tick data for symbol and time range
     */
    @Async
    public CompletableFuture<List<TickData>> getTickData(String symbol, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching tick data for {} from {} to {}", symbol, startTime, endTime);
                
                // Use optimized query with pagination
                Pageable pageable = PageRequest.of(0, maxRecordsPerQuery);
                List<TickData> tickData = marketDataRepository.findBySymbolAndTimestampBetween(
                        symbol, startTime, endTime, pageable);
                
                log.debug("Retrieved {} tick records for {}", tickData.size(), symbol);
                return tickData;
                
            } catch (Exception e) {
                log.error("Error fetching tick data for {}", symbol, e);
                return List.of();
            }
        });
    }

    /**
     * Get tick data for symbol, exchange, and time range
     */
    @Async
    public CompletableFuture<List<TickData>> getTickData(String symbol, String exchange, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching tick data for {}:{} from {} to {}", symbol, exchange, startTime, endTime);
                
                // Use optimized query with pagination
                Pageable pageable = PageRequest.of(0, maxRecordsPerQuery);
                List<TickData> tickData = marketDataRepository.findBySymbolAndExchangeAndTimestampBetween(
                        symbol, exchange, startTime, endTime, pageable);
                
                log.debug("Retrieved {} tick records for {}:{}", tickData.size(), symbol, exchange);
                return tickData;
                
            } catch (Exception e) {
                log.error("Error fetching tick data for {}:{}", symbol, exchange, e);
                return List.of();
            }
        });
    }

    /**
     * Get latest tick data for symbol
     */
    @Async
    public CompletableFuture<TickData> getLatestTickData(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching latest tick data for {}", symbol);
                
                return marketDataRepository.findTopBySymbolOrderByTimestampDesc(symbol).orElse(null);
                
            } catch (Exception e) {
                log.error("Error fetching latest tick data for {}", symbol, e);
                return null;
            }
        });
    }

    /**
     * Get latest tick data for symbol and exchange
     */
    @Async
    public CompletableFuture<TickData> getLatestTickData(String symbol, String exchange) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching latest tick data for {}:{}", symbol, exchange);
                
                return marketDataRepository.findTopBySymbolAndExchangeOrderByTimestampDesc(symbol, exchange).orElse(null);
                
            } catch (Exception e) {
                log.error("Error fetching latest tick data for {}:{}", symbol, exchange, e);
                return null;
            }
        });
    }

    /**
     * Get candle data for symbol, timeframe, and time range
     */
    @Async
    public CompletableFuture<List<CandleData>> getCandleData(String symbol, CandleData.Timeframe timeframe, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching candle data for {}:{} from {} to {}", symbol, timeframe, startTime, endTime);
                
                // Use optimized query with pagination
                Pageable pageable = PageRequest.of(0, maxRecordsPerQuery);
                List<CandleData> candleData = candleRepository.findBySymbolAndTimeframeAndTimestampBetween(
                        symbol, timeframe, startTime, endTime, pageable);
                
                log.debug("Retrieved {} candle records for {}:{}", candleData.size(), symbol, timeframe);
                return candleData;
                
            } catch (Exception e) {
                log.error("Error fetching candle data for {}:{}", symbol, timeframe, e);
                return List.of();
            }
        });
    }

    /**
     * Get candle data for symbol, exchange, timeframe, and time range
     */
    @Async
    public CompletableFuture<List<CandleData>> getCandleData(String symbol, String exchange, CandleData.Timeframe timeframe, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching candle data for {}:{}:{} from {} to {}", symbol, exchange, timeframe, startTime, endTime);
                
                // Use optimized query with pagination
                Pageable pageable = PageRequest.of(0, maxRecordsPerQuery);
                List<CandleData> candleData = candleRepository.findBySymbolAndExchangeAndTimeframeAndTimestampBetween(
                        symbol, exchange, timeframe, startTime, endTime, pageable);
                
                log.debug("Retrieved {} candle records for {}:{}:{}", candleData.size(), symbol, exchange, timeframe);
                return candleData;
                
            } catch (Exception e) {
                log.error("Error fetching candle data for {}:{}:{}", symbol, exchange, timeframe, e);
                return List.of();
            }
        });
    }

    /**
     * Get latest candle data for symbol and timeframe
     */
    @Async
    public CompletableFuture<CandleData> getLatestCandleData(String symbol, CandleData.Timeframe timeframe) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching latest candle data for {}:{}", symbol, timeframe);
                
                return candleRepository.findTopBySymbolAndTimeframeOrderByTimestampDesc(symbol, timeframe).orElse(null);
                
            } catch (Exception e) {
                log.error("Error fetching latest candle data for {}:{}", symbol, timeframe, e);
                return null;
            }
        });
    }

    /**
     * Get latest candle data for symbol, exchange, and timeframe
     */
    @Async
    public CompletableFuture<CandleData> getLatestCandleData(String symbol, String exchange, CandleData.Timeframe timeframe) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching latest candle data for {}:{}:{}", symbol, exchange, timeframe);
                
                return candleRepository.findTopBySymbolAndExchangeAndTimeframeOrderByTimestampDesc(symbol, exchange, timeframe).orElse(null);
                
            } catch (Exception e) {
                log.error("Error fetching latest candle data for {}:{}:{}", symbol, exchange, timeframe, e);
                return null;
            }
        });
    }

    /**
     * Get latest N candles for symbol and timeframe
     */
    @Async
    public CompletableFuture<List<CandleData>> getLatestCandles(String symbol, CandleData.Timeframe timeframe, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching latest {} candles for {}:{}", limit, symbol, timeframe);
                
                return candleRepository.findLatestBySymbolAndTimeframe(symbol, timeframe, limit);
                
            } catch (Exception e) {
                log.error("Error fetching latest candles for {}:{}", symbol, timeframe, e);
                return List.of();
            }
        });
    }

    /**
     * Get tick statistics for symbol and time range
     */
    @Async
    public CompletableFuture<TickStatistics> getTickStatistics(String symbol, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Calculating tick statistics for {} from {} to {}", symbol, startTime, endTime);
                
                Object[] stats = marketDataRepository.getTickStatistics(symbol, startTime, endTime);
                
                if (stats != null && stats.length >= 6) {
                    return TickStatistics.builder()
                            .symbol(symbol)
                            .startTime(startTime)
                            .endTime(endTime)
                            .minPrice((java.math.BigDecimal) stats[0])
                            .maxPrice((java.math.BigDecimal) stats[1])
                            .avgPrice((java.math.BigDecimal) stats[2])
                            .totalVolume((Long) stats[3])
                            .totalValue((java.math.BigDecimal) stats[4])
                            .tickCount((Long) stats[5])
                            .build();
                } else {
                    return TickStatistics.builder()
                            .symbol(symbol)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
                }
                
            } catch (Exception e) {
                log.error("Error calculating tick statistics for {}", symbol, e);
                return TickStatistics.builder()
                            .symbol(symbol)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
            }
        });
    }

    /**
     * Get candle statistics for symbol, timeframe, and time range
     */
    @Async
    public CompletableFuture<CandleStatistics> getCandleStatistics(String symbol, CandleData.Timeframe timeframe, Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Calculating candle statistics for {}:{} from {} to {}", symbol, timeframe, startTime, endTime);
                
                Object[] stats = candleRepository.getCandleStatistics(symbol, timeframe, startTime, endTime);
                
                if (stats != null && stats.length >= 6) {
                    return CandleStatistics.builder()
                            .symbol(symbol)
                            .timeframe(timeframe)
                            .startTime(startTime)
                            .endTime(endTime)
                            .minPrice((java.math.BigDecimal) stats[0])
                            .maxPrice((java.math.BigDecimal) stats[1])
                            .avgPrice((java.math.BigDecimal) stats[2])
                            .totalVolume((Long) stats[3])
                            .totalValue((java.math.BigDecimal) stats[4])
                            .candleCount((Long) stats[5])
                            .build();
                } else {
                    return CandleStatistics.builder()
                            .symbol(symbol)
                            .timeframe(timeframe)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
                }
                
            } catch (Exception e) {
                log.error("Error calculating candle statistics for {}:{}", symbol, timeframe, e);
                return CandleStatistics.builder()
                            .symbol(symbol)
                            .timeframe(timeframe)
                            .startTime(startTime)
                            .endTime(endTime)
                            .build();
            }
        });
    }

    /**
     * Get available symbols
     */
    @Async
    public CompletableFuture<List<String>> getAvailableSymbols() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching available symbols");
                
                return marketDataRepository.findAllDistinctSymbols();
                
            } catch (Exception e) {
                log.error("Error fetching available symbols", e);
                return List.of();
            }
        });
    }

    /**
     * Get available exchanges for symbol
     */
    @Async
    public CompletableFuture<List<String>> getAvailableExchanges(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching available exchanges for {}", symbol);
                
                return marketDataRepository.findDistinctExchangesBySymbol(symbol);
                
            } catch (Exception e) {
                log.error("Error fetching available exchanges for {}", symbol, e);
                return List.of();
            }
        });
    }

    /**
     * Get available timeframes for symbol
     */
    @Async
    public CompletableFuture<List<CandleData.Timeframe>> getAvailableTimeframes(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Fetching available timeframes for {}", symbol);
                
                return candleRepository.findDistinctTimeframesBySymbol(symbol);
                
            } catch (Exception e) {
                log.error("Error fetching available timeframes for {}", symbol, e);
                return List.of();
            }
        });
    }

    /**
     * Cleanup old data
     */
    @Scheduled(cron = "0 0 2 * *") // Every 2 hours
    public void cleanupOldData() {
        try {
            log.info("Starting cleanup of old historical data");
            
            // Calculate cutoff time (30 days ago)
            Instant cutoffTime = Instant.now().minusSeconds(30 * 24 * 60 * 60);
            
            // Delete old tick data
            int deletedTicks = marketDataRepository.deleteByTimestampBefore(cutoffTime);
            log.info("Deleted {} old tick records", deletedTicks);
            
            // Delete old candle data
            int deletedCandles = candleRepository.deleteByTimestampBefore(cutoffTime);
            log.info("Deleted {} old candle records", deletedCandles);
            
            log.info("Completed cleanup of old historical data");
            
        } catch (Exception e) {
            log.error("Error during cleanup of old historical data", e);
        }
    }

    /**
     * Get data availability information
     */
    @Async
    public CompletableFuture<DataAvailability> getDataAvailability(String symbol, CandleData.Timeframe timeframe) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Checking data availability for {}:{}", symbol, timeframe);
                
                // Get earliest and latest timestamps
                Instant minTimestamp = candleRepository.findMinTimestampBySymbolAndTimeframe(symbol, timeframe);
                Instant maxTimestamp = candleRepository.findMaxTimestampBySymbolAndTimeframe(symbol, timeframe);
                
                return DataAvailability.builder()
                        .symbol(symbol)
                        .timeframe(timeframe)
                        .earliestTimestamp(minTimestamp)
                        .latestTimestamp(maxTimestamp)
                        .hasData(minTimestamp != null && maxTimestamp != null)
                        .build();
                
            } catch (Exception e) {
                log.error("Error checking data availability for {}:{}", symbol, timeframe, e);
                return DataAvailability.builder()
                        .symbol(symbol)
                        .timeframe(timeframe)
                        .hasData(false)
                        .build();
            }
        });
    }

    /**
     * Tick statistics class
     */
    @lombok.Builder
    @lombok.Data
    public static class TickStatistics {
        private String symbol;
        private Instant startTime;
        private Instant endTime;
        private java.math.BigDecimal minPrice;
        private java.math.BigDecimal maxPrice;
        private java.math.BigDecimal avgPrice;
        private Long totalVolume;
        private java.math.BigDecimal totalValue;
        private Long tickCount;
    }

    /**
     * Candle statistics class
     */
    @lombok.Builder
    @lombok.Data
    public static class CandleStatistics {
        private String symbol;
        private CandleData.Timeframe timeframe;
        private Instant startTime;
        private Instant endTime;
        private java.math.BigDecimal minPrice;
        private java.math.BigDecimal maxPrice;
        private java.math.BigDecimal avgPrice;
        private Long totalVolume;
        private java.math.BigDecimal totalValue;
        private Long candleCount;
    }

    /**
     * Data availability class
     */
    @lombok.Builder
    @lombok.Data
    public static class DataAvailability {
        private String symbol;
        private CandleData.Timeframe timeframe;
        private Instant earliestTimestamp;
        private Instant latestTimestamp;
        private boolean hasData;
    }
}