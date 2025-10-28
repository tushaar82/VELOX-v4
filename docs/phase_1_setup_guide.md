# Phase 1 Setup Guide

This guide provides detailed instructions for setting up the Phase 1 foundation of the Velox algotrading system.

## 🎯 Phase 1 Objectives

Phase 1 establishes the core infrastructure and basic functionality:
- ✅ Complete development environment setup
- ✅ Database schema and migrations
- ✅ Java microservices foundation
- ✅ Kafka cluster for messaging
- ✅ Basic project structure

## 📋 Prerequisites

### Required Software

1. **Java 21+**
   ```bash
   # Check Java version
   java -version
   # Should show Java 21 or higher
   ```

2. **Node.js 18+ and npm**
   ```bash
   # Check Node.js version
   node --version
   # Should show 18 or higher
   
   # Check npm version
   npm --version
   ```

3. **Docker & Docker Compose**
   ```bash
   # Check Docker
   docker --version
   
   # Check Docker Compose
   docker-compose --version
   ```

4. **Git**
   ```bash
   # Check Git version
   git --version
   ```

### System Requirements

- **RAM**: Minimum 8GB, Recommended 16GB
- **Storage**: Minimum 20GB free space
- **OS**: Windows 10+, macOS 10.15+, or Linux (Ubuntu 18.04+)

## 🚀 Quick Setup

### 1. Clone Repository

```bash
git clone https://github.com/velox-algotrading/velox-algotrading.git
cd velox-algotrading
```

### 2. Run Setup Script

**For Linux/macOS:**
```bash
chmod +x scripts/setup.sh
./scripts/setup.sh
```

**For Windows:**
```cmd
scripts\setup.bat
```

The setup script will:
- ✅ Verify all prerequisites
- ✅ Create necessary directories
- ✅ Install dependencies
- ✅ Set up infrastructure services
- ✅ Create Kafka topics
- ✅ Start all services

### 3. Configure Environment

```bash
# Copy environment template
cp .env.example .env

# Edit with your configuration
nano .env
```

Key configuration items:
- Database credentials
- Kafka bootstrap servers
- Service ports
- JWT secrets

## 🔧 Manual Setup (Alternative)

If the setup script fails, you can set up manually:

### 1. Infrastructure Services

```bash
# Start database and messaging services
docker-compose up -d postgres zookeeper kafka-1 kafka-2 kafka-3 redis

# Wait for services to be ready (30-60 seconds)
```

### 2. Database Setup

```bash
# Check PostgreSQL connection
docker-compose exec postgres pg_isready -U velox_user -d velox_algotrading

# Run database migrations
docker-compose exec postgres psql -U velox_user -d velox_algotrading -f /docker-entrypoint-initdb.d/001_initial_schema.sql
```

### 3. Kafka Setup

```bash
# Check Kafka connectivity
docker-compose exec kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092

# Create topics
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic market-data --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic indicators --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic signals --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic orders --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic positions --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic alerts --partitions 6 --replication-factor 3 --if-not-exists
```

### 4. Java Services

```bash
# Build all Java services
cd java-services
./mvnw clean install -DskipTests

# Start individual services
cd market-data-processor
./mvnw spring-boot:run

cd ../indicators-calculator
./mvnw spring-boot:run

cd ../risk-management
./mvnw spring-boot:run
```

### 5. API Gateway

```bash
cd api-gateway
npm install
npm run dev
```

### 6. Frontend

```bash
cd frontend
npm install
npm start
```

## 🔍 Verification

### Health Checks

Verify all services are running:

```bash
# Java Services
curl http://localhost:8081/api/v1/actuator/health  # Market Data Processor
curl http://localhost:8082/api/v1/actuator/health  # Indicators Calculator
curl http://localhost:8083/api/v1/actuator/health  # Risk Management

# API Gateway
curl http://localhost:8000/health

# Frontend
curl http://localhost:3000
```

### Service URLs

After successful setup, access services at:

- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8000
- **Market Data Processor**: http://localhost:8081
- **Indicators Calculator**: http://localhost:8082
- **Risk Management**: http://localhost:8083
- **Kafka UI**: http://localhost:8080

### Database Connection

```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U velox_user -d velox_algotrading

# List tables
\dt

# Check TimescaleDB extension
\dx
```

## 🛠️ Development Workflow

### Starting Services

```bash
# Start all services
docker-compose up -d

# Start specific services
docker-compose up -d postgres kafka-1 redis

# View logs
docker-compose logs -f [service-name]
```

### Stopping Services

```bash
# Stop all services
docker-compose down

# Stop and remove volumes
docker-compose down -v
```

### Code Changes

For Java services:
```bash
# Build and restart
cd java-services/[service-name]
./mvnw clean install
docker-compose restart [service-name]
```

For API Gateway:
```bash
cd api-gateway
npm install
docker-compose restart api-gateway
```

For Frontend:
```bash
cd frontend
npm install
docker-compose restart frontend
```

## 🐛 Troubleshooting

### Common Issues

1. **Port Conflicts**
   ```bash
   # Check what's using ports
   netstat -tulpn | grep :8080
   # Kill conflicting processes or change ports in .env
   ```

2. **Docker Issues**
   ```bash
   # Reset Docker
   docker system prune -a
   docker-compose down -v
   docker-compose up -d
   ```

3. **Java Memory Issues**
   ```bash
   # Increase JVM memory in .env
   JAVA_OPTS=-Xmx2g -Xms1g
   ```

4. **Kafka Connection Issues**
   ```bash
   # Check Kafka logs
   docker-compose logs kafka-1
   
   # Verify topic creation
   docker-compose exec kafka-1 kafka-topics --list --bootstrap-server localhost:9092
   ```

5. **Database Connection Issues**
   ```bash
   # Check PostgreSQL logs
   docker-compose logs postgres
   
   # Verify database exists
   docker-compose exec postgres psql -U velox_user -d velox_algotrading -c "\l"
   ```

### Getting Help

1. **Check Logs**
   ```bash
   # Service-specific logs
   docker-compose logs -f [service-name]
   
   # All logs
   docker-compose logs -f
   ```

2. **Verify Configuration**
   ```bash
   # Check environment variables
   docker-compose config
   
   # Check service configuration
   docker-compose exec [service-name] env
   ```

3. **Reset Environment**
   ```bash
   # Complete reset
   docker-compose down -v
   docker system prune -a
   ./scripts/setup.sh  # or scripts\setup.bat on Windows
   ```

## 📚 Next Steps

After completing Phase 1 setup:

1. **Review Architecture**: Read [architecture.md](architecture.md)
2. **Explore API**: Check [api_specification.md](api_specification.md)
3. **Understand Database**: Review [database_schema.md](database_schema.md)
4. **Development Guide**: Follow [development_guide.md](development_guide.md)

## 🎯 Phase 1 Success Criteria

✅ **Environment Setup**
- All prerequisites installed
- Development environment configured
- Services running successfully

✅ **Infrastructure**
- PostgreSQL with TimescaleDB operational
- Kafka cluster (3 nodes) running
- All 6 core topics created
- Redis cache operational

✅ **Java Services**
- Maven build system configured
- Spring Boot applications starting
- Health endpoints accessible
- Logging configured

✅ **Project Structure**
- Directory structure created
- Configuration files in place
- Docker compose functional
- Documentation available

---

**Ready for Phase 2**: Core Trading Engine implementation! 🚀