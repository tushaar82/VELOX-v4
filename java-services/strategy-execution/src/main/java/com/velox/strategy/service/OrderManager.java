package com.velox.strategy.service;

import com.velox.marketdata.service.BrokerAdapter;
import com.velox.marketdata.service.BrokerAdapter.OrderRequest;
import com.velox.marketdata.service.BrokerAdapter.OrderResponse;
import com.velox.marketdata.service.BrokerAdapter.OrderState;
import com.velox.risk.strategy.SignalProcessor.OrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
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
 * Order management service.
 * Handles order placement, modification, cancellation, and tracking.
 */
@Service
@Slf4j
public class OrderManager {

    private final BrokerAdapter brokerAdapter;
    private final KafkaTemplate<String, Object> orderKafkaTemplate;
    private final Map<String, Order> activeOrders;
    private final Map<String, Order> orderHistory;
    private final Map<String, Instant> lastOrderTimes;

    @Value("${order.management.max.concurrent.orders:50}")
    private int maxConcurrentOrders;

    @Value("${order.management.order.timeout.ms:30000}")
    private long orderTimeoutMs;

    // Performance metrics
    private final AtomicLong ordersPlaced = new AtomicLong(0);
    private final AtomicLong ordersModified = new AtomicLong(0);
    private final AtomicLong ordersCancelled = new AtomicLong(0);
    private final AtomicLong ordersFilled = new AtomicLong(0);
    private final AtomicLong ordersRejected = new AtomicLong(0);

    @Autowired
    public OrderManager(BrokerAdapter brokerAdapter, KafkaTemplate<String, Object> orderKafkaTemplate) {
        this.brokerAdapter = brokerAdapter;
        this.orderKafkaTemplate = orderKafkaTemplate;
        this.activeOrders = new ConcurrentHashMap<>();
        this.orderHistory = new ConcurrentHashMap<>();
        this.lastOrderTimes = new ConcurrentHashMap<>();
    }

