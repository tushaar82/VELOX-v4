# Velox Algotrading System

A high-performance, multi-user algotrading system built with Java microservices for optimal performance, React frontend, and real-time data processing capabilities.

## 🚀 Quick Start

### Prerequisites

- **Java 21+** - For Java microservices
- **Node.js 18+** - For API Gateway and Frontend
- **Docker & Docker Compose** - For infrastructure services
- **PostgreSQL with TimescaleDB** - For time-series data storage
- **Apache Kafka** - For real-time messaging

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/velox-algotrading/velox-algotrading.git
   cd velox-algotrading
   ```

2. **Run the setup script**
   
   **On Linux/macOS:**
   ```bash
   chmod +x scripts/setup.sh
   ./scripts/setup.sh
   ```
   
   **On Windows:**
   ```cmd
   scripts\setup.bat
   ```

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your configuration
   ```

4. **Start the system**
   ```bash
   docker-compose up -d
   ```

## 📊 Architecture Overview

### Microservices Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Market Data    │    │  Indicators     │    │  Risk           │
│  Processor      │    │  Calculator     │    │  Management     │
│  (Port 8081)   │    │  (Port 8082)   │    │  (Port 8083)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │  API Gateway    │
                    │  (Port 8000)   │
                    └─────────────────┘
                                 │
                    ┌─────────────────┐
                    │  Frontend       │
                    │  (Port 3000)   │
                    └─────────────────┘
```

### Technology Stack

- **Backend Services**: Java 21, Spring Boot 3.2, Kafka, PostgreSQL with TimescaleDB
- **API Gateway**: Node.js, FastAPI, WebSocket
- **Frontend**: React 18, TypeScript, Redux
- **Infrastructure**: Docker, Docker Compose, Nginx
- **Monitoring**: Prometheus, Spring Boot Actuator

## 🏗️ Project Structure

```
velox-algotrading/
├── java-services/              # Java microservices
│   ├── market-data-processor/  # Real-time market data processing
│   ├── indicators-calculator/   # Technical indicators calculation
│   └── risk-management/       # Risk management and monitoring
├── api-gateway/              # Node.js API gateway
├── frontend/                 # React frontend application
├── database/                 # Database migrations and schemas
├── docker/                   # Docker configurations
├── scripts/                  # Utility scripts
├── docs/                     # Documentation
└── config/                   # Configuration files
```

## 🔧 Development

### Running Services Individually

**Java Services:**
```bash
# Market Data Processor
cd java-services/market-data-processor
./mvnw spring-boot:run

# Indicators Calculator
cd java-services/indicators-calculator
./mvnw spring-boot:run

# Risk Management
cd java-services/risk-management
./mvnw spring-boot:run
```

**API Gateway:**
```bash
cd api-gateway
npm run dev
```

**Frontend:**
```bash
cd frontend
npm start
```

### Testing

```bash
# Run all tests
npm test

# Run Java service tests
cd java-services
./mvnw test

# Run API Gateway tests
cd api-gateway
npm test

# Run Frontend tests
cd frontend
npm test
```

## 📚 Documentation

- [Architecture Overview](docs/architecture.md)
- [API Specification](docs/api_specification.md)
- [Database Schema](docs/database_schema.md)
- [Development Guide](docs/development_guide.md)
- [Project Structure](docs/project_structure.md)

## 🚦 Phase 1 Implementation Status

### ✅ Completed

- [x] Project repository structure
- [x] Environment configuration files
- [x] Maven/Gradle build system
- [x] PostgreSQL with TimescaleDB setup
- [x] Database migration scripts
- [x] Spring Boot application structure
- [x] Application properties configuration
- [x] Logging and monitoring setup
- [x] Spring Boot Actuator configuration
- [x] Kafka cluster setup (3-node)
- [x] ZooKeeper ensemble
- [x] Docker compose files

### 🔄 In Progress

- [ ] Java microservices implementation
- [ ] API Gateway development
- [ ] Frontend React application
- [ ] Authentication and authorization
- [ ] REST API endpoints
- [ ] WebSocket real-time data

### 📋 Planned

- [ ] Performance optimization
- [ ] Security hardening
- [ ] Comprehensive testing
- [ ] Documentation completion
- [ ] CI/CD pipeline setup

## 🔌 Service Endpoints

### API Gateway
- **Base URL**: `http://localhost:8000`
- **Health Check**: `http://localhost:8000/health`
- **API Documentation**: `http://localhost:8000/docs`

### Java Services
- **Market Data Processor**: `http://localhost:8081/api/v1`
- **Indicators Calculator**: `http://localhost:8082/api/v1`
- **Risk Management**: `http://localhost:8083/api/v1`

### Frontend
- **Application**: `http://localhost:3000`
- **Development Server**: `http://localhost:3000`

### Infrastructure
- **Kafka UI**: `http://localhost:8080`
- **PostgreSQL**: `localhost:5432`
- **Redis**: `localhost:6379`

## 🛠️ Configuration

### Environment Variables

Key environment variables in `.env`:

```bash
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=velox_algotrading
DB_USER=velox_user
DB_PASSWORD=your_secure_password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=localhost:9092,localhost:9093,localhost:9094

# Services
MARKET_DATA_PROCESSOR_PORT=8081
INDICATORS_CALCULATOR_PORT=8082
RISK_MANAGEMENT_PORT=8083
API_GATEWAY_PORT=8000
FRONTEND_PORT=3000
```

## 🔍 Monitoring

### Health Checks

All services expose health endpoints:

```bash
# Java Services
curl http://localhost:8081/api/v1/actuator/health
curl http://localhost:8082/api/v1/actuator/health
curl http://localhost:8083/api/v1/actuator/health

# API Gateway
curl http://localhost:8000/health
```

### Metrics

- **Prometheus Metrics**: Available on each Java service at `/actuator/prometheus`
- **Spring Boot Actuator**: Available at `/actuator` endpoints
- **Kafka Monitoring**: Available via Kafka UI at `http://localhost:8080`

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

For support and questions:

- 📧 Email: support@velox-algotrading.com
- 📖 Documentation: [docs/](docs/)
- 🐛 Issues: [GitHub Issues](https://github.com/velox-algotrading/velox-algotrading/issues)

---

**Velox Algotrading** - High-performance trading infrastructure for the modern trader.