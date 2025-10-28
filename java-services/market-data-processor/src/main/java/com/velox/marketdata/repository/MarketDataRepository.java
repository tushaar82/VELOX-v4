package com.velox.marketdata.repository;

import com.velox.marketdata.model.TickData;
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
 * Repository interface for TickData entity.
 * Provides optimized queries for high-frequency market data operations.
 */
@Repository
public interface MarketDataRepository extends JpaRepository<TickData, Long> {

    /**
     * Find the latest tick for a symbol
     */
    Optional<TickData> findTopBySymbolOrderByTimestampDesc(String symbol);

    /**
     * Find the latest tick for a symbol and exchange
     */
    Optional<TickData> findTopBySymbolAndExchangeOrderByTimestampDesc(String symbol, String exchange);

    /**
     * Find ticks by symbol and time range
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.timestamp BETWEEN :startTime AND :endTime ORDER BY t.timestamp ASC")
    List<TickData> findBySymbolAndTimestampBetween(@Param("symbol") String symbol, 
                                                 @Param("startTime") Instant startTime, 
                                                 @Param("endTime") Instant endTime);

    /**
     * Find ticks by symbol, exchange, and time range
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.exchange = :exchange AND t.timestamp BETWEEN :startTime AND :endTime ORDER BY t.timestamp ASC")
    List<TickData> findBySymbolAndExchangeAndTimestampBetween(@Param("symbol") String symbol, 
                                                           @Param("exchange") String exchange,
                                                           @Param("startTime") Instant startTime, 
                                                           @Param("endTime") Instant endTime);

    /**
     * Find ticks by symbol and time range with pagination
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.timestamp BETWEEN :startTime AND :endTime ORDER BY t.timestamp ASC")
    Page<TickData> findBySymbolAndTimestampBetween(@Param("symbol") String symbol, 
                                                 @Param("startTime") Instant startTime, 
                                                 @Param("endTime") Instant endTime,
                                                 Pageable pageable);

    /**
     * Find ticks after a specific timestamp for a symbol
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.timestamp > :timestamp ORDER BY t.timestamp ASC")
    List<TickData> findBySymbolAndTimestampAfter(@Param("symbol") String symbol, 
                                               @Param("timestamp") Instant timestamp);

    /**
     * Find ticks after a specific timestamp for a symbol with limit
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.timestamp > :timestamp ORDER BY t.timestamp ASC LIMIT :limit")
    List<TickData> findBySymbolAndTimestampAfterWithLimit(@Param("symbol") String symbol, 
                                                         @Param("timestamp") Instant timestamp,
                                                         @Param("limit") int limit);

    /**
     * Find ticks by quality
     */
    List<TickData> findByQuality(TickData.TickQuality quality);

    /**
     * Find ticks by symbol and quality
     */
    List<TickData> findBySymbolAndQuality(String symbol, TickData.TickQuality quality);

    /**
     * Count ticks by symbol and time range
     */
    @Query("SELECT COUNT(t) FROM TickData t WHERE t.symbol = :symbol AND t.timestamp BETWEEN :startTime AND :endTime")
    long countBySymbolAndTimestampBetween(@Param("symbol") String symbol, 
                                        @Param("startTime") Instant startTime, 
                                        @Param("endTime") Instant endTime);

    /**
     * Get the maximum timestamp for a symbol
     */
    @Query("SELECT MAX(t.timestamp) FROM TickData t WHERE t.symbol = :symbol")
    Instant findMaxTimestampBySymbol(@Param("symbol") String symbol);

    /**
     * Get the minimum timestamp for a symbol
     */
    @Query("SELECT MIN(t.timestamp) FROM TickData t WHERE t.symbol = :symbol")
    Instant findMinTimestampBySymbol(@Param("symbol") String symbol);

    /**
     * Find ticks with sequence number greater than specified value
     */
    List<TickData> findBySymbolAndSequenceNumberGreaterThan(String symbol, Long sequenceNumber);

    /**
     * Find ticks with sequence number greater than specified value with limit
     */
    @Query("SELECT t FROM TickData t WHERE t.symbol = :symbol AND t.sequenceNumber > :sequenceNumber ORDER BY t.sequenceNumber ASC LIMIT :limit")
    List<TickData> findBySymbolAndSequenceNumberGreaterThanWithLimit(@Param("symbol") String symbol, 
                                                                   @Param("sequenceNumber") Long sequenceNumber,
                                                                   @Param("limit") int limit);

    /**
     * Delete old ticks before a specific timestamp
     */
    @Modifying
    @Query("DELETE FROM TickData t WHERE t.timestamp < :timestamp")
    int deleteByTimestampBefore(@Param("timestamp") Instant timestamp);

    /**
     * Delete old ticks for a symbol before a specific timestamp
     */
    @Modifying
    @Query("DELETE FROM TickData t WHERE t.symbol = :symbol AND t.timestamp < :timestamp")
    int deleteBySymbolAndTimestampBefore(@Param("symbol") String symbol, 
                                       @Param("timestamp") Instant timestamp);

    /**
     * Get tick statistics for a symbol and time range
     */
    @Query("SELECT " +
           "MIN(t.lastPrice) as minPrice, " +
           "MAX(t.lastPrice) as maxPrice, " +
           "AVG(t.lastPrice) as avgPrice, " +
           "SUM(t.volume) as totalVolume, " +
           "SUM(t.value) as totalValue, " +
           "COUNT(t) as tickCount " +
           "FROM TickData t " +
           "WHERE t.symbol = :symbol AND t.timestamp BETWEEN :startTime AND :endTime")
    Object[] getTickStatistics(@Param("symbol") String symbol, 
                             @Param("startTime") Instant startTime, 
                             @Param("endTime") Instant endTime);

    /**
     * Find all distinct symbols
     */
    @Query("SELECT DISTINCT t.symbol FROM TickData t")
    List<String> findAllDistinctSymbols();

    /**
     * Find all distinct exchanges for a symbol
     */
    @Query("SELECT DISTINCT t.exchange FROM TickData t WHERE t.symbol = :symbol")
    List<String> findDistinctExchangesBySymbol(@Param("symbol") String symbol);

    /**
     * Find ticks by source
     */
    List<TickData> findBySource(String source);

    /**
     * Find ticks by symbol and source
     */
    List<TickData> findBySymbolAndSource(String symbol, String source);

    /**
     * Bulk insert ticks for performance
     */
    @Modifying
    @Query(value = "INSERT INTO tick_data (symbol, exchange, last_price, bid_price, ask_price, bid_volume, ask_volume, volume, value, open_price, high_price, low_price, close_price, trade_count, timestamp, sequence_number, source, quality, created_at, updated_at) VALUES " +
           "(:#{#ticks[0].symbol}, :#{#ticks[0].exchange}, :#{#ticks[0].lastPrice}, :#{#ticks[0].bidPrice}, :#{#ticks[0].askPrice}, :#{#ticks[0].bidVolume}, :#{#ticks[0].askVolume}, :#{#ticks[0].volume}, :#{#ticks[0].value}, :#{#ticks[0].openPrice}, :#{#ticks[0].highPrice}, :#{#ticks[0].lowPrice}, :#{#ticks[0].closePrice}, :#{#ticks[0].tradeCount}, :#{#ticks[0].timestamp}, :#{#ticks[0].sequenceNumber}, :#{#ticks[0].source}, :#{#ticks[0].quality}, :#{#ticks[0].createdAt}, :#{#ticks[0].updatedAt})", 
           nativeQuery = true)
    void bulkInsertTick(@Param("ticks") List<TickData> ticks);
}