package com.velox.marketdata.service;

import com.velox.marketdata.model.TickData;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract interface for broker adapters.
 * Provides a common interface for interacting with different brokers.
 */
public interface BrokerAdapter {

    /**
     * Get broker name
     */
    String getBrokerName();

    /**
     * Connect to broker
     */
    CompletableFuture<Boolean> connect();

    /**
     * Disconnect from broker
     */
    void disconnect();

    /**
     * Check if connected to broker
     */
    boolean isConnected();

    /**
     * Subscribe to market data for symbols
     */
    CompletableFuture<Boolean> subscribeToMarketData(List<String> symbols, String exchange);

    /**
     * Unsubscribe from market data for symbols
     */
    CompletableFuture<Boolean> unsubscribeFromMarketData(List<String> symbols, String exchange);

    /**
     * Get subscribed symbols
     */
    List<String> getSubscribedSymbols();

    /**
     * Place an order
     */
    CompletableFuture<OrderResponse> placeOrder(OrderRequest orderRequest);

    /**
     * Modify an existing order
     */
    CompletableFuture<OrderResponse> modifyOrder(ModifyOrderRequest modifyRequest);

    /**
     * Cancel an existing order
     */
    CompletableFuture<OrderResponse> cancelOrder(String orderId);

    /**
     * Get order status
     */
    CompletableFuture<OrderStatus> getOrderStatus(String orderId);

    /**
     * Get open orders
     */
    CompletableFuture<List<Order>> getOpenOrders();

    /**
     * Get order history
     */
    CompletableFuture<List<Order>> getOrderHistory(String symbol, int limit);

    /**
     * Get positions
     */
    CompletableFuture<List<Position>> getPositions();

    /**
     * Get position for a specific symbol
     */
    CompletableFuture<Position> getPosition(String symbol);

    /**
     * Get account balance
     */
    CompletableFuture<AccountBalance> getAccountBalance();

    /**
     * Get trading limits
     */
    CompletableFuture<TradingLimits> getTradingLimits();

    /**
     * Get broker health status
     */
    CompletableFuture<BrokerHealth> getHealthStatus();

    /**
     * Order request data structure
     */
    interface OrderRequest {
        String getSymbol();
        String getExchange();
        OrderType getOrderType();
        OrderSide getSide();
        Integer getQuantity();
        Double getPrice();
        OrderValidity getValidity();
        String getProduct();
        Double getTriggerPrice();
        Double getStopLoss();
        Double getTakeProfit();
        String getTag();
    }

    /**
     * Modify order request data structure
     */
    interface ModifyOrderRequest {
        String getOrderId();
        Integer getQuantity();
        Double getPrice();
        Double getTriggerPrice();
        Double getStopLoss();
        Double getTakeProfit();
        OrderValidity getValidity();
    }

    /**
     * Order response data structure
     */
    interface OrderResponse {
        boolean isSuccess();
        String getOrderId();
        String getMessage();
        String getErrorCode();
        long getTimestamp();
    }

    /**
     * Order status data structure
     */
    interface OrderStatus {
        String getOrderId();
        OrderState getState();
        Double getFilledQuantity();
        Double getRemainingQuantity();
        Double getAveragePrice();
        long getLastUpdated();
    }

    /**
     * Order data structure
     */
    interface Order {
        String getOrderId();
        String getSymbol();
        String getExchange();
        OrderType getOrderType();
        OrderSide getSide();
        Integer getQuantity();
        Double getPrice();
        Double getTriggerPrice();
        Double getStopLoss();
        Double getTakeProfit();
        OrderState getState();
        Double getFilledQuantity();
        Double getRemainingQuantity();
        Double getAveragePrice();
        OrderValidity getValidity();
        String getProduct();
        long getOrderTimestamp();
        long getLastUpdated();
        String getTag();
    }

    /**
     * Position data structure
     */
    interface Position {
        String getSymbol();
        String getExchange();
        String getProduct();
        PositionType getPositionType();
        Double getQuantity();
        Double getAveragePrice();
        Double getCurrentPrice();
        Double getUnrealizedPnL();
        Double getRealizedPnL();
        Double getTotalPnL();
        long getLastUpdated();
    }

    /**
     * Account balance data structure
     */
    interface AccountBalance {
        Double getAvailableBalance();
        Double getUsedMargin();
        Double getAvailableMargin();
        Double getTotalBalance();
        String getCurrency();
        long getLastUpdated();
    }

    /**
     * Trading limits data structure
     */
    interface TradingLimits {
        Double getMaxOrderSize();
        Double getMaxPositionSize();
        Double getMaxOrdersPerDay();
        Double getMaxPositionValue();
        String getCurrency();
        long getLastUpdated();
    }

    /**
     * Broker health data structure
     */
    interface BrokerHealth {
        boolean isHealthy();
        String getStatus();
        long getResponseTime();
        long getLastUpdated();
        List<String> getActiveServices();
    }

    /**
     * Order types
     */
    enum OrderType {
        MARKET("MARKET"),
        LIMIT("LIMIT"),
        STOP_LOSS("STOP_LOSS"),
        STOP_LIMIT("STOP_LIMIT");

        private final String value;

        OrderType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Order sides
     */
    enum OrderSide {
        BUY("BUY"),
        SELL("SELL");

        private final String value;

        OrderSide(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Order states
     */
    enum OrderState {
        PENDING("PENDING"),
        OPEN("OPEN"),
        FILLED("FILLED"),
        CANCELLED("CANCELLED"),
        REJECTED("REJECTED"),
        EXPIRED("EXPIRED"),
        PARTIALLY_FILLED("PARTIALLY_FILLED");

        private final String value;

        OrderState(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Order validity
     */
    enum OrderValidity {
        DAY("DAY"),
        IOC("IOC"),
        GTC("GTC");

        private final String value;

        OrderValidity(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Position types
     */
    enum PositionType {
        LONG("LONG"),
        SHORT("SHORT"),
        NET("NET");

        private final String value;

        PositionType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}