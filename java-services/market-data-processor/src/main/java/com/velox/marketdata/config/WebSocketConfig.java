package com.velox.marketdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * WebSocket configuration for the Market Data Processor microservice.
 * Configures WebSocket clients for connecting to SMART API and other market data sources.
 */
@Configuration
public class WebSocketConfig {

    @Value("${smart.api.websocket.url}")
    private String smartApiWebSocketUrl;

    @Value("${smart.api.websocket.max-text-message-buffer-size}")
    private int maxTextMessageBufferSize;

    @Value("${smart.api.websocket.max-binary-message-buffer-size}")
    private int maxBinaryMessageBufferSize;

    @Value("${smart.api.websocket.async-send-timeout}")
    private long asyncSendTimeout;

    @Value("${smart.api.websocket.max-session-idle-timeout}")
    private long maxSessionIdleTimeout;

    @Value("${marketdata.websocket.thread-pool-size}")
    private int webSocketThreadPoolSize;

    /**
     * WebSocket client for SMART API connection.
     * Configured with optimized settings for high-frequency market data.
     */
    @Bean
    public WebSocketClient smartApiWebSocketClient() {
        StandardWebSocketClient client = new StandardWebSocketClient();
        
        // Configure WebSocket client properties
        System.setProperty("javax.websocket.client.maxTextMessageBufferSize", 
                         String.valueOf(maxTextMessageBufferSize));
        System.setProperty("javax.websocket.client.maxBinaryMessageBufferSize", 
                         String.valueOf(maxBinaryMessageBufferSize));
        System.setProperty("javax.websocket.client.asyncSendTimeout", 
                         String.valueOf(asyncSendTimeout));
        System.setProperty("javax.websocket.client.maxSessionIdleTimeout", 
                         String.valueOf(maxSessionIdleTimeout));
        
        return client;
    }

    /**
     * Dedicated thread pool for WebSocket operations.
     * Ensures non-blocking WebSocket message processing.
     */
    @Bean
    public ScheduledExecutorService webSocketExecutorService() {
        return Executors.newScheduledThreadPool(webSocketThreadPoolSize, r -> {
            Thread thread = new Thread(r, "websocket-executor");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY + 1); // Slightly higher priority for real-time data
            return thread;
        });
    }

    /**
     * WebSocket connection properties for SMART API.
     */
    @Bean
    public SmartApiWebSocketProperties smartApiWebSocketProperties() {
        SmartApiWebSocketProperties properties = new SmartApiWebSocketProperties();
        properties.setUrl(smartApiWebSocketUrl);
        properties.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        properties.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        properties.setAsyncSendTimeout(asyncSendTimeout);
        properties.setMaxSessionIdleTimeout(maxSessionIdleTimeout);
        properties.setConnectionTimeout(5000); // 5 seconds
        properties.setReconnectInterval(1000); // 1 second
        properties.setMaxReconnectAttempts(10);
        properties.setHeartbeatInterval(30000); // 30 seconds
        properties.setHeartbeatTimeout(10000); // 10 seconds
        return properties;
    }

    /**
     * Properties class for SMART API WebSocket configuration.
     */
    public static class SmartApiWebSocketProperties {
        private String url;
        private int maxTextMessageBufferSize;
        private int maxBinaryMessageBufferSize;
        private long asyncSendTimeout;
        private long maxSessionIdleTimeout;
        private int connectionTimeout;
        private int reconnectInterval;
        private int maxReconnectAttempts;
        private int heartbeatInterval;
        private int heartbeatTimeout;

        // Getters and setters
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public int getMaxTextMessageBufferSize() { return maxTextMessageBufferSize; }
        public void setMaxTextMessageBufferSize(int maxTextMessageBufferSize) { 
            this.maxTextMessageBufferSize = maxTextMessageBufferSize; 
        }

        public int getMaxBinaryMessageBufferSize() { return maxBinaryMessageBufferSize; }
        public void setMaxBinaryMessageBufferSize(int maxBinaryMessageBufferSize) { 
            this.maxBinaryMessageBufferSize = maxBinaryMessageBufferSize; 
        }

        public long getAsyncSendTimeout() { return asyncSendTimeout; }
        public void setAsyncSendTimeout(long asyncSendTimeout) { 
            this.asyncSendTimeout = asyncSendTimeout; 
        }

        public long getMaxSessionIdleTimeout() { return maxSessionIdleTimeout; }
        public void setMaxSessionIdleTimeout(long maxSessionIdleTimeout) { 
            this.maxSessionIdleTimeout = maxSessionIdleTimeout; 
        }

        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { 
            this.connectionTimeout = connectionTimeout; 
        }

        public int getReconnectInterval() { return reconnectInterval; }
        public void setReconnectInterval(int reconnectInterval) { 
            this.reconnectInterval = reconnectInterval; 
        }

        public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
        public void setMaxReconnectAttempts(int maxReconnectAttempts) { 
            this.maxReconnectAttempts = maxReconnectAttempts; 
        }

        public int getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(int heartbeatInterval) { 
            this.heartbeatInterval = heartbeatInterval; 
        }

        public int getHeartbeatTimeout() { return heartbeatTimeout; }
        public void setHeartbeatTimeout(int heartbeatTimeout) { 
            this.heartbeatTimeout = heartbeatTimeout; 
        }
    }
}