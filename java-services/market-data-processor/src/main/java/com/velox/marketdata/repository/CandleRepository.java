package com.velox.marketdata.repository;

import com.velox.marketdata.model.CandleData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CandleData entity.
 * Provides optimized queries for candle data operations with TimescaleDB optimizations.
 */
@Repository
public interface CandleRepository extends JpaRepository<CandleData, Long> {

    /**
     * Find latest candle for a symbol and timeframe
     */
    Optional<CandleData> findTopBySymbolAndTimeframeOrderByTimestampDesc(String symbol, CandleData.Timeframe timeframe);

    /**
     * Find latest candle for a symbol, exchange, and timeframe
     */
    Optional<CandleData> findTopBySymbolAndExchangeAndTimeframeOrderByTimestampDesc(String symbol, String exchange, CandleData.Timeframe timeframe);

    /**
     * Find candles by symbol, timeframe, and time range
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp ASC")
    List<CandleData> findBySymbolAndTimeframeAndTimestampBetween(@Param("symbol") String symbol, 
                                                             @Param("timeframe") CandleData.Timeframe timeframe,
                                                             @Param("startTime") Instant startTime, 
                                                             @Param("endTime") Instant endTime);

    /**
     * Find candles by symbol, exchange, timeframe, and time range
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.exchange = :exchange AND c.timeframe = :timeframe AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp ASC")
    List<CandleData> findBySymbolAndExchangeAndTimeframeAndTimestampBetween(@Param("symbol") String symbol, 
                                                                           @Param("exchange") String exchange,
                                                                           @Param("timeframe") CandleData.Timeframe timeframe,
                                                                           @Param("startTime") Instant startTime, 
                                                                           @Param("endTime") Instant endTime);

    /**
     * Find candles by symbol, timeframe, and time range with pagination
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp BETWEEN :startTime AND :endTime ORDER BY c.timestamp ASC")
    Page<CandleData> findBySymbolAndTimeframeAndTimestampBetween(@Param("symbol") String symbol, 
                                                             @Param("timeframe") CandleData.Timeframe timeframe,
                                                             @Param("startTime") Instant startTime, 
                                                             @Param("endTime") Instant endTime,
                                                             Pageable pageable);

    /**
     * Find candles after a specific timestamp for a symbol and timeframe
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp > :timestamp ORDER BY c.timestamp ASC")
    List<CandleData> findBySymbolAndTimeframeAndTimestampAfter(@Param("symbol") String symbol, 
                                                             @Param("timeframe") CandleData.Timeframe timeframe,
                                                             @Param("timestamp") Instant timestamp);

    /**
     * Find candles after a specific timestamp for a symbol and timeframe with limit
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp > :timestamp ORDER BY c.timestamp ASC LIMIT :limit")
    List<CandleData> findBySymbolAndTimeframeAndTimestampAfterWithLimit(@Param("symbol") String symbol, 
                                                                     @Param("timeframe") CandleData.Timeframe timeframe,
                                                                     @Param("timestamp") Instant timestamp,
                                                                     @Param("limit") int limit);

    /**
     * Find latest N candles for a symbol and timeframe
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe ORDER BY c.timestamp DESC LIMIT :limit")
    List<CandleData> findLatestBySymbolAndTimeframe(@Param("symbol") String symbol, 
                                                     @Param("timeframe") CandleData.Timeframe timeframe,
                                                     @Param("limit") int limit);

    /**
     * Find candles by quality
     */
    List<CandleData> findByQuality(CandleData.CandleQuality quality);

    /**
     * Find candles by symbol, timeframe, and quality
     */
    List<CandleData> findBySymbolAndTimeframeAndQuality(String symbol, CandleData.Timeframe timeframe, CandleData.CandleQuality quality);

    /**
     * Find complete candles only
     */
    @Query("SELECT c FROM CandleData c WHERE c.isComplete = true AND c.symbol = :symbol AND c.timeframe = :timeframe ORDER BY c.timestamp DESC")
    List<CandleData> findCompleteBySymbolAndTimeframe(@Param("symbol") String symbol, 
                                                     @Param("timeframe") CandleData.Timeframe timeframe);

    /**
     * Count candles by symbol, timeframe, and time range
     */
    @Query("SELECT COUNT(c) FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp BETWEEN :startTime AND :endTime")
    long countBySymbolAndTimeframeAndTimestampBetween(@Param("symbol") String symbol, 
                                                   @Param("timeframe") CandleData.Timeframe timeframe,
                                                   @Param("startTime") Instant startTime, 
                                                   @Param("endTime") Instant endTime);

    /**
     * Get the maximum timestamp for a symbol and timeframe
     */
    @Query("SELECT MAX(c.timestamp) FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    Instant findMaxTimestampBySymbolAndTimeframe(@Param("symbol") String symbol, 
                                               @Param("timeframe") CandleData.Timeframe timeframe);

