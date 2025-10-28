# Project Structure

## Overview

This document outlines the complete directory structure and key files for the multi-user algotrading system with Java microservices for high-performance components.

## Root Directory Structure

```
velox-algotrading/
├── java-services/               # Java microservices for high-performance components
├── api-gateway/                # Node.js/FastAPI server for API gateway
├── frontend/                    # React frontend
├── database/                   # Database migrations and schemas
├── docs/                       # Documentation
├── scripts/                    # Utility scripts
├── docker/                     # Docker configurations
├── config/                     # Configuration files
├── requirements.txt              # Node.js dependencies
├── package.json               # Node.js dependencies and scripts
└── .env.example               # Environment variables template
```

## Java Services Structure

```
java-services/
├── market-data-processor/         # Market data processing microservice
│   ├── src/main/java/com/velox/marketdata/
│   │   ├── MarketDataProcessorApplication.java
│   │   ├── config/
│   │   │   ├── KafkaConfig.java
│   │   │   ├── DatabaseConfig.java
│   │   │   └── WebSocketConfig.java
│   │   ├── controller/
│   │   │   ├── MarketDataController.java
│   │   │   └── WebSocketController.java
│   │   ├── service/
│   │   │   ├── TickProcessor.java
│   │   │   ├── CandleBuilder.java
│   │   │   └── MarketDataService.java
│   │   ├── model/
│   │   │   ├── TickData.java
│   │   │   ├── CandleData.java
│   │   │   └── FormingCandle.java
│   │   ├── repository/
│   │   │   ├── MarketDataRepository.java
│   │   │   └── CandleRepository.java
│   │   └── util/
│   │       ├── DataValidator.java
│   │       └── DataNormalizer.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── logback-spring.xml
│   ├── src/test/java/com/velox/marketdata/
│   │   ├── MarketDataProcessorTest.java
│   │   ├── TickProcessorTest.java
│   │   └── CandleBuilderTest.java
│   ├── Dockerfile
│   ├── pom.xml
│   └── build.gradle
├── indicators-calculator/          # Technical indicators calculation microservice
│   ├── src/main/java/com/velox/indicators/
│   │   ├── IndicatorsCalculatorApplication.java
│   │   ├── config/
│   │   │   ├── KafkaConfig.java
│   │   │   ├── CacheConfig.java
│   │   │   └── IndicatorConfig.java
│   │   ├── controller/
│   │   │   ├── IndicatorController.java
│   │   │   └── IndicatorUpdateController.java
│   │   ├── service/
│   │   │   ├── IndicatorCalculator.java
│   │   │   ├── RealTimeIndicatorEngine.java
│   │   │   └── IndicatorCache.java
│   │   ├── algorithm/
│   │   │   ├── IndicatorAlgorithm.java
│   │   │   ├── MovingAverage.java
│   │   │   ├── EMA.java
│   │   │   ├── SMA.java
│   │   │   ├── RSI.java
│   │   │   ├── MACD.java
│   │   │   ├── BollingerBands.java
│   │   │   └── CustomIndicator.java
│   │   ├── model/
│   │   │   ├── IndicatorValue.java
│   │   │   ├── IndicatorUpdate.java
│   │   │   └── SymbolIndicatorData.java
│   │   └── repository/
│   │       └── IndicatorRepository.java
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── logback-spring.xml
│   ├── src/test/java/com/velox/indicators/
│   │   ├── IndicatorCalculatorTest.java
│   │   ├── RealTimeIndicatorEngineTest.java
│   │   └── MovingAverageTest.java
│   ├── Dockerfile
│   ├── pom.xml
│   └── build.gradle
└── risk-management/              # Risk management microservice
    ├── src/main/java/com/velox/risk/
    │   ├── RiskManagementApplication.java
    │   ├── config/
    │   │   ├── KafkaConfig.java
    │   │   ├── DatabaseConfig.java
    │   │   └── RiskConfig.java
    │   ├── controller/
    │   │   ├── RiskController.java
    │   │   ├── OrderValidationController.java
    │   │   └── EmergencyExitController.java
    │   ├── service/
    │   │   ├── RiskManager.java
    │   │   ├── PositionMonitor.java
    │   │   ├── RiskRuleProcessor.java
    │   │   ├── EmergencyExitHandler.java
    │   │   └── RiskAlertManager.java
    │   ├── rule/
    │   │   ├── RiskRule.java
    │   │   ├── DailyLossLimitRule.java
    │   │   ├── MaxDrawdownRule.java
    │   │   ├── PositionSizeRule.java
    │   │   └── StopLossRule.java
    │   ├── model/
    │   │   ├── RiskViolation.java
    │   │   ├── RiskEvaluationResult.java
    │   │   ├── UserRiskState.java
    │   │   ├── PositionRiskData.java
    │   │   └── OrderValidationResult.java
    │   └── repository/
    │       ├── RiskRuleRepository.java
    │       └── UserRiskStateRepository.java
    ├── src/main/resources/
    │   ├── application.yml
    │   └── logback-spring.xml
    ├── src/test/java/com/velox/risk/
    │   ├── RiskManagerTest.java
    │   ├── PositionMonitorTest.java
    │   └── RiskRuleProcessorTest.java
    ├── Dockerfile
    ├── pom.xml
    └── build.gradle
```

