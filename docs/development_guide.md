# Development Guide

## Overview

This guide provides comprehensive instructions for setting up development environment and implementing the multi-user algotrading system with Java microservices for high-performance components.

## Prerequisites

### System Requirements

- **Operating System**: Windows 10/11, macOS 10.15+, or Linux (Ubuntu 18.04+)
- **Java**: JDK 21 or higher (for Java microservices)
- **Node.js**: 16.x or higher (for API gateway)
- **PostgreSQL**: 15 or higher with TimescaleDB extension
- **Kafka**: 3.x or higher
- **Redis**: 6.0 or higher
- **Git**: Latest version

### Development Tools

- **IDE**: IntelliJ IDEA, VS Code, or similar
- **Database Tool**: pgAdmin, DBeaver, or similar
- **API Testing**: Postman, Insomnia, or similar
- **Version Control**: Git
- **Containerization**: Docker and Docker Compose

## Environment Setup

### 1. Clone Repository

```bash
git clone https://github.com/your-org/velox-algotrading.git
cd velox-algotrading
```

### 2. Java Microservices Setup

#### Create Virtual Environment

```bash
# For Java microservices
cd java-services
./mvnw clean install
```

#### Environment Variables

Create a `.env` file in each Java service directory:

```env
# Database Configuration
DATABASE_URL=postgresql://username:password@localhost:5432/velox_trading
DATABASE_USERNAME=velox
DATABASE_PASSWORD=velox123

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_GROUP_ID=velox-java-services

# Application Settings
SPRING_PROFILES_ACTIVE=development
LOGGING_LEVEL_COM_VELOX=DEBUG
```

#### Database Setup

```bash
# Create database
createdb velox_trading

# Run migrations (using Flyway for Java)
./mvnw flyway:migrate

# Seed initial data (optional)
./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed-data
```

### 3. API Gateway Setup (Node.js)

#### Install Dependencies

```bash
cd api-gateway
npm install
```

#### Environment Variables

Create a `.env` file in the API gateway directory:

```env
# Database Configuration
DATABASE_URL=postgresql://username:password@localhost:5432/velox_trading

# JWT Configuration
SECRET_KEY=your-secret-key-here
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30

# Java Services Configuration
MARKET_DATA_SERVICE_URL=http://localhost:8081
INDICATORS_SERVICE_URL=http://localhost:8082
RISK_MANAGEMENT_URL=http://localhost:8083

# Application Settings
DEBUG=true
LOG_LEVEL=debug
CORS_ORIGINS=["http://localhost:3000"]

# WebSocket Settings
WEBSOCKET_HEARTBEAT_INTERVAL=30
```

### 4. Frontend Setup

#### Install Dependencies

```bash
cd frontend
npm install
```

#### Environment Variables

Create a `.env` file in the frontend directory:

```env
REACT_APP_API_BASE_URL=http://localhost:8080/v1
REACT_APP_WS_URL=ws://localhost:8080/ws
REACT_APP_ENVIRONMENT=development
```

### 5. Docker Setup (Optional)

#### Using Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## Development Workflow

### 1. Java Microservices Development

#### Running Development Servers

```bash
# Market Data Processor
cd market-data-processor
./mvnw spring-boot:run

# Indicators Calculator
cd indicators-calculator
./mvnw spring-boot:run

# Risk Manager
cd risk-management
./mvnw spring-boot:run
```

#### Database Migrations

```bash
# Create new migration
./mvnw flyway:repair
./mvnw flyway:migrate

# Rollback migration
./mvnw flyway:undo
```

#### Running Tests

```bash
# Run all tests
./mvnw test

# Run specific test
./mvnw test -Dtest=MarketDataProcessorTest

# Run with coverage
./mvnw jacoco:report
```

#### Code Quality

```bash
# Format code
./mvnw spotless:apply

# Lint code
./mvnw checkstyle:check

# Static analysis
./mvnw spotbugs:check
```

### 2. API Gateway Development

#### Running Development Server

```bash
cd api-gateway
npm run dev
```

#### Running Tests

```bash
# Run all tests
npm test

# Run tests with coverage
npm run test:coverage

# Run E2E tests
npm run test:e2e
```

#### Code Quality

