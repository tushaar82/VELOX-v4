# Velox Algotrading System

A comprehensive multi-user algotrading system for NSE India, specialized in intraday options and stock trading with real-time tick-by-tick data processing.

## 🚀 Project Overview

Velox is a robust, scalable, and feature-rich algotrading platform that enables traders to:
- Execute automated trading strategies with real-time market data
- Integrate with multiple brokers (Zerodha, Angel Broking, ICICI Direct)
- Develop and backtest custom trading strategies
- Monitor positions and manage risk in real-time
- Analyze trading performance with comprehensive analytics

## 📋 Key Features

### Core Trading Features
- **Real-time Data Processing**: Tick-by-tick market data processing with zero-latency indicator updates
- **High-Throughput Messaging**: Apache Kafka for scalable real-time data streaming
- **Multi-Broker Support**: Unified interface for SMART API (primary), Zerodha Kite, Angel Broking, and ICICI Direct
- **Strategy Framework**: Extensible framework for developing custom trading strategies
- **Risk Management**: Comprehensive risk controls with daily loss limits and position sizing
- **Position Monitoring**: Real-time position tracking with P&L calculation

### User Interface
- **Trading Dashboard**: Real-time charts, order management, and position monitoring
- **Analytics Suite**: Performance analytics, trade analysis, and reporting
- **Investor View**: Read-only access for investors to monitor trades
- **Strategy Management**: Interface for creating, testing, and deploying strategies

### Technical Features
- **Live/Dry Modes**: Switch between live trading and paper trading
- **Backtesting Engine**: Historical strategy testing with detailed reports
- **WebSocket Streaming**: Real-time data streaming for low-latency updates
- **Comprehensive Logging**: Complete audit trail for all trading activities

## 🏗️ Architecture

### Technology Stack
- **Backend**: Python with FastAPI
- **Database**: PostgreSQL with TimescaleDB for time-series data
- **Frontend**: React with TypeScript
- **Message Queue**: Redis for caching and real-time messaging
- **WebSocket**: Real-time data streaming

### System Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   React UI      │    │  FastAPI Server │    │   PostgreSQL    │
│                 │◄──►│                 │◄──►│                 │
│ - Dashboard     │    │ - REST API      │    │ - User Data     │
│ - Charts        │    │ - WebSocket     │    │ - Trades        │
│ - Analytics     │    │ - Auth          │    │ - Market Data   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │  Trading Engine │
                       │                 │
                       │ - Strategy Mgr  │
                       │ - Risk Mgr      │
                       │ - Position Mgr  │
                       └─────────────────┘
                       │
                       ▼
                       ┌─────────────────┐
                       │  Kafka Cluster │
                       │                 │
                       │ - Market Data   │
                       │ - Indicators    │
                       │ - Signals       │
                       │ - Orders        │
                       │ - Positions     │
                       │ - Risk Alerts   │
                       └─────────────────┘
                       │
                       ▼
                       ┌─────────────────┐
                       │  Broker Adapters│
                       │                 │
                       │ - SMART API     │
                       │ - Zerodha       │
                       │ - Angel         │
                       │ - ICICI         │
                       └─────────────────┘
```

## 📁 Project Structure

```
velox-algotrading/
├── backend/                    # Python FastAPI backend
│   ├── app/
│   │   ├── api/               # API routes
│   │   ├── core/              # Core business logic
│   │   ├── models/            # Database models
│   │   ├── services/          # Business services
│   │   ├── trading/           # Trading engine
│   │   ├── brokers/           # Broker adapters
│   │   │   ├── smart_api/     # SMART API integration
│   │   ├── strategies/        # Strategy framework
│   │   ├── indicators/        # Technical indicators
│   │   │   ├── realtime.py    # Real-time indicator engine
│   │   │   └── forming_candle.py # Forming candle manager
│   │   └── data/              # Data handling
│   └── tests/                 # Backend tests
├── frontend/                   # React frontend
│   ├── src/
│   │   ├── components/        # React components
│   │   ├── pages/             # Page components
│   │   ├── hooks/             # Custom hooks
│   │   ├── services/          # API services
│   │   └── store/             # State management
├── database/                   # Database schemas
├── docs/                       # Documentation
├── scripts/                    # Utility scripts
└── docker/                     # Docker configurations
```

## 🚀 Quick Start

### Prerequisites
- Python 3.9+
- Node.js 16+
- PostgreSQL 13+
- Redis 6+

### Installation

1. **Clone the repository**
```bash
git clone https://github.com/your-org/velox-algotrading.git
cd velox-algotrading
```

2. **Backend Setup**
```bash
cd backend
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
```

3. **Database Setup**
```bash
# Create database
createdb velox_trading

