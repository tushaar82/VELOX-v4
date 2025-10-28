package com.velox.marketdata.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velox.marketdata.config.WebSocketConfig;
import com.velox.marketdata.model.TickData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket client for connecting to SMART API and receiving real-time market data.
 * Handles connection management, reconnection logic, and data processing.
 */
@Service
@Slf4j
public class SmartApiWebSocketClient {

    private final WebSocketClient webSocketClient;
    private final ScheduledExecutorService executorService;
    private final KafkaTemplate<String, Object> tickDataKafkaTemplate;
    private final WebSocketConfig.SmartApiWebSocketProperties properties;
    private final ObjectMapper objectMapper;

    @Value("${smart.api.jwt.token}")
    private String jwtToken;

    @Value("${smart.api.client.code}")
    private String clientCode;

    @Value("${smart.api.feed.token}")
    private String feedToken;

    private WebSocketSession webSocketSession;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicLong reconnectAttempts = new AtomicLong(0);
    private final AtomicLong lastHeartbeat = new AtomicLong(0);
    private final AtomicLong messageCount = new AtomicLong(0);

    // Subscription management
    private final ConcurrentHashMap<String, String> subscribedSymbols = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TickData> lastTickData = new ConcurrentHashMap<>();

    // Connection state callbacks
    private final ConcurrentHashMap<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    @Autowired
    public SmartApiWebSocketClient(
            WebSocketClient webSocketClient,
            ScheduledExecutorService executorService,
            KafkaTemplate<String, Object> tickDataKafkaTemplate,
            WebSocketConfig.SmartApiWebSocketProperties properties,
            ObjectMapper objectMapper) {
        this.webSocketClient = webSocketClient;
        this.executorService = executorService;
        this.tickDataKafkaTemplate = tickDataKafkaTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Connect to SMART API WebSocket
     */
    public CompletableFuture<Boolean> connect() {
        if (isConnected.get() || isConnecting.get()) {
            return CompletableFuture.completedFuture(isConnected.get());
        }

        isConnecting.set(true);
        log.info("Connecting to SMART API WebSocket at: {}", properties.getUrl());

        return CompletableFuture.supplyAsync(() -> {
            try {
                WebSocketSession session = webSocketClient.doHandshake(
                        new SmartApiWebSocketHandler(),
                        null,
                        URI.create(properties.getUrl())
                ).get();

                webSocketSession = session;
                isConnected.set(true);
                isConnecting.set(false);
                reconnectAttempts.set(0);
                
                log.info("Successfully connected to SMART API WebSocket");
                return true;

            } catch (Exception e) {
                log.error("Failed to connect to SMART API WebSocket", e);
                isConnected.set(false);
                isConnecting.set(false);
                scheduleReconnect();
                return false;
            }
        }, executorService);
    }

    /**
     * Disconnect from SMART API WebSocket
     */
    public void disconnect() {
        if (webSocketSession != null && webSocketSession.isOpen()) {
            try {
                webSocketSession.close();
                log.info("Disconnected from SMART API WebSocket");
            } catch (Exception e) {
                log.error("Error closing WebSocket session", e);
            }
        }
        isConnected.set(false);
        isConnecting.set(false);
    }

    /**
     * Subscribe to market data for a symbol
     */
    public CompletableFuture<Boolean> subscribe(String symbol, String exchange) {
        if (!isConnected.get()) {
            log.warn("Cannot subscribe to {}: WebSocket not connected", symbol);
            return CompletableFuture.completedFuture(false);
        }

        String subscriptionKey = exchange + ":" + symbol;
        if (subscribedSymbols.containsKey(subscriptionKey)) {
            log.debug("Already subscribed to {}", subscriptionKey);
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String subscribeMessage = createSubscribeMessage(symbol, exchange);
                webSocketSession.sendMessage(new TextMessage(subscribeMessage));
                subscribedSymbols.put(subscriptionKey, symbol);
                log.info("Subscribed to market data for {}", subscriptionKey);
                return true;
            } catch (Exception e) {
                log.error("Failed to subscribe to {}", subscriptionKey, e);
                return false;
            }
        }, executorService);
    }

    /**
     * Unsubscribe from market data for a symbol
     */
    public CompletableFuture<Boolean> unsubscribe(String symbol, String exchange) {
        if (!isConnected.get()) {
            return CompletableFuture.completedFuture(false);
        }

        String subscriptionKey = exchange + ":" + symbol;
        if (!subscribedSymbols.containsKey(subscriptionKey)) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String unsubscribeMessage = createUnsubscribeMessage(symbol, exchange);
                webSocketSession.sendMessage(new TextMessage(unsubscribeMessage));
                subscribedSymbols.remove(subscriptionKey);
                lastTickData.remove(subscriptionKey);
                log.info("Unsubscribed from market data for {}", subscriptionKey);
                return true;
            } catch (Exception e) {
                log.error("Failed to unsubscribe from {}", subscriptionKey, e);
                return false;
            }
        }, executorService);
    }

    /**
     * Get connection status
     */
    public boolean isConnected() {
        return isConnected.get() && webSocketSession != null && webSocketSession.isOpen();
    }

    /**
     * Get subscribed symbols
     */
    public ConcurrentHashMap<String, String> getSubscribedSymbols() {
        return new ConcurrentHashMap<>(subscribedSymbols);
    }

    /**
     * Get last tick data for a symbol
     */
    public TickData getLastTickData(String symbol, String exchange) {
        String subscriptionKey = exchange + ":" + symbol;
        return lastTickData.get(subscriptionKey);
    }

    /**
     * WebSocket handler for SMART API
     */
    private class SmartApiWebSocketHandler implements WebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            log.info("WebSocket connection established with session: {}", session.getId());
            activeSessions.put(session.getId(), session);
            
            // Send authentication message
            sendAuthMessage(session);
            
            // Start heartbeat
            startHeartbeat(session);
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
            if (message instanceof TextMessage) {
                String payload = ((TextMessage) message).getPayload();
                processMessage(payload);
                messageCount.incrementAndGet();
            }
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
            log.error("WebSocket transport error for session: {}", session.getId(), exception);
            handleConnectionLoss(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            log.info("WebSocket connection closed for session: {}, status: {}", session.getId(), closeStatus);
            activeSessions.remove(session.getId());
            handleConnectionLoss(session);
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }

    /**
     * Process incoming WebSocket message
     */
    private void processMessage(String payload) {
        try {
            JsonNode rootNode = objectMapper.readTree(payload);
            
            if (rootNode.has("type")) {
                String messageType = rootNode.get("type").asText();
                
                switch (messageType) {
                    case "auth":
                        handleAuthResponse(rootNode);
                        break;
                    case "feed":
                        handleFeedData(rootNode);
                        break;
                    case "error":
                        handleErrorResponse(rootNode);
                        break;
                    case "heartbeat":
                        handleHeartbeatResponse(rootNode);
                        break;
                    default:
                        log.debug("Unknown message type: {}", messageType);
                }
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message: {}", payload, e);
        }
    }

    /**
     * Handle authentication response
     */
    private void handleAuthResponse(JsonNode response) {
        if (response.has("status") && "success".equals(response.get("status").asText())) {
            log.info("Authentication successful");
            // Resubscribe to all symbols after successful authentication
            resubscribeAllSymbols();
        } else {
            log.error("Authentication failed: {}", response.toString());
        }
    }

    /**
     * Handle market feed data
     */
    private void handleFeedData(JsonNode feedData) {
        try {
            TickData tickData = parseTickData(feedData);
            if (tickData != null && tickData.isValid()) {
                
                // Store last tick data
                String subscriptionKey = tickData.getExchange() + ":" + tickData.getSymbol();
                lastTickData.put(subscriptionKey, tickData);
                
                // Publish to Kafka
                tickDataKafkaTemplate.send("tick-data", tickData.getSymbol(), tickData);
                
                // Update metrics
                messageCount.incrementAndGet();
                lastHeartbeat.set(System.currentTimeMillis());
                
                log.trace("Processed tick for {}: {}", tickData.getSymbol(), tickData.getLastPrice());
            }
        } catch (Exception e) {
            log.error("Error handling feed data", e);
        }
    }

    /**
     * Parse tick data from SMART API feed
     */
    private TickData parseTickData(JsonNode feedData) {
        try {
            JsonNode data = feedData.get("data");
            
            return TickData.builder()
                    .symbol(data.get("symbol").asText())
                    .exchange(data.get("exchange").asText())
                    .lastPrice(data.get("ltp").decimalValue())
                    .bidPrice(data.has("bid") ? data.get("bid").decimalValue() : null)
                    .askPrice(data.has("ask") ? data.get("ask").decimalValue() : null)
                    .bidVolume(data.has("bidQty") ? data.get("bidQty").longValue() : null)
                    .askVolume(data.has("askQty") ? data.get("askQty").longValue() : null)
                    .volume(data.has("volume") ? data.get("volume").longValue() : null)
                    .value(data.has("value") ? data.get("value").decimalValue() : null)
                    .openPrice(data.has("open") ? data.get("open").decimalValue() : null)
                    .highPrice(data.has("high") ? data.get("high").decimalValue() : null)
                    .lowPrice(data.has("low") ? data.get("low").decimalValue() : null)
                    .closePrice(data.has("close") ? data.get("close").decimalValue() : null)
                    .tradeCount(data.has("trades") ? data.get("trades").intValue() : null)
                    .timestamp(Instant.now())
                    .sequenceNumber(data.has("sequence") ? data.get("sequence").longValue() : null)
                    .source("SMART_API")
                    .quality(TickData.TickQuality.GOOD)
                    .build();
        } catch (Exception e) {
            log.error("Error parsing tick data from feed: {}", feedData.toString(), e);
            return null;
        }
    }

    /**
     * Handle error response
     */
    private void handleErrorResponse(JsonNode error) {
        log.error("Received error from SMART API: {}", error.toString());
    }

    /**
     * Handle heartbeat response
     */
    private void handleHeartbeatResponse(JsonNode heartbeat) {
        lastHeartbeat.set(System.currentTimeMillis());
        log.debug("Received heartbeat response");
    }

    /**
     * Send authentication message
     */
    private void sendAuthMessage(WebSocketSession session) {
        try {
            String authMessage = String.format(
                    "{\"type\":\"auth\",\"token\":\"%s\",\"clientcode\":\"%s\",\"feedtoken\":\"%s\"}",
                    jwtToken, clientCode, feedToken
            );
            session.sendMessage(new TextMessage(authMessage));
            log.info("Sent authentication message");
        } catch (Exception e) {
            log.error("Error sending authentication message", e);
        }
    }

    /**
     * Create subscription message
     */
    private String createSubscribeMessage(String symbol, String exchange) {
        return String.format(
                "{\"type\":\"subscribe\",\"symbol\":\"%s\",\"exchange\":\"%s\"}",
                symbol, exchange
        );
    }

    /**
     * Create unsubscribe message
     */
    private String createUnsubscribeMessage(String symbol, String exchange) {
        return String.format(
                "{\"type\":\"unsubscribe\",\"symbol\":\"%s\",\"exchange\":\"%s\"}",
                symbol, exchange
        );
    }

    /**
     * Start heartbeat mechanism
     */
    private void startHeartbeat(WebSocketSession session) {
        executorService.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage("{\"type\":\"heartbeat\"}"));
                } catch (Exception e) {
                    log.error("Error sending heartbeat", e);
                }
            }
        }, properties.getHeartbeatInterval(), properties.getHeartbeatInterval(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Handle connection loss
     */
    private void handleConnectionLoss(WebSocketSession session) {
        isConnected.set(false);
        log.warn("Connection lost, scheduling reconnect...");
        scheduleReconnect();
    }

    /**
     * Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        if (reconnectAttempts.get() >= properties.getMaxReconnectAttempts()) {
            log.error("Maximum reconnection attempts reached. Giving up.");
            return;
        }

        long delay = properties.getReconnectInterval() * (long) Math.pow(2, reconnectAttempts.get());
        executorService.schedule(() -> {
            reconnectAttempts.incrementAndGet();
            log.info("Attempting reconnection {}/{}", reconnectAttempts.get(), properties.getMaxReconnectAttempts());
            connect();
        }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Resubscribe to all symbols after reconnection
     */
    private void resubscribeAllSymbols() {
        subscribedSymbols.forEach((subscriptionKey, symbol) -> {
            String[] parts = subscriptionKey.split(":", 2);
            if (parts.length == 2) {
                subscribe(parts[1], parts[0]);
            }
        });
    }

    /**
     * Scheduled health check
     */
    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void healthCheck() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastHeartbeat = currentTime - lastHeartbeat.get();
        
        if (isConnected.get() && timeSinceLastHeartbeat > properties.getHeartbeatTimeout()) {
            log.warn("Heartbeat timeout detected. Connection may be stale.");
            disconnect();
            connect();
        }
        
        log.debug("Health check - Connected: {}, Messages: {}, Last heartbeat: {}ms ago", 
                 isConnected.get(), messageCount.get(), timeSinceLastHeartbeat);
    }
}