    /**
     * Place order
     */
    @Async
    public CompletableFuture<OrderResponse> placeOrder(OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String orderId = generateOrderId();
                String symbol = orderRequest.getSymbol();
                
                log.info("Placing order {} for symbol {}: {}", orderId, symbol, orderRequest);
                
                // Check concurrent order limit
                if (getConcurrentOrderCount(symbol) >= maxConcurrentOrders) {
                    log.warn("Concurrent order limit exceeded for symbol {}", symbol);
                    ordersRejected.incrementAndGet();
                    return OrderResponse.builder()
                            .success(false)
                            .message("Concurrent order limit exceeded")
                            .build();
                }
                
                // Check order timeout
                if (isOrderTimedOut(symbol)) {
                    log.warn("Order timeout for symbol {}", symbol);
                    ordersRejected.incrementAndGet();
                    return OrderResponse.builder()
                            .success(false)
                            .message("Order timeout")
                            .build();
                }
                
                // Place order through broker adapter
                CompletableFuture<BrokerAdapter.OrderResponse> brokerResponse = brokerAdapter.placeOrder(orderRequest);
                
                // Create order tracking
                Order order = Order.builder()
                        .orderId(orderId)
                        .symbol(symbol)
                        .exchange(orderRequest.getExchange())
                        .orderType(orderRequest.getOrderType())
                        .side(orderRequest.getSide())
                        .quantity(orderRequest.getQuantity())
                        .price(orderRequest.getPrice())
                        .triggerPrice(orderRequest.getTriggerPrice())
                        .stopLoss(orderRequest.getStopLoss())
                        .takeProfit(orderRequest.getTakeProfit())
                        .validity(orderRequest.getValidity())
                        .product(orderRequest.getProduct())
                        .state(OrderState.PENDING)
                        .filledQuantity(0)
                        .remainingQuantity(orderRequest.getQuantity())
                        .averagePrice(BigDecimal.ZERO)
                        .orderTimestamp(Instant.now())
                        .lastUpdated(Instant.now())
                        .tag(orderRequest.getTag())
                        .build();
                
                // Store order
                activeOrders.put(orderId, order);
                orderHistory.put(orderId, order);
                lastOrderTimes.put(symbol, Instant.now());
                
                // Handle broker response
                BrokerAdapter.OrderResponse response = brokerResponse.get();
                if (response.isSuccess()) {
                    order.setOrderId(response.getOrderId());
                    order.setState(OrderState.OPEN);
                    ordersPlaced.incrementAndGet();
                    
                    log.info("Order {} placed successfully", orderId);
                } else {
                    order.setState(OrderState.REJECTED);
                    ordersRejected.incrementAndGet();
                    
                    log.error("Order {} placement failed: {}", orderId, response.getMessage());
                }
                
                // Update order
                order.setLastUpdated(Instant.now());
                activeOrders.put(orderId, order);
                orderHistory.put(orderId, order);
                
                // Publish order status
                publishOrderStatus(order);
                
                return OrderResponse.builder()
                        .success(response.isSuccess())
                        .orderId(orderId)
                        .message(response.getMessage())
                        .build();
                
            } catch (Exception e) {
                log.error("Error placing order", e);
                ordersRejected.incrementAndGet();
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Order placement error: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Modify order
     */
    @Async
    public CompletableFuture<OrderResponse> modifyOrder(String orderId, Integer quantity, BigDecimal price, 
                                               BigDecimal triggerPrice, BigDecimal stopLoss, BigDecimal takeProfit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Order order = activeOrders.get(orderId);
                if (order == null) {
                    log.warn("Order {} not found for modification", orderId);
                    return OrderResponse.builder()
                            .success(false)
                            .message("Order not found")
                            .build();
                }
                
                if (order.getState() != OrderState.OPEN && order.getState() != OrderState.PENDING) {
                    log.warn("Order {} cannot be modified in state {}", orderId, order.getState());
                    return OrderResponse.builder()
                            .success(false)
                            .message("Order cannot be modified in current state")
                            .build();
                }
                
                log.info("Modifying order {}: qty={}, price={}, trigger={}, SL={}, TP={}", 
                          orderId, quantity, price, triggerPrice, stopLoss, takeProfit);
                
                // Create modify request
                BrokerAdapter.ModifyOrderRequest modifyRequest = BrokerAdapter.ModifyOrderRequest.builder()
                        .orderId(orderId)
                        .quantity(quantity)
                        .price(price)
                        .triggerPrice(triggerPrice)
                        .stopLoss(stopLoss)
                        .takeProfit(takeProfit)
                        .validity(order.getValidity())
                        .build();
                
                // Modify order through broker adapter
                CompletableFuture<BrokerAdapter.OrderResponse> brokerResponse = brokerAdapter.modifyOrder(modifyRequest);
                
                // Update order
                order.setQuantity(quantity != null ? quantity : order.getQuantity());
                order.setPrice(price != null ? price : order.getPrice());
                order.setTriggerPrice(triggerPrice != null ? triggerPrice : order.getTriggerPrice());
                order.setStopLoss(stopLoss != null ? stopLoss : order.getStopLoss());
                order.setTakeProfit(takeProfit != null ? takeProfit : order.getTakeProfit());
                order.setLastUpdated(Instant.now());
                
                // Handle broker response
                BrokerAdapter.OrderResponse response = brokerResponse.get();
                if (response.isSuccess()) {
                    ordersModified.incrementAndGet();
                    log.info("Order {} modified successfully", orderId);
                } else {
                    log.error("Order {} modification failed: {}", orderId, response.getMessage());
                }
                
                // Update order
                activeOrders.put(orderId, order);
                orderHistory.put(orderId, order);
                
                // Publish order status
                publishOrderStatus(order);
                
                return OrderResponse.builder()
                        .success(response.isSuccess())
                        .orderId(orderId)
                        .message(response.getMessage())
                        .build();
                
            } catch (Exception e) {
                log.error("Error modifying order {}", orderId, e);
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Order modification error: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Cancel order
     */
    @Async
    public CompletableFuture<OrderResponse> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Order order = activeOrders.get(orderId);
                if (order == null) {
                    log.warn("Order {} not found for cancellation", orderId);
                    return OrderResponse.builder()
                            .success(false)
                            .message("Order not found")
                            .build();
                }
                
                if (order.getState() == OrderState.FILLED || order.getState() == OrderState.CANCELLED) {
                    log.warn("Order {} cannot be cancelled in state {}", orderId, order.getState());
                    return OrderResponse.builder()
                            .success(false)
                            .message("Order cannot be cancelled in current state")
                            .build();
                }
                
                log.info("Cancelling order {}", orderId);
                
                // Cancel order through broker adapter
                CompletableFuture<BrokerAdapter.OrderResponse> brokerResponse = brokerAdapter.cancelOrder(orderId);
                
                // Update order
                order.setState(OrderState.CANCELLING);
                order.setLastUpdated(Instant.now());
                
                // Handle broker response
                BrokerAdapter.OrderResponse response = brokerResponse.get();
                if (response.isSuccess()) {
                    order.setState(OrderState.CANCELLED);
                    ordersCancelled.incrementAndGet();
                    log.info("Order {} cancelled successfully", orderId);
                } else {
                    log.error("Order {} cancellation failed: {}", orderId, response.getMessage());
                }
                
                // Update order
                activeOrders.put(orderId, order);
                orderHistory.put(orderId, order);
                
                // Publish order status
                publishOrderStatus(order);
                
                return OrderResponse.builder()
                        .success(response.isSuccess())
                        .orderId(orderId)
                        .message(response.getMessage())
                        .build();
                
            } catch (Exception e) {
                log.error("Error cancelling order {}", orderId, e);
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Order cancellation error: " + e.getMessage())
                        .build();
            }
        });
    }

    /**
     * Get order status
     */
    @Async
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Order order = activeOrders.get(orderId);
                if (order == null) {
                    order = orderHistory.get(orderId);
                }
                
                if (order == null) {
                    log.warn("Order {} not found", orderId);
                    return null;
                }
                
                // Get status from broker if order is active
                if (order.getState() == OrderState.OPEN || order.getState() == OrderState.PENDING) {
                    CompletableFuture<BrokerAdapter.OrderStatus> brokerStatus = brokerAdapter.getOrderStatus(orderId);
                    BrokerAdapter.OrderStatus status = brokerStatus.get();
                    
                    if (status != null) {
                        // Update order with broker status
                        order.setState(parseOrderState(status.getState()));
                        order.setFilledQuantity(status.getFilledQuantity());
                        order.setRemainingQuantity(status.getRemainingQuantity());
                        order.setAveragePrice(status.getAveragePrice());
                        order.setLastUpdated(Instant.now());
                        
                        // Update order if filled
                        if (order.getState() == OrderState.FILLED) {
                            ordersFilled.incrementAndGet();
                            log.info("Order {} filled", orderId);
                        }
                        
                        // Update order
                        activeOrders.put(orderId, order);
                        orderHistory.put(orderId, order);
                        
                        // Publish order status
                        publishOrderStatus(order);
                    }
                    
                    return OrderStatus.builder()
                            .orderId(orderId)
                            .state(order.getState())
                            .filledQuantity(order.getFilledQuantity())
                            .remainingQuantity(order.getRemainingQuantity())
                            .averagePrice(order.getAveragePrice())
                            .lastUpdated(order.getLastUpdated())
                            .build();
                } else {
                    return OrderStatus.builder()
                            .orderId(orderId)
                            .state(order.getState())
                            .filledQuantity(order.getFilledQuantity())
                            .remainingQuantity(order.getRemainingQuantity())
                            .averagePrice(order.getAveragePrice())
                            .lastUpdated(order.getLastUpdated())
                            .build();
                }
                
            } catch (Exception e) {
                log.error("Error getting order status for {}", orderId, e);
                return null;
            }
        });
    }

    /**
     * Get active orders
     */
    public List<Order> getActiveOrders() {
        return new ArrayList<>(activeOrders.values());
    }

    /**
     * Get active orders for symbol
     */
    public List<Order> getActiveOrders(String symbol) {
        List<Order> symbolOrders = new ArrayList<>();
        
        for (Order order : activeOrders.values()) {
            if (symbol.equals(order.getSymbol())) {
                symbolOrders.add(order);
            }
        }
        
        return symbolOrders;
    }

    /**
     * Get order history
     */
    public List<Order> getOrderHistory() {
        return new ArrayList<>(orderHistory.values());
    }

    /**
     * Get order history for symbol
     */
    public List<Order> getOrderHistory(String symbol, int limit) {
        List<Order> symbolOrders = new ArrayList<>();
        
        for (Order order : orderHistory.values()) {
            if (symbol.equals(order.getSymbol())) {
                symbolOrders.add(order);
            }
        }
        
        // Sort by timestamp (most recent first)
        symbolOrders.sort(Comparator.comparing(Order::getOrderTimestamp).reversed());
        
        // Apply limit
        if (limit > 0 && symbolOrders.size() > limit) {
            symbolOrders = symbolOrders.subList(0, limit);
        }
        
        return symbolOrders;
    }

    /**
     * Get order statistics
     */
    public OrderStatistics getStatistics() {
        return OrderStatistics.builder()
                .ordersPlaced(ordersPlaced.get())
                .ordersModified(ordersModified.get())
                .ordersCancelled(ordersCancelled.get())
                .ordersFilled(ordersFilled.get())
                .ordersRejected(ordersRejected.get())
                .activeOrders(activeOrders.size())
                .build();
    }

    /**
     * Process order status update from Kafka
     */
    @KafkaListener(topics = "order-status-updates", groupId = "order-manager-group")
    public void processOrderStatusUpdate(OrderStatusUpdate statusUpdate) {
        try {
            String orderId = statusUpdate.getOrderId();
            Order order = activeOrders.get(orderId);
            
            if (order == null) {
                log.warn("Order {} not found for status update", orderId);
                return;
            }
            
            // Update order status
            order.setState(parseOrderState(statusUpdate.getState()));
            order.setFilledQuantity(statusUpdate.getFilledQuantity());
            order.setRemainingQuantity(statusUpdate.getRemainingQuantity());
            order.setAveragePrice(statusUpdate.getAveragePrice());
            order.setLastUpdated(Instant.now());
            
            // Update order
            activeOrders.put(orderId, order);
            orderHistory.put(orderId, order);
            
            // Publish order status
            publishOrderStatus(order);
            
            log.debug("Updated status for order {}: {}", orderId, statusUpdate.getState());
            
        } catch (Exception e) {
            log.error("Error processing order status update for {}", statusUpdate.getOrderId(), e);
        }
    }

    /**
     * Cleanup old orders
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupOldOrders() {
        Instant cutoff = Instant.now().minusSeconds(86400); // 24 hours ago
        int cleanedCount = 0;
        
        Iterator<Map.Entry<String, Order>> iterator = activeOrders.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Order> entry = iterator.next();
            Order order = entry.getValue();
            
            if (order.getOrderTimestamp().isBefore(cutoff) && 
                (order.getState() == OrderState.FILLED || order.getState() == OrderState.CANCELLED)) {
                iterator.remove();
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            log.info("Cleaned up {} old orders", cleanedCount);
        }
    }

    // Private helper methods
    private String generateOrderId() {
        return "ORD-" + System.currentTimeMillis() + "-" + Thread.currentThread().getId();
    }

    private int getConcurrentOrderCount(String symbol) {
        int count = 0;
        
        for (Order order : activeOrders.values()) {
            if (symbol.equals(order.getSymbol()) && 
                (order.getState() == OrderState.OPEN || order.getState() == OrderState.PENDING)) {
                count++;
            }
        }
        
        return count;
    }

    private boolean isOrderTimedOut(String symbol) {
        Instant lastOrderTime = lastOrderTimes.get(symbol);
        if (lastOrderTime == null) {
            return false;
        }
        
        long timeSinceLastOrder = Instant.now().toEpochMilli() - lastOrderTime.toEpochMilli();
        return timeSinceLastOrder < orderTimeoutMs;
    }

    private OrderState parseOrderState(String brokerState) {
        try {
            return OrderState.valueOf(brokerState);
        } catch (Exception e) {
            return OrderState.PENDING;
        }
    }

    private void publishOrderStatus(Order order) {
        try {
            OrderStatusUpdate statusUpdate = OrderStatusUpdate.builder()
                    .orderId(order.getOrderId())
                    .state(order.getState().name())
                    .filledQuantity(order.getFilledQuantity())
                    .remainingQuantity(order.getRemainingQuantity())
                    .averagePrice(order.getAveragePrice())
                    .timestamp(Instant.now())
                    .build();
            
            orderKafkaTemplate.send("order-status-updates", order.getOrderId(), statusUpdate);
        } catch (Exception e) {
            log.error("Error publishing order status for {}", order.getOrderId(), e);
        }
    }

    /**
     * Order status update class
     */
    @lombok.Builder
    @lombok.Data
    public static class OrderStatusUpdate {
        private String orderId;
        private String state;
        private Integer filledQuantity;
        private Integer remainingQuantity;
        private BigDecimal averagePrice;
        private Instant timestamp;
    }

    /**
     * Order statistics class
     */
    @lombok.Builder
    @lombok.Data
    public static class OrderStatistics {
        private long ordersPlaced;
        private long ordersModified;
        private long ordersCancelled;
        private long ordersFilled;
        private long ordersRejected;
        private int activeOrders;
    }

    /**
     * Order status class
     */
    @lombok.Builder
    @lombok.Data
    public static class OrderStatus {
        private String orderId;
        private OrderState state;
        private Integer filledQuantity;
        private Integer remainingQuantity;
        private BigDecimal averagePrice;
        private Instant lastUpdated;
    }

    /**
     * Order class
     */
    @lombok.Builder
    @lombok.Data
    public static class Order {
        private String orderId;
        private String symbol;
        private String exchange;
        private BrokerAdapter.OrderType orderType;
        private BrokerAdapter.OrderSide side;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal triggerPrice;
        private BigDecimal stopLoss;
        private BigDecimal takeProfit;
        private BrokerAdapter.OrderValidity validity;
        private String product;
        private OrderState state;
        private Integer filledQuantity;
        private Integer remainingQuantity;
        private BigDecimal averagePrice;
        private Instant orderTimestamp;
        private Instant lastUpdated;
        private String tag;
    }
}