```bash
# Format code
npm run format

# Lint code
npm run lint

# Type checking
npm run type-check
```

### 3. Frontend Development

#### Running Development Server

```bash
cd frontend
npm start
```

#### Running Tests

```bash
# Run all tests
npm test

# Run tests with coverage
npm test -- --coverage

# Run E2E tests
npm run test:e2e
```

#### Code Quality

```bash
# Format code
npm run format

# Lint code
npm run lint

# Type checking
npm run type-check
```

## Implementation Guide

### 1. Core Components Implementation

#### User Authentication System

**File**: `api-gateway/src/main/java/com/velox/auth/`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .build();
    }
    
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
```

#### Broker Adapter Interface

**File**: `java-services/broker-adapter/src/main/java/com/velox/broker/`

```java
public interface BrokerAdapter {
    
    boolean connect() throws BrokerException;
    
    void disconnect() throws BrokerException;
    
    OrderResponse placeOrder(OrderRequest order) throws BrokerException;
    
    boolean cancelOrder(String orderId) throws BrokerException;
    
    List<Position> getPositions() throws BrokerException;
    
    OrderStatus getOrderStatus(String orderId) throws BrokerException;
    
    void subscribeToSymbols(List<String> symbols) throws BrokerException;
}
```

#### Strategy Framework

**File**: `java-services/strategy-engine/src/main/java/com/velox/strategy/`

```java
public abstract class BaseStrategy {
    
    protected String strategyId;
    protected Map<String, Object> parameters;
    protected boolean isRunning;
    protected Map<String, Position> positions;
    
    public BaseStrategy(String strategyId, Map<String, Object> parameters) {
        this.strategyId = strategyId;
        this.parameters = parameters;
        this.isRunning = false;
        this.positions = new ConcurrentHashMap<>();
    }
    
    public abstract boolean initialize() throws StrategyException;
    
    public abstract void onMarketData(MarketData data) throws StrategyException;
    
    public abstract void onTick(TickData tick) throws StrategyException;
    
    public abstract void cleanup() throws StrategyException;
    
    public void start() {
        this.isRunning = true;
    }
    
    public void stop() {
        this.isRunning = false;
    }
}
```

### 2. Technical Indicators Implementation

#### Moving Average Indicator

**File**: `java-services/indicators-calculator/src/main/java/com/velox/indicators/`

```java
@Component
public class SimpleMovingAverage implements Indicator {
    
    private final int period;
    private final CircularBuffer<Double> priceBuffer;
    
    public SimpleMovingAverage(int period) {
        this.period = period;
        this.priceBuffer = new CircularBuffer<>(period);
    }
    
    @Override
    public Double calculate(List<CandleData> candles) {
        if (candles.isEmpty()) {
            return null;
        }
        
        // Add latest price to buffer
        priceBuffer.add(candles.get(candles.size() - 1).getClose());
        
        if (priceBuffer.size() < period) {
            return null;
        }
        
        // Calculate SMA
        double sum = 0;
        for (Double price : priceBuffer) {
            sum += price;
        }
        
        return sum / period;
    }
    
