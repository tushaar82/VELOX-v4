# Velox Algotrading System

## Overview

Velox is a comprehensive multi-user algotrading system specialized in NSE India intraday options and stock trading. The system is designed to be robust, scalable, and support multiple brokers and trading strategies with real-time tick-by-tick data processing.

## Technology Stack

### Backend
- **Java 21**: High-performance microservices for core trading components
- **Spring Boot 3.x**: Enterprise-grade application framework
- **Node.js/FastAPI**: API gateway for existing components
- **PostgreSQL with TimescaleDB**: Time-series optimized database
- **Apache Kafka**: High-throughput messaging system

### Frontend
- **React**: Modern user interface
- **TypeScript**: Type-safe development
- **Redux**: State management
- **WebSocket**: Real-time data streaming

### Infrastructure
- **Docker**: Containerization
- **Kubernetes**: Orchestration
- **Prometheus**: Monitoring
- **Grafana**: Visualization

## System Architecture

The system consists of multiple Java microservices for high-performance components:

1. **Market Data Processor**: Handles real-time tick data processing
2. **Indicators Calculator**: Calculates technical indicators with forming candle optimization
3. **Risk Manager**: Manages trading risk and position limits
4. **API Gateway**: Routes requests to appropriate services
5. **Frontend**: Provides user interface for trading and analytics

## Key Features

- **Real-time Data Processing**: Sub-millisecond tick processing
- **Technical Indicators**: Comprehensive indicator library with real-time updates
- **Risk Management**: Advanced risk controls and position monitoring
- **Multi-broker Support**: Integration with multiple trading brokers
- **Strategy Framework**: Custom strategy development and execution
- **Analytics Dashboard**: Comprehensive trading analytics and reporting
- **Multi-user Support**: Role-based access control for different user types

## Documentation

For detailed documentation, see the `docs/` directory:

- [Architecture](docs/architecture.md)
- [Java Microservices](docs/java_microservices_architecture.md)
- [API Specification](docs/api_specification.md)
- [Development Guide](docs/development_guide.md)
- [Implementation Roadmap](docs/implementation_roadmap.md)
- [Project Structure](docs/project_structure.md)

## Getting Started

### Prerequisites
- Java 21 or higher
- Node.js 16.x or higher
- PostgreSQL 15 or higher
- Docker and Docker Compose
- Git

### Installation

1. Clone the repository
2. Set up development environment
3. Configure database and Kafka
4. Run the services

For detailed installation instructions, see the [Development Guide](docs/development_guide.md).

## Contributing

We welcome contributions! Please see the [Development Guide](docs/development_guide.md) for guidelines on how to contribute to the project.

## License

This project is licensed under the MIT License. See the LICENSE file for details.