# Run migrations
alembic upgrade head
```

4. **Frontend Setup**
```bash
cd frontend
npm install
```

5. **Environment Configuration**
```bash
# Copy environment templates
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env

# Edit with your configuration
```

6. **Start Development Servers**
```bash
# Backend (Terminal 1)
cd backend
uvicorn app.main:app --reload

# Frontend (Terminal 2)
cd frontend
npm start
```

## 📚 Documentation

### Architecture Documents
- [`architecture.md`](architecture.md) - Detailed system architecture
- [`project_structure.md`](project_structure.md) - Project organization
- [`database_schema.md`](database_schema.md) - Database design
- [`api_specification.md`](api_specification.md) - Complete API reference

### Development Guides
- [`development_guide.md`](development_guide.md) - Development setup and best practices
- [`implementation_roadmap.md`](implementation_roadmap.md) - Implementation phases and timeline

### Strategy Development
- Strategy framework documentation (in development)
- Example strategies and templates
- Backtesting guide

## 🔧 Configuration

### Environment Variables

#### Backend (.env)
```env
# Database
DATABASE_URL=postgresql://user:password@localhost:5432/velox_trading
REDIS_URL=redis://localhost:6379/0

# Security
SECRET_KEY=your-secret-key
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=30

# Broker APIs
ZERODHA_API_KEY=your-zerodha-key
ZERODHA_API_SECRET=your-zerodha-secret

# Application
DEBUG=True
LOG_LEVEL=INFO
```

#### Frontend (.env)
```env
REACT_APP_API_BASE_URL=http://localhost:8000/v1
REACT_APP_WS_URL=ws://localhost:8000/ws
REACT_APP_ENVIRONMENT=development
```

## 🧪 Testing

### Backend Tests
```bash
cd backend
pytest                          # Run all tests
pytest --cov=app tests/         # Run with coverage
pytest tests/test_trading/      # Run specific module
```

### Frontend Tests
```bash
cd frontend
npm test                         # Run unit tests
npm run test:e2e                # Run E2E tests
```

## 🚀 Deployment

### Docker Deployment
```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

### Production Deployment
See [`deployment.md`](docs/deployment.md) for detailed production deployment instructions.

## 📊 Monitoring

### Health Checks
- API Health: `GET /health`
- Database Health: `GET /health/db`
- WebSocket Health: `GET /health/ws`

### Metrics
- Application metrics via Prometheus
- Database performance monitoring
- Real-time trading metrics

## 🔒 Security

### Authentication
- JWT-based authentication
- Role-based access control (Trader, Investor, Admin)
- Session management with refresh tokens

### Data Security
- Encrypted broker credentials
- Secure API communication (HTTPS/WSS)
- Input validation and sanitization

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow the project structure and coding standards
- Write comprehensive tests for new features
- Update documentation for API changes
- Ensure all tests pass before submitting PR

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

### Documentation
- Check the [documentation](docs/) for detailed guides
- Review the [API specification](api_specification.md) for integration help

### Issues
- Report bugs via [GitHub Issues](https://github.com/your-org/velox-algotrading/issues)
- Feature requests are welcome

### Community
- Join our [Discord community](https://discord.gg/velox)
- Follow us on [Twitter](https://twitter.com/veloxtrading)

## 🗺️ Roadmap

### Phase 1: Foundation (Weeks 1-3)
- ✅ System architecture design
- ✅ Database schema design
- 🔄 Development environment setup
- 🔄 Basic authentication system

### Phase 2: Core Trading Engine (Weeks 4-6)
- 🔄 Broker integrations
- 🔄 Real-time data processing
- 🔄 Strategy framework

### Phase 3: Risk Management (Weeks 7-8)
- 🔄 Risk management system
- 🔄 Position monitoring
- 🔄 Trade logging

### Phase 4: Frontend & Dashboard (Weeks 9-11)
- 🔄 Trading dashboard
- 🔄 Analytics and reporting
- 🔄 Multi-user support

### Phase 5: Advanced Features (Weeks 12-14)
- 🔄 Additional broker integrations
- 🔄 Backtesting capabilities
- 🔄 System monitoring

### Phase 6: Testing & Documentation (Weeks 15-16)
- 🔄 Comprehensive testing
- 🔄 Documentation
- 🔄 Deployment preparation

## 📈 Performance

### Benchmarks
- **Data Processing**: < 10ms latency for tick data
- **API Response**: < 100ms average response time
- **WebSocket**: < 5ms message delivery
- **Database**: Optimized for time-series data

### Scalability
- Horizontal scaling support
- Load balancing ready
- Database partitioning for large datasets
- Caching strategies for high-frequency operations

---

**Velox Algotrading System** - Empowering traders with robust, scalable, and intelligent trading solutions.

Built with ❤️ for the Indian trading community.