    @Override
    public String getName() {
        return "SMA";
    }
}
```

### 3. Real-time Data Processing

#### Market Data Handler

**File**: `java-services/market-data-processor/src/main/java/com/velox/marketdata/`

```java
@Service
@Slf4j
public class MarketDataHandler {
    
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    private final TickProcessor tickProcessor;
    private final CandleBuilder candleBuilder;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public MarketDataHandler(TickProcessor tickProcessor,
                           CandleBuilder candleBuilder,
                           KafkaTemplate<String, Object> kafkaTemplate) {
        this.tickProcessor = tickProcessor;
        this.candleBuilder = candleBuilder;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void initializeConnections() {
        // Initialize broker connections
        List<BrokerConfig> brokers = brokerConfigRepository.findByActiveTrue();
        
        brokers.parallelStream().forEach(broker -> {
            try {
                connectToBroker(broker);
            } catch (Exception e) {
                log.error("Failed to connect to broker: {}", broker.getName(), e);
            }
        });
    }
    
    private void connectToBroker(BrokerConfig broker) {
        WebSocketSession session = switch (broker.getApiType()) {
            case "SMART_API" -> connectToSmartApi(broker);
            case "ZERODHA" -> connectToZerodha(broker);
            case "ANGEL" -> connectToAngel(broker);
            case "ICICI" -> connectToIcici(broker);
            default -> throw new IllegalArgumentException("Unsupported broker: " + broker.getApiType());
        };
        
        activeSessions.put(broker.getBrokerId(), session);
        log.info("Connected to broker: {}", broker.getName());
    }
    
    @KafkaListener(topics = "market-data-ticks")
    public void processTick(TickData tick) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Process tick
            TickData processedTick = tickProcessor.process(tick);
            
            // Update forming candle
            CandleData candle = candleBuilder.addTick(processedTick);
            
            // Send to Kafka
            kafkaTemplate.send("market-data-candles", candle);
            
            // Update metrics
            tickCounter.increment();
            
        } catch (Exception e) {
            log.error("Error processing tick: {}", tick, e);
            errorCounter.increment();
        } finally {
            sample.stop(Timer.builder("tick.processing.time").register(meterRegistry));
        }
    }
}
```

### 4. Risk Management System

#### Risk Manager

**File**: `java-services/risk-management/src/main/java/com/velox/risk/`

```java
@Service
@Slf4j
public class RiskManager {
    
    private final RiskRuleProcessor ruleProcessor;
    private final PositionMonitor positionMonitor;
    private final EmergencyExitHandler emergencyExitHandler;
    private final RiskAlertManager alertManager;
    private final Map<String, UserRiskState> userRiskStates = new ConcurrentHashMap<>();
    
    public RiskManager(RiskRuleProcessor ruleProcessor,
                        PositionMonitor positionMonitor,
                        EmergencyExitHandler emergencyExitHandler,
                        RiskAlertManager alertManager) {
        this.ruleProcessor = ruleProcessor;
        this.positionMonitor = positionMonitor;
        this.emergencyExitHandler = emergencyExitHandler;
        this.alertManager = alertManager;
    }
    
    @KafkaListener(topics = "position-updates")
    public void processPositionUpdate(PositionUpdate update) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Update position risk data
            updatePositionRisk(update);
            
            // Get user risk state
            UserRiskState riskState = getOrCreateUserRiskState(update.getUserId());
            
            // Evaluate all risk rules
            RiskEvaluationResult result = evaluateRiskRules(update, riskState);
            
            // Process risk evaluation result
            processRiskResult(result, update);
            
            // Update metrics
            positionUpdateCounter.increment();
            
        } catch (Exception e) {
            log.error("Error processing position update: {}", update, e);
            errorCounter.increment();
        } finally {
            sample.stop(Timer.builder("risk.position.evaluation.time").register(meterRegistry));
        }
    }
    
    @KafkaListener(topics = "order-events")
    public OrderValidationResult validateOrder(OrderEvent orderEvent) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            String userId = orderEvent.getUserId();
            String symbol = orderEvent.getSymbol();
            
            // Get user risk state
            UserRiskState riskState = getOrCreateUserRiskState(userId);
            
            // Pre-trade risk validation
            OrderValidationResult validation = validateOrderRisk(orderEvent, riskState);
            
            // Check compliance
            ComplianceResult compliance = complianceChecker.checkOrderCompliance(orderEvent);
            
            // Combine results
            OrderValidationResult finalResult = combineValidationResults(validation, compliance);
            
            orderValidationCounter.increment();
            
            return finalResult;
            
        } catch (Exception e) {
            log.error("Error validating order: {}", orderEvent, e);
            errorCounter.increment();
            return OrderValidationResult.rejected("Internal risk validation error");
        } finally {
            sample.stop(Timer.builder("risk.order.validation.time").register(meterRegistry));
        }
    }
    
    private void processRiskResult(RiskEvaluationResult result, PositionUpdate update) {
        if (result.hasViolations()) {
            // Handle risk violations
            for (RiskViolation violation : result.getViolations()) {
                handleRiskViolation(violation, update);
            }
            
            // Send risk alert
            alertManager.sendRiskAlert(result);
        }
        
        // Check for emergency exit conditions
        if (result.requiresEmergencyExit()) {
            emergencyExitHandler.triggerEmergencyExit(update.getUserId(), update.getSymbol());
        }
    }
}
```

## Testing Strategy

### 1. Unit Testing

#### Example Test for Strategy

**File**: `java-services/strategy-engine/src/test/java/com/velox/strategy/`

```java
@ExtendWith(MockitoExtension.class)
class BaseStrategyTest {
    