    /**
     * Get the minimum timestamp for a symbol and timeframe
     */
    @Query("SELECT MIN(c.timestamp) FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe")
    Instant findMinTimestampBySymbolAndTimeframe(@Param("symbol") String symbol, 
                                               @Param("timeframe") CandleData.Timeframe timeframe);

    /**
     * Delete old candles before a specific timestamp
     */
    @Modifying
    @Query("DELETE FROM CandleData c WHERE c.timestamp < :timestamp")
    int deleteByTimestampBefore(@Param("timestamp") Instant timestamp);

    /**
     * Delete old candles for a symbol and timeframe before a specific timestamp
     */
    @Modifying
    @Query("DELETE FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp < :timestamp")
    int deleteBySymbolAndTimeframeAndTimestampBefore(@Param("symbol") String symbol, 
                                                   @Param("timeframe") CandleData.Timeframe timeframe,
                                                   @Param("timestamp") Instant timestamp);

    /**
     * Get candle statistics for a symbol, timeframe, and time range
     */
    @Query("SELECT " +
           "MIN(c.lowPrice) as minPrice, " +
           "MAX(c.highPrice) as maxPrice, " +
           "AVG(c.closePrice) as avgPrice, " +
           "SUM(c.volume) as totalVolume, " +
           "SUM(c.value) as totalValue, " +
           "COUNT(c) as candleCount " +
           "FROM CandleData c " +
           "WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.timestamp BETWEEN :startTime AND :endTime")
    Object[] getCandleStatistics(@Param("symbol") String symbol, 
                               @Param("timeframe") CandleData.Timeframe timeframe,
                               @Param("startTime") Instant startTime, 
                               @Param("endTime") Instant endTime);

    /**
     * Find all distinct symbols
     */
    @Query("SELECT DISTINCT c.symbol FROM CandleData c")
    List<String> findAllDistinctSymbols();

    /**
     * Find all distinct timeframes for a symbol
     */
    @Query("SELECT DISTINCT c.timeframe FROM CandleData c WHERE c.symbol = :symbol")
    List<CandleData.Timeframe> findDistinctTimeframesBySymbol(@Param("symbol") String symbol);

    /**
     * Find all distinct exchanges for a symbol
     */
    @Query("SELECT DISTINCT c.exchange FROM CandleData c WHERE c.symbol = :symbol")
    List<String> findDistinctExchangesBySymbol(@Param("symbol") String symbol);

    /**
     * Find candles by source
     */
    List<CandleData> findBySource(String source);

    /**
     * Find candles by symbol and source
     */
    List<CandleData> findBySymbolAndSource(String symbol, String source);

    /**
     * Find candles with volume above threshold
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND c.volume > :volumeThreshold ORDER BY c.timestamp DESC")
    List<CandleData> findBySymbolAndTimeframeAndVolumeGreaterThan(@Param("symbol") String symbol, 
                                                              @Param("timeframe") CandleData.Timeframe timeframe,
                                                              @Param("volumeThreshold") Long volumeThreshold);

    /**
     * Find candles with high volatility (range > threshold)
     */
    @Query("SELECT c FROM CandleData c WHERE c.symbol = :symbol AND c.timeframe = :timeframe AND (c.highPrice - c.lowPrice) > :rangeThreshold ORDER BY c.timestamp DESC")
    List<CandleData> findBySymbolAndTimeframeAndRangeGreaterThan(@Param("symbol") String symbol, 
                                                               @Param("timeframe") CandleData.Timeframe timeframe,
                                                               @Param("rangeThreshold") java.math.BigDecimal rangeThreshold);

    /**
     * Bulk insert candles for performance
     */
    @Modifying
    @Query(value = "INSERT INTO candle_data (symbol, exchange, timeframe, timestamp, open_price, high_price, low_price, close_price, volume, value, trade_count, vwap, is_complete, tick_count, source, quality, created_at, updated_at) VALUES " +
           "(:#{#candles[0].symbol}, :#{#candles[0].exchange}, :#{#candles[0].timeframe}, :#{#candles[0].timestamp}, :#{#candles[0].openPrice}, :#{#candles[0].highPrice}, :#{#candles[0].lowPrice}, :#{#candles[0].closePrice}, :#{#candles[0].volume}, :#{#candles[0].value}, :#{#candles[0].tradeCount}, :#{#candles[0].vwap}, :#{#candles[0].isComplete}, :#{#candles[0].tickCount}, :#{#candles[0].source}, :#{#candles[0].quality}, :#{#candles[0].createdAt}, :#{#candles[0].updatedAt})", 
           nativeQuery = true)
    void bulkInsertCandles(@Param("candles") List<CandleData> candles);

    /**
     * Update candle completion status
     */
    @Modifying
    @Query("UPDATE CandleData c SET c.isComplete = true, c.updatedAt = :updateTime WHERE c.id = :candleId")
    int markCandleAsComplete(@Param("candleId") Long candleId, @Param("updateTime") Instant updateTime);

    /**
     * Find incomplete candles
     */
    @Query("SELECT c FROM CandleData c WHERE c.isComplete = false")
    List<CandleData> findIncompleteCandles();
}