## API Gateway Structure

```
api-gateway/
├── src/
│   ├── main.js                 # FastAPI application entry point
│   ├── config.js               # Configuration settings
│   ├── database.js             # Database connection setup
│   ├── dependencies.js          # FastAPI dependencies
│   ├── middleware.js           # Custom middleware
│   └── websocket.js            # WebSocket handler
├── api/                       # API routes
│   ├── __init__.py
│   ├── auth.py               # Authentication endpoints
│   ├── users.py              # User management endpoints
│   ├── brokers.py            # Broker management endpoints
│   ├── strategies.py          # Strategy management endpoints
│   ├── trading.py            # Trading endpoints
│   ├── analytics.py           # Analytics endpoints
│   └── websocket.py          # WebSocket endpoints
├── core/                       # Core business logic
│   ├── __init__.py
│   ├── auth.py               # Authentication logic
│   ├── security.py            # Security utilities
│   ├── exceptions.py          # Custom exceptions
│   └── logging.py            # Logging configuration
├── models/                     # Database models
│   ├── __init__.py
│   ├── user.py               # User model
│   ├── broker.py             # Broker model
│   ├── strategy.py            # Strategy model
│   ├── trade.py              # Trade model
│   ├── position.py            # Position model
│   ├── market_data.py         # Market data model
│   └── risk_setting.py        # Risk settings model
├── schemas/                    # Pydantic schemas
│   ├── __init__.py
│   ├── user.py               # User schemas
│   ├── broker.py             # Broker schemas
│   ├── strategy.py            # Strategy schemas
│   ├── trade.py              # Trade schemas
│   ├── market_data.py         # Market data schemas
│   └── broker.py              # Broker schemas
├── services/                   # Business logic services
│   ├── __init__.py
│   ├── user_service.py       # User management service
│   ├── broker_service.py      # Broker management service
│   ├── strategy_service.py    # Strategy management service
│   ├── trading_service.py     # Trading service
│   └── analytics_service.py   # Analytics service
├── tests/                      # Test files
│   ├── __init__.py
│   ├── conftest.py           # Test configuration
│   ├── test_api/             # API tests
│   ├── test_services/         # Service tests
│   └── test_trading/          # Trading tests
├── requirements.txt              # Python dependencies
└── Dockerfile                   # Docker configuration
```

## Frontend Structure

```
frontend/
├── public/
│   ├── index.html             # HTML template
│   ├── favicon.ico            # Favicon
│   └── manifest.json         # PWA manifest
├── src/
│   ├── index.js               # React app entry point
│   ├── App.js                 # Main App component
│   ├── index.css              # Global styles
│   ├── components/            # Reusable components
│   │   ├── common/            # Common UI components
│   │   │   ├── Header.js
│   │   │   ├── Sidebar.js
│   │   │   ├── Loading.js
│   │   │   └── ErrorBoundary.js
│   │   ├── charts/            # Chart components
│   │   │   ├── CandlestickChart.js
│   │   │   ├── IndicatorChart.js
│   │   │   └── PnLChart.js
│   │   ├── forms/             # Form components
│   │   │   ├── LoginForm.js
│   │   │   ├── StrategyForm.js
│   │   │   └── BrokerForm.js
│   │   └── tables/            # Table components
│   │       ├── TradesTable.js
│   │       ├── PositionsTable.js
│   │       └── OrdersTable.js
│   ├── pages/                 # Page components
│   │   ├── Dashboard.js       # Main dashboard
│   │   ├── Trading.js         # Trading interface
│   │   ├── Strategies.js       # Strategy management
│   │   ├── Analytics.js       # Analytics page
│   │   ├── Settings.js        # Settings page
│   │   ├── Login.js           # Login page
│   │   └── InvestorView.js    # Investor view
│   ├── hooks/                 # Custom React hooks
│   │   ├── useAuth.js         # Authentication hook
│   │   ├── useWebSocket.js    # WebSocket hook
│   │   ├── useMarketData.js   # Market data hook
│   │   └── useTrading.js      # Trading hook
│   ├── services/              # API services
│   │   ├── api.js             # API client setup
│   │   ├── authService.js     # Authentication service
│   │   ├── tradingService.js  # Trading service
│   │   ├── strategyService.js # Strategy service
│   │   └── analyticsService.js # Analytics service
│   ├── store/                 # State management
│   │   ├── index.js           # Store setup
│   │   ├── authSlice.js       # Auth state
│   │   ├── tradingSlice.js    # Trading state
│   │   ├── marketSlice.js     # Market data state
│   │   └── uiSlice.js         # UI state
│   ├── utils/                 # Utility functions
│   │   ├── constants.js       # Constants
│   │   ├── helpers.js         # Helper functions
│   │   ├── formatters.js      # Data formatters
│   │   └── validators.js      # Form validators
│   └── styles/                # CSS modules
│       ├── globals.css        # Global styles
│       ├── variables.css      # CSS variables
│       └── components/        # Component-specific styles
└── package.json               # Node.js dependencies
```

