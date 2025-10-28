package com.velox.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velox.marketdata.model.TickData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SMART API adapter implementation for broker operations.
 * Handles order placement, modification, cancellation, and position management.
 */
@Service
@Slf4j
public class SmartApiAdapter implements BrokerAdapter {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${smart.api.base.url}")
    private String baseUrl;

    @Value("${smart.api.jwt.token}")
    private String jwtToken;

    @Value("${smart.api.client.code}")
    private String clientCode;

    @Value("${smart.api.timeout.ms:5000}")
    private int timeoutMs;

    private volatile boolean isConnected = false;
    private final Map<String, Order> openOrders = new HashMap<>();
    private final Map<String, Position> positions = new HashMap<>();

    @Autowired
    public SmartApiAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getBrokerName() {
        return "SMART_API";
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Test connection with a simple API call
                String url = baseUrl + "/profile";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    isConnected = true;
                    log.info("Successfully connected to SMART API");
                    return true;
                } else {
                    isConnected = false;
                    log.error("Failed to connect to SMART API: {}", response.getStatusCode());
                    return false;
                }
            } catch (Exception e) {
                isConnected = false;
                log.error("Error connecting to SMART API", e);
                return false;
            }
        });
    }

    @Override
    public void disconnect() {
        isConnected = false;
        openOrders.clear();
        positions.clear();
        log.info("Disconnected from SMART API");
    }

    @Override
    public boolean isConnected() {
        return isConnected;
    }

    @Override
    public CompletableFuture<Boolean> subscribeToMarketData(List<String> symbols, String exchange) {
        // Market data subscription is handled by WebSocket client
        // This is just a placeholder implementation
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> unsubscribeFromMarketData(List<String> symbols, String exchange) {
        // Market data unsubscription is handled by WebSocket client
        // This is just a placeholder implementation
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public List<String> getSubscribedSymbols() {
        // Market data subscription is handled by WebSocket client
        // This is just a placeholder implementation
        return new ArrayList<>();
    }

    @Override
    public CompletableFuture<OrderResponse> placeOrder(OrderRequest orderRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return OrderResponse.builder()
                            .success(false)
                            .message("Not connected to broker")
                            .build();
                }

                String url = baseUrl + "/order/regular/order";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = createOrderRequestBody(orderRequest);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        String orderId = responseJson.get("data").get("orderid").asText();
                        
                        Order order = createOrderFromResponse(orderId, orderRequest);
                        openOrders.put(orderId, order);
                        
                        log.info("Order placed successfully: {}", orderId);
                        
                        return OrderResponse.builder()
                                .success(true)
                                .orderId(orderId)
                                .message("Order placed successfully")
                                .build();
                    } else {
                        String errorMessage = responseJson.has("message") ? 
                                responseJson.get("message").asText() : "Unknown error";
                        
                        log.error("Order placement failed: {}", errorMessage);
                        
                        return OrderResponse.builder()
                                .success(false)
                                .message(errorMessage)
                                .build();
                    }
                } else {
                    log.error("Order placement failed with HTTP status: {}", response.getStatusCode());
                    
                    return OrderResponse.builder()
                            .success(false)
                            .message("HTTP error: " + response.getStatusCode())
                            .build();
                }
            } catch (Exception e) {
                log.error("Error placing order", e);
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Exception: " + e.getMessage())
                        .build();
            }
        });
    }

    @Override
    public CompletableFuture<OrderResponse> modifyOrder(ModifyOrderRequest modifyRequest) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return OrderResponse.builder()
                            .success(false)
                            .message("Not connected to broker")
                            .build();
                }

                String url = baseUrl + "/order/regular/order";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = createModifyOrderRequestBody(modifyRequest);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.PUT, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        String orderId = modifyRequest.getOrderId();
                        
                        // Update order in our tracking
                        Order existingOrder = openOrders.get(orderId);
                        if (existingOrder != null) {
                            Order updatedOrder = updateOrderFromRequest(existingOrder, modifyRequest);
                            openOrders.put(orderId, updatedOrder);
                        }
                        
                        log.info("Order modified successfully: {}", orderId);
                        
                        return OrderResponse.builder()
                                .success(true)
                                .orderId(orderId)
                                .message("Order modified successfully")
                                .build();
                    } else {
                        String errorMessage = responseJson.has("message") ? 
                                responseJson.get("message").asText() : "Unknown error";
                        
                        log.error("Order modification failed: {}", errorMessage);
                        
                        return OrderResponse.builder()
                                .success(false)
                                .message(errorMessage)
                                .build();
                    }
                } else {
                    log.error("Order modification failed with HTTP status: {}", response.getStatusCode());
                    
                    return OrderResponse.builder()
                            .success(false)
                            .message("HTTP error: " + response.getStatusCode())
                            .build();
                }
            } catch (Exception e) {
                log.error("Error modifying order", e);
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Exception: " + e.getMessage())
                        .build();
            }
        });
    }

    @Override
    public CompletableFuture<OrderResponse> cancelOrder(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return OrderResponse.builder()
                            .success(false)
                            .message("Not connected to broker")
                            .build();
                }

                String url = baseUrl + "/order/regular/order";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = Map.of(
                        "variety", "NORMAL",
                        "orderid", orderId
                );
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.DELETE, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        // Remove order from our tracking
                        openOrders.remove(orderId);
                        
                        log.info("Order cancelled successfully: {}", orderId);
                        
                        return OrderResponse.builder()
                                .success(true)
                                .orderId(orderId)
                                .message("Order cancelled successfully")
                                .build();
                    } else {
                        String errorMessage = responseJson.has("message") ? 
                                responseJson.get("message").asText() : "Unknown error";
                        
                        log.error("Order cancellation failed: {}", errorMessage);
                        
                        return OrderResponse.builder()
                                .success(false)
                                .message(errorMessage)
                                .build();
                    }
                } else {
                    log.error("Order cancellation failed with HTTP status: {}", response.getStatusCode());
                    
                    return OrderResponse.builder()
                            .success(false)
                            .message("HTTP error: " + response.getStatusCode())
                            .build();
                }
            } catch (Exception e) {
                log.error("Error cancelling order", e);
                
                return OrderResponse.builder()
                        .success(false)
                        .message("Exception: " + e.getMessage())
                        .build();
            }
        });
    }

    @Override
    public CompletableFuture<OrderStatus> getOrderStatus(String orderId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return null;
                }

                String url = baseUrl + "/order/regular/order";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = Map.of(
                        "orderid", orderId
                );
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode orderData = responseJson.get("data");
                        
                        return createOrderStatusFromResponse(orderId, orderData);
                    } else {
                        log.error("Failed to get order status: {}", response.getBody());
                        return null;
                    }
                } else {
                    log.error("Failed to get order status with HTTP status: {}", response.getStatusCode());
                    return null;
                }
            } catch (Exception e) {
                log.error("Error getting order status", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> getOpenOrders() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return new ArrayList<>();
                }

                String url = baseUrl + "/order/regular/orders";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode ordersData = responseJson.get("data");
                        
                        List<Order> orders = new ArrayList<>();
                        if (ordersData.isArray()) {
                            for (JsonNode orderNode : ordersData) {
                                Order order = createOrderFromJsonNode(orderNode);
                                orders.add(order);
                                openOrders.put(order.getOrderId(), order);
                            }
                        }
                        
                        return orders;
                    } else {
                        log.error("Failed to get open orders: {}", response.getBody());
                        return new ArrayList<>();
                    }
                } else {
                    log.error("Failed to get open orders with HTTP status: {}", response.getStatusCode());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("Error getting open orders", e);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<List<Order>> getOrderHistory(String symbol, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return new ArrayList<>();
                }

                String url = baseUrl + "/order/regular/orders";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = Map.of(
                        "symbol", symbol,
                        "limit", limit
                );
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode ordersData = responseJson.get("data");
                        
                        List<Order> orders = new ArrayList<>();
                        if (ordersData.isArray()) {
                            for (JsonNode orderNode : ordersData) {
                                Order order = createOrderFromJsonNode(orderNode);
                                orders.add(order);
                            }
                        }
                        
                        return orders;
                    } else {
                        log.error("Failed to get order history: {}", response.getBody());
                        return new ArrayList<>();
                    }
                } else {
                    log.error("Failed to get order history with HTTP status: {}", response.getStatusCode());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("Error getting order history", e);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<List<Position>> getPositions() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return new ArrayList<>();
                }

                String url = baseUrl + "/portfolio/longterm-positions";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode positionsData = responseJson.get("data");
                        
                        List<Position> positions = new ArrayList<>();
                        if (positionsData.isArray()) {
                            for (JsonNode positionNode : positionsData) {
                                Position position = createPositionFromJsonNode(positionNode);
                                positions.add(position);
                                positions.put(position.getSymbol(), position);
                            }
                        }
                        
                        return positions;
                    } else {
                        log.error("Failed to get positions: {}", response.getBody());
                        return new ArrayList<>();
                    }
                } else {
                    log.error("Failed to get positions with HTTP status: {}", response.getStatusCode());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                log.error("Error getting positions", e);
                return new ArrayList<>();
            }
        });
    }

    @Override
    public CompletableFuture<Position> getPosition(String symbol) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return null;
                }

                String url = baseUrl + "/portfolio/longterm-positions";
                HttpHeaders headers = createAuthHeaders();
                
                Map<String, Object> requestBody = Map.of(
                        "symbol", symbol
                );
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode positionsData = responseJson.get("data");
                        
                        if (positionsData.isArray()) {
                            for (JsonNode positionNode : positionsData) {
                                Position position = createPositionFromJsonNode(positionNode);
                                if (symbol.equals(position.getSymbol())) {
                                    positions.put(symbol, position);
                                    return position;
                                }
                            }
                        }
                        
                        return null;
                    } else {
                        log.error("Failed to get position: {}", response.getBody());
                        return null;
                    }
                } else {
                    log.error("Failed to get position with HTTP status: {}", response.getStatusCode());
                    return null;
                }
            } catch (Exception e) {
                log.error("Error getting position", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<AccountBalance> getAccountBalance() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return null;
                }

                String url = baseUrl + "/user/segment";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode balanceData = responseJson.get("data");
                        
                        return createAccountBalanceFromResponse(balanceData);
                    } else {
                        log.error("Failed to get account balance: {}", response.getBody());
                        return null;
                    }
                } else {
                    log.error("Failed to get account balance with HTTP status: {}", response.getStatusCode());
                    return null;
                }
            } catch (Exception e) {
                log.error("Error getting account balance", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<TradingLimits> getTradingLimits() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected) {
                    return null;
                }

                String url = baseUrl + "/user/segment";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                
                if (response.getStatusCode() == HttpStatus.OK) {
                    JsonNode responseJson = objectMapper.readTree(response.getBody());
                    
                    if (responseJson.has("status") && "success".equals(responseJson.get("status").asText())) {
                        JsonNode limitsData = responseJson.get("data");
                        
                        return createTradingLimitsFromResponse(limitsData);
                    } else {
                        log.error("Failed to get trading limits: {}", response.getBody());
                        return null;
                    }
                } else {
                    log.error("Failed to get trading limits with HTTP status: {}", response.getStatusCode());
                    return null;
                }
            } catch (Exception e) {
                log.error("Error getting trading limits", e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<BrokerHealth> getHealthStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = baseUrl + "/profile";
                HttpHeaders headers = createAuthHeaders();
                
                HttpEntity<String> entity = new HttpEntity<>(headers);
                
                long startTime = System.currentTimeMillis();
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);
                long responseTime = System.currentTimeMillis() - startTime;
                
                boolean isHealthy = response.getStatusCode() == HttpStatus.OK;
                
                return BrokerHealth.builder()
                        .healthy(isHealthy)
                        .status(isHealthy ? "UP" : "DOWN")
                        .responseTime(responseTime)
                        .lastUpdated(Instant.now())
                        .activeServices(isHealthy ? List.of("orders", "positions", "marketdata") : List.of())
                        .build();
            } catch (Exception e) {
                log.error("Error checking broker health", e);
                
                return BrokerHealth.builder()
                        .healthy(false)
                        .status("ERROR")
                        .responseTime(-1L)
                        .lastUpdated(Instant.now())
                        .activeServices(List.of())
                        .build();
            }
        });
    }

    // Helper methods
    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + jwtToken);
        headers.set("x-clientcode", clientCode);
        headers.set("x-sourceid", "WEB");
        return headers;
    }

    private Map<String, Object> createOrderRequestBody(OrderRequest orderRequest) {
        return Map.of(
                "variety", "NORMAL",
                "tradingsymbol", orderRequest.getSymbol(),
                "symboltoken", Map.of(
                        "symbol", orderRequest.getSymbol(),
                        "token", "NSE_EQ"
                )),
                "exchange", orderRequest.getExchange(),
                "transactiontype", "NEW",
                "ordertype", orderRequest.getOrderType().getValue(),
                "producttype", orderRequest.getProduct(),
                "price", orderRequest.getPrice(),
                "quantity", orderRequest.getQuantity(),
                "disclosedquantity", "0",
                "triggerprice", orderRequest.getTriggerPrice(),
                "stoploss", orderRequest.getStopLoss(),
                "squareoff", "day",
                "validity", orderRequest.getValidity().getValue(),
                "tag", orderRequest.getTag()
        );
    }

    private Map<String, Object> createModifyOrderRequestBody(ModifyOrderRequest modifyRequest) {
        return Map.of(
                "variety", "NORMAL",
                "orderid", modifyRequest.getOrderId(),
                "ordertype", "LIMIT",
                "price", modifyRequest.getPrice(),
                "quantity", modifyRequest.getQuantity(),
                "triggerprice", modifyRequest.getTriggerPrice(),
                "stoploss", modifyRequest.getStopLoss(),
                "validity", modifyRequest.getValidity().getValue()
        );
    }

    private Order createOrderFromResponse(String orderId, OrderRequest orderRequest) {
        return Order.builder()
                .orderId(orderId)
                .symbol(orderRequest.getSymbol())
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
                .state(OrderState.OPEN)
                .filledQuantity(0)
                .remainingQuantity(orderRequest.getQuantity())
                .averagePrice(BigDecimal.ZERO)
                .orderTimestamp(Instant.now())
                .lastUpdated(Instant.now())
                .tag(orderRequest.getTag())
                .build();
    }

    private Order updateOrderFromRequest(Order existingOrder, ModifyOrderRequest modifyRequest) {
        return Order.builder()
                .orderId(existingOrder.getOrderId())
                .symbol(existingOrder.getSymbol())
                .exchange(existingOrder.getExchange())
                .orderType(existingOrder.getOrderType())
                .side(existingOrder.getSide())
                .quantity(modifyRequest.getQuantity())
                .price(modifyRequest.getPrice())
                .triggerPrice(modifyRequest.getTriggerPrice())
                .stopLoss(modifyRequest.getStopLoss())
                .takeProfit(existingOrder.getTakeProfit())
                .validity(modifyRequest.getValidity())
                .product(existingOrder.getProduct())
                .state(existingOrder.getState())
                .filledQuantity(existingOrder.getFilledQuantity())
                .remainingQuantity(modifyRequest.getQuantity())
                .averagePrice(existingOrder.getAveragePrice())
                .orderTimestamp(existingOrder.getOrderTimestamp())
                .lastUpdated(Instant.now())
                .tag(existingOrder.getTag())
                .build();
    }

    private Order createOrderFromJsonNode(JsonNode orderNode) {
        return Order.builder()
                .orderId(orderNode.has("orderid") ? orderNode.get("orderid").asText() : "")
                .symbol(orderNode.has("tradingsymbol") ? orderNode.get("tradingsymbol").asText() : "")
                .exchange(orderNode.has("exchange") ? orderNode.get("exchange").asText() : "")
                .orderType(parseOrderType(orderNode.has("ordertype") ? orderNode.get("ordertype").asText() : ""))
                .side(parseOrderSide(orderNode.has("transactiontype") ? orderNode.get("transactiontype").asText() : ""))
                .quantity(orderNode.has("quantity") ? orderNode.get("quantity").intValue() : 0)
                .price(orderNode.has("price") ? BigDecimal.valueOf(orderNode.get("price").asDouble()) : BigDecimal.ZERO)
                .triggerPrice(orderNode.has("triggerprice") ? BigDecimal.valueOf(orderNode.get("triggerprice").asDouble()) : null)
                .stopLoss(orderNode.has("stoploss") ? BigDecimal.valueOf(orderNode.get("stoploss").asDouble()) : null)
                .takeProfit(orderNode.has("targetprice") ? BigDecimal.valueOf(orderNode.get("targetprice").asDouble()) : null)
                .validity(parseOrderValidity(orderNode.has("validity") ? orderNode.get("validity").asText() : ""))
                .product(orderNode.has("producttype") ? orderNode.get("producttype").asText() : "")
                .state(parseOrderState(orderNode.has("status") ? orderNode.get("status").asText() : ""))
                .filledQuantity(orderNode.has("filledquantity") ? orderNode.get("filledquantity").intValue() : 0)
                .remainingQuantity(orderNode.has("pendingquantity") ? orderNode.get("pendingquantity").intValue() : 0)
                .averagePrice(orderNode.has("averageprice") ? BigDecimal.valueOf(orderNode.get("averageprice").asDouble()) : BigDecimal.ZERO)
                .orderTimestamp(orderNode.has("orderupdatetime") ? Instant.parse(orderNode.get("orderupdatetime").asText()) : Instant.now())
                .lastUpdated(Instant.now())
                .tag(orderNode.has("tag") ? orderNode.get("tag").asText() : "")
                .build();
    }

    private OrderStatus createOrderStatusFromResponse(String orderId, JsonNode orderData) {
        return OrderStatus.builder()
                .orderId(orderId)
                .state(parseOrderState(orderData.has("status") ? orderData.get("status").asText() : ""))
                .filledQuantity(orderData.has("filledquantity") ? orderData.get("filledquantity").intValue() : 0)
                .remainingQuantity(orderData.has("pendingquantity") ? orderData.get("pendingquantity").intValue() : 0)
                .averagePrice(orderData.has("averageprice") ? BigDecimal.valueOf(orderData.get("averageprice").asDouble()) : BigDecimal.ZERO)
                .lastUpdated(Instant.now())
                .build();
    }

    private Position createPositionFromJsonNode(JsonNode positionNode) {
        return Position.builder()
                .symbol(positionNode.has("tradingsymbol") ? positionNode.get("tradingsymbol").asText() : "")
                .exchange(positionNode.has("exchange") ? positionNode.get("exchange").asText() : "")
                .product(positionNode.has("producttype") ? positionNode.get("producttype").asText() : "")
                .positionType(parsePositionType(positionNode.has("positiontype") ? positionNode.get("positiontype").asText() : ""))
                .quantity(positionNode.has("quantity") ? positionNode.get("quantity").doubleValue() : 0.0)
                .averagePrice(positionNode.has("averageprice") ? BigDecimal.valueOf(positionNode.get("averageprice").asDouble()) : BigDecimal.ZERO)
                .currentPrice(positionNode.has("ltp") ? BigDecimal.valueOf(positionNode.get("ltp").asDouble()) : BigDecimal.ZERO)
                .unrealizedPnL(positionNode.has("pnl") ? BigDecimal.valueOf(positionNode.get("pnl").asDouble()) : BigDecimal.ZERO)
                .realizedPnL(BigDecimal.ZERO)
                .totalPnL(positionNode.has("pnl") ? BigDecimal.valueOf(positionNode.get("pnl").asDouble()) : BigDecimal.ZERO)
                .lastUpdated(Instant.now())
                .build();
    }

    private AccountBalance createAccountBalanceFromResponse(JsonNode balanceData) {
        return AccountBalance.builder()
                .availableBalance(balanceData.has("cash") ? BigDecimal.valueOf(balanceData.get("cash").asDouble()) : BigDecimal.ZERO)
                .usedMargin(balanceData.has("marginused") ? BigDecimal.valueOf(balanceData.get("marginused").asDouble()) : BigDecimal.ZERO)
                .availableMargin(balanceData.has("marginavailable") ? BigDecimal.valueOf(balanceData.get("marginavailable").asDouble()) : BigDecimal.ZERO)
                .totalBalance(balanceData.has("net") ? BigDecimal.valueOf(balanceData.get("net").asDouble()) : BigDecimal.ZERO)
                .currency("INR")
                .lastUpdated(Instant.now())
                .build();
    }

    private TradingLimits createTradingLimitsFromResponse(JsonNode limitsData) {
        return TradingLimits.builder()
                .maxOrderSize(limitsData.has("maxorder") ? limitsData.get("maxorder").doubleValue() : 0.0)
                .maxPositionSize(limitsData.has("maxposition") ? limitsData.get("maxposition").doubleValue() : 0.0)
                .maxOrdersPerDay(limitsData.has("maxorderperday") ? limitsData.get("maxorderperday").intValue() : 0)
                .maxPositionValue(limitsData.has("maxpositionvalue") ? limitsData.get("maxpositionvalue").doubleValue() : 0.0)
                .currency("INR")
                .lastUpdated(Instant.now())
                .build();
    }

    private OrderType parseOrderType(String orderType) {
        try {
            return OrderType.valueOf(orderType);
        } catch (Exception e) {
            return OrderType.MARKET;
        }
    }

    private OrderSide parseOrderSide(String transactionType) {
        try {
            return "BUY".equals(transactionType) ? OrderSide.BUY : OrderSide.SELL;
        } catch (Exception e) {
            return OrderSide.BUY;
        }
    }

    private OrderState parseOrderState(String status) {
        try {
            return OrderState.valueOf(status);
        } catch (Exception e) {
            return OrderState.PENDING;
        }
    }

    private OrderValidity parseOrderValidity(String validity) {
        try {
            return OrderValidity.valueOf(validity);
        } catch (Exception e) {
            return OrderValidity.DAY;
        }
    }

    private PositionType parsePositionType(String positionType) {
        try {
            return PositionType.valueOf(positionType);
        } catch (Exception e) {
            return PositionType.NET;
        }
    }
}