    @Mock
    private MarketDataService marketDataService;
    
    @InjectMocks
    private TestStrategy testStrategy;
    
    @Test
    void testStrategyInitialization() {
        // Given
        Map<String, Object> parameters = Map.of(
            "fast_period", 10,
            "slow_period", 20,
            "symbol", "NIFTY"
        );
        
        // When
        boolean result = testStrategy.initialize();
        
        // Then
        assertTrue(result);
        assertTrue(testStrategy.isInitialized());
        assertEquals(parameters, testStrategy.getParameters());
    }
    
    @Test
    void testSignalGeneration() throws StrategyException {
        // Given
        testStrategy.initialize();
        
        MarketData marketData = MarketData.builder()
            .symbol("NIFTY")
            .price(18500.0)
            .timestamp(Instant.now())
            .build();
        
        // When
        Signal signal = testStrategy.onMarketData(marketData);
        
        // Then
        assertNotNull(signal);
        assertTrue(signal.getAction().isValid());
    }
}
```

### 2. Integration Testing

#### Example Test for Broker Integration

**File**: `java-services/broker-adapter/src/test/java/com/velox/broker/`

```java
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class SmartApiAdapterIntegrationTest {
    
    @Autowired
    private SmartApiAdapter smartApiAdapter;
    
    @Test
    void testBrokerConnection() {
        // Given
        BrokerConfig config = BrokerConfig.builder()
            .apiType("SMART_API")
            .credentials(getTestCredentials())
            .build();
        
        // When
        boolean result = smartApiAdapter.connect();
        
        // Then
        assertTrue(result);
        assertTrue(smartApiAdapter.isConnected());
    }
    
    @Test
    void testOrderPlacement() throws BrokerException {
        // Given
        smartApiAdapter.connect();
        
        OrderRequest order = OrderRequest.builder()
            .symbol("NIFTY")
            .transactionType(TransactionType.BUY)
            .quantity(50)
            .orderType(OrderType.MARKET)
            .build();
        
        // When
        OrderResponse response = smartApiAdapter.placeOrder(order);
        
        // Then
        assertNotNull(response);
        assertNotNull(response.getOrderId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }
    
    private Map<String, String> getTestCredentials() {
        return Map.of(
            "api_key", "test_key",
            "client_id", "test_client",
            "password", "test_password",
            "totp", "test_totp"
        );
    }
}
```

### 3. Performance Testing

#### Example Test for Market Data Processing

**File**: `java-services/market-data-processor/src/test/java/com/velox/marketdata/`

```java
@SpringBootTest
class MarketDataProcessorPerformanceTest {
    
    @Autowired
    private MarketDataHandler marketDataHandler;
    
    @Test
    void testTickProcessingLatency() {
        // Given
        List<TickData> ticks = generateTestTicks(10000);
        
        // When
        long startTime = System.nanoTime();
        
        for (TickData tick : ticks) {
            marketDataHandler.processTick(tick);
        }
        
        long endTime = System.nanoTime();
        
        // Then
        double avgLatencyMs = (endTime - startTime) / 1_000_000.0 / ticks.size();
        
        // Assert sub-millisecond processing
        assertTrue(avgLatencyMs < 0.5, 
            "Average tick processing latency should be less than 0.5ms, but was: " + avgLatencyMs + "ms");
    }
    
    @Test
    void testThroughput() {
        // Given
        int tickCount = 10000;
        List<TickData> ticks = generateTestTicks(tickCount);
        
        // When
        long startTime = System.currentTimeMillis();
        
        for (TickData tick : ticks) {
            marketDataHandler.processTick(tick);
        }
        
        long endTime = System.currentTimeMillis();
        
        // Then
        double durationSeconds = (endTime - startTime) / 1000.0;
        double throughputTicksPerSecond = tickCount / durationSeconds;
        
        // Assert high throughput
        assertTrue(throughputTicksPerSecond > 1000, 
            "Throughput should be greater than 1000 ticks/second, but was: " + throughputTicksPerSecond);
    }
    
    private List<TickData> generateTestTicks(int count) {
        List<TickData> ticks = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ticks.add(TickData.builder()
                .symbol("NIFTY")
                .price(18500.0 + (i % 100))
                .volume(1000)
                .timestamp(Instant.now())
                .build());
        }
        
        return ticks;
    }
}
```

## Deployment Guide

### 1. Production Environment Setup

#### Environment Variables

```env
# Production Configuration
SPRING_PROFILES_ACTIVE=production
LOGGING_LEVEL_COM_VELOX=INFO