## Database Structure

```
database/
├── migrations/                # Database migration files
│   ├── 001_initial_schema.sql
│   ├── 002_add_broker_tables.sql
│   ├── 003_add_strategy_tables.sql
│   ├── 004_add_trading_tables.sql
│   └── 005_add_risk_tables.sql
├── seeds/                     # Seed data
│   ├── users.sql
│   ├── brokers.sql
│   └── strategies.sql
└── schemas/                   # Database schema definitions
    ├── users.sql
    ├── brokers.sql
    ├── strategies.sql
    ├── trades.sql
    ├── positions.sql
    ├── market_data.sql
    └── risk_settings.sql
```

## Configuration Structure

```
config/
├── development.json           # Development environment config
├── production.json            # Production environment config
├── testing.json              # Testing environment config
├── brokers.json              # Broker configurations
└── strategies.json            # Default strategy configurations
```

## Docker Structure

```
docker/
├── java-services/
│   ├── market-data-processor/Dockerfile
│   ├── indicators-calculator/Dockerfile
│   └── risk-management/Dockerfile
├── api-gateway/
│   └── Dockerfile
├── frontend/
│   └── Dockerfile
├── database/
│   └── Dockerfile
├── nginx/
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml           # Multi-container setup
├── docker-compose.kafka.yml   # Kafka-specific setup
└── docker-compose.ui.yml      # UI services setup
```

## Scripts Structure

```
scripts/
├── setup.sh                  # Environment setup script
├── start-dev.sh              # Development startup script
├── build.sh                  # Build script
├── deploy.sh                 # Deployment script
├── backup.sh                 # Database backup script
├── test.sh                   # Test runner script
└── migrate.sh                # Database migration script
```

## Documentation Structure

```
docs/
├── java_microservices_architecture.md    # Java microservices architecture
├── java_market_data_service.md          # Market data processor design
├── java_indicators_service.md           # Indicators calculator design
├── java_risk_management_service.md       # Risk manager design
├── java_implementation_summary.md         # Implementation summary
├── nodejs_architecture.md              # API gateway architecture
├── api_specification.md                 # API specification
├── realtime_indicators_optimization.md   # Indicators optimization
├── development_guide.md                # Development guide
├── phase_1_foundation.md              # Phase 1 plan
├── phase_2_core_trading_engine.md     # Phase 2 plan
├── phase_3_risk_position_management.md # Phase 3 plan
├── phase_4_frontend_dashboard.md        # Phase 4 plan
├── phase_5_advanced_features.md         # Phase 5 plan
├── phase_6_testing_deployment.md       # Phase 6 plan
├── project_structure.md                 # This file
└── README.md                          # Project overview
```

## Key Files Description

### Java Microservices

1. **Market Data Processor** (`java-services/market-data-processor/`)
   - Handles real-time tick data processing
   - Implements forming candle management
   - Provides high-throughput data streaming

2. **Indicators Calculator** (`java-services/indicators-calculator/`)
   - Calculates technical indicators in real-time
   - Implements forming candle approach
   - Provides sub-millisecond calculation times

3. **Risk Management** (`java-services/risk-management/`)
   - Enforces trading risk limits
   - Provides real-time position monitoring
   - Implements emergency exit mechanisms

### API Gateway

1. **FastAPI Server** (`api-gateway/`)
   - Provides REST API endpoints
   - Handles WebSocket connections
   - Routes requests to Java microservices

### Frontend

1. **React Application** (`frontend/`)
   - Provides user interface
   - Implements real-time data visualization
   - Supports multiple user roles

### Database

1. **PostgreSQL with TimescaleDB** (`database/`)
   - Stores relational data
   - Optimized for time-series data
   - Provides historical data storage

## Development Workflow

### Java Services Development

```bash
# Build all Java services
cd java-services
./mvnw clean install

# Run specific service
cd market-data-processor
./mvnw spring-boot:run

# Run tests
./mvnw test
```

### API Gateway Development

```bash
# Install dependencies
cd api-gateway
npm install

# Run development server
npm run dev

# Run tests
npm test
```

### Frontend Development

```bash
# Install dependencies
cd frontend
npm install

# Run development server
npm start

# Run tests
npm test
```

This project structure provides a solid foundation for developing a scalable, maintainable, and feature-rich algotrading system with Java microservices for optimal performance.