# Database Configuration
DATABASE_URL=postgresql://user:password@db-host:5432/velox_trading
DATABASE_POOL_SIZE=20

# Kafka Configuration
KAFKA_BOOTSTRAP_SERVERS=kafka-cluster:9092
KAFKA_GROUP_ID=velox-java-services-prod

# Security
SECRET_KEY=production-secret-key
ALLOWED_HOSTS=["api.veloxtrading.com"]

# Monitoring
SENTRY_DSN=your-sentry-dsn
PROMETHEUS_ENABLED=true
```

#### Docker Configuration

**File**: `java-services/market-data-processor/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY target/market-data-processor-*.jar app.jar

# JVM optimizations for high-performance
ENV JAVA_OPTS="-XX:+UseVirtualThreads -XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+UseTransparentHugePages -Xms1g -Xmx4g -XX:MaxDirectMemorySize=1g"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

### 2. Monitoring and Logging

#### Application Monitoring

```java
@Component
public class PerformanceMetrics {
    
    private final MeterRegistry meterRegistry;
    private final Timer tickProcessingTimer;
    private final Counter tickCounter;
    private final Counter errorCounter;
    
    public PerformanceMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.tickProcessingTimer = Timer.builder("tick.processing.time")
            .description("Time taken to process a single tick")
            .register(meterRegistry);
        this.tickCounter = Counter.builder("ticks.processed")
            .description("Number of ticks processed")
            .register(meterRegistry);
        this.errorCounter = Counter.builder("processing.errors")
            .description("Number of processing errors")
            .register(meterRegistry);
    }
    
    public void recordTickProcessing(Duration duration) {
        tickProcessingTimer.record(duration);
        tickCounter.increment();
    }
    
    public void recordError() {
        errorCounter.increment();
    }
}
```

#### Logging Configuration

```xml
<!-- logback-spring.xml -->
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
            <providers>
                <provider class="net.logstash.logback.encoder.LogstashMarkersJsonProvider"/>
                <provider class="net.logstash.logback.encoder.LogstashMdcJsonProvider"/>
            </providers>
        </encoder>
    </appender>
    
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/velox-java-services.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/velox-java-services.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </root>
    
    <logger name="com.velox" level="DEBUG" additivity="false">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </logger>
</configuration>
```

## Best Practices

### 1. Code Organization

- Follow the project structure defined in `project_structure.md`
- Use clear and descriptive naming conventions
- Implement proper error handling and logging
- Write comprehensive tests for all components

### 2. Security

- Never commit sensitive information like API keys
- Use environment variables for configuration
- Implement proper authentication and authorization
- Validate all input data

### 3. Performance

- Use virtual threads for concurrent operations
- Implement proper caching strategies
- Optimize database queries
- Monitor system performance

### 4. Documentation

- Write clear docstrings for all functions and classes
- Keep API documentation up to date
- Document complex business logic
- Provide examples for common use cases

## Troubleshooting

### Common Issues

1. **Database Connection Issues**
   - Check database URL configuration
   - Verify database is running
   - Check network connectivity

2. **Kafka Connection Issues**
   - Verify Kafka cluster is running
   - Check topic configuration
   - Review consumer group settings

3. **Performance Issues**
   - Monitor resource usage
   - Check database query performance
   - Review code for bottlenecks

### Debugging Tips

1. Use logging extensively
2. Implement health check endpoints
3. Monitor system metrics
4. Use profiling tools for performance analysis

This development guide provides a comprehensive foundation for implementing the algotrading system with Java microservices. Follow these guidelines and best practices to ensure a robust, scalable, and maintainable application.