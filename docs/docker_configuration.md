# Docker Configuration for Velox Algotrading System

This document provides comprehensive Docker configurations for all infrastructure components including Redis, Kafka, PostgreSQL, and the application services.

## Overview

The Velox algotrading system uses Docker containerization for:
- **PostgreSQL**: Primary database with TimescaleDB extension
- **Redis**: Application caching and session storage
- **Kafka**: High-throughput message streaming
- **ZooKeeper**: Kafka cluster coordination
- **Application Services**: FastAPI backend and React frontend

## Docker Compose Structure

### Main Docker Compose File
**File**: `docker-compose.yml`

```yaml
version: '3.8'

services:
  # PostgreSQL Database with TimescaleDB
  postgres:
    image: timescale/timescaledb:latest-pg14
    container_name: velox-postgres
    environment:
      POSTGRES_DB: velox_trading
      POSTGRES_USER: velox_user
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-velox_password}
      POSTGRES_HOST_AUTH_METHOD: trust
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./database/init:/docker-entrypoint-initdb.d
    networks:
      - velox-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U velox_user -d velox_trading"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Redis for Caching and Sessions
  redis:
    image: redis:7-alpine
    container_name: velox-redis
    command: redis-server --appendonly yes --replica-read-only no
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
      - ./config/redis.conf:/usr/local/etc/redis/redis.conf
    networks:
      - velox-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 30s
      timeout: 10s
      retries: 3

  # ZooKeeper for Kafka Cluster
  zookeeper:
    image: confluentinc/cp-zookeeper:7.3.0
    container_name: velox-zookeeper
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
      ZOOKEEPER_SERVER_ID: 1
    ports:
      - "2181:2181"
      - "2888:2888"
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_logs:/var/lib/zookeeper/log
    networks:
      - velox-network
    restart: unless-stopped

  # Kafka Broker 1
  kafka-broker-1:
    image: confluentinc/cp-kafka:7.3.0
    container_name: velox-kafka-1
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-broker-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_JMX_PORT: 9101
      KAFKA_JMX_HOSTNAME: localhost
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_DELETE_TOPIC_ENABLE: 'true'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS: 300000
      KAFKA_LOG_CLEANUP_POLICY: delete
      KAFKA_COMPRESSION_TYPE: lz4
      KAFKA_MESSAGE_MAX_BYTES: 1000000
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_NUM_NETWORK_THREADS: 8
      KAFKA_SOCKET_REQUEST_MAX_BYTES: 104857600
    ports:
      - "9092:9092"
      - "9101:9101"
    volumes:
      - kafka-1_data:/var/lib/kafka/data
      - kafka-1_logs:/var/lib/kafka/logs
    networks:
      - velox-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Kafka Broker 2
  kafka-broker-2:
    image: confluentinc/cp-kafka:7.3.0
    container_name: velox-kafka-2
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-broker-2:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_JMX_PORT: 9102
      KAFKA_JMX_HOSTNAME: localhost
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_DELETE_TOPIC_ENABLE: 'true'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS: 300000
      KAFKA_LOG_CLEANUP_POLICY: delete
      KAFKA_COMPRESSION_TYPE: lz4
      KAFKA_MESSAGE_MAX_BYTES: 1000000
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_NUM_NETWORK_THREADS: 8
      KAFKA_SOCKET_REQUEST_MAX_BYTES: 104857600
    ports:
      - "9093:9093"
      - "9102:9102"
    volumes:
      - kafka-2_data:/var/lib/kafka/data
      - kafka-2_logs:/var/lib/kafka/logs
    networks:
      - velox-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9093"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Kafka Broker 3
  kafka-broker-3:
    image: confluentinc/cp-kafka:7.3.0
    container_name: velox-kafka-3
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 3
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-broker-3:9094
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_DEFAULT_REPLICATION_FACTOR: 3
      KAFKA_MIN_INSYNC_REPLICAS: 2
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      KAFKA_JMX_PORT: 9103
      KAFKA_JMX_HOSTNAME: localhost
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: 'true'
      KAFKA_DELETE_TOPIC_ENABLE: 'true'
      KAFKA_LOG_RETENTION_HOURS: 168
      KAFKA_LOG_SEGMENT_BYTES: 1073741824
      KAFKA_LOG_RETENTION_CHECK_INTERVAL_MS: 300000
      KAFKA_LOG_CLEANUP_POLICY: delete
      KAFKA_COMPRESSION_TYPE: lz4
      KAFKA_MESSAGE_MAX_BYTES: 1000000
      KAFKA_NUM_PARTITIONS: 6
      KAFKA_NUM_NETWORK_THREADS: 8
      KAFKA_SOCKET_REQUEST_MAX_BYTES: 104857600
    ports:
      - "9094:9094"
      - "9103:9103"
    volumes:
      - kafka-3_data:/var/lib/kafka/data
      - kafka-3_logs:/var/lib/kafka/logs
    networks:
      - velox-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9094"]
      interval: 30s
      timeout: 10s
      retries: 3

  # FastAPI Backend
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: velox-backend
    environment:
      - DATABASE_URL=postgresql://velox_user:${POSTGRES_PASSWORD:-velox_password}@postgres:5432/velox_trading
      - REDIS_URL=redis://redis:6379/0
      - KAFKA_BOOTSTRAP_SERVERS=kafka-broker-1:9092,kafka-broker-2:9093,kafka-broker-3:9094
      - SECRET_KEY=${SECRET_KEY:-your-secret-key-here}
      - DEBUG=${DEBUG:-false}
      - LOG_LEVEL=${LOG_LEVEL:-INFO}
    ports:
      - "8000:8000"
    volumes:
      - ./backend:/app
      - ./logs:/app/logs
    networks:
      - velox-network
    depends_on:
      - postgres
      - redis
      - kafka-broker-1
      - kafka-broker-2
      - kafka-broker-3
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8000/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  # React Frontend
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: velox-frontend
    environment:
      - REACT_APP_API_BASE_URL=http://localhost:8000/v1
      - REACT_APP_WS_URL=ws://localhost:8000/ws
      - REACT_APP_ENVIRONMENT=development
    ports:
      - "3000:3000"
    volumes:
      - ./frontend:/app
      - /app/node_modules
    networks:
      - velox-network
    depends_on:
      - backend
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3000"]
      interval: 30s
      timeout: 10s
      retries: 3

  # Kafka UI for Management
  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: velox-kafka-ui
    environment:
      KAFKA_CLUSTERS_0_NAME: velox-cluster
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka-broker-1:9092,kafka-broker-2:9093,kafka-broker-3:9094
      KAFKA_CLUSTERS_0_ZOOKEEPER: zookeeper:2181
      KAFKA_CLUSTERS_0_KAFKACONFIG_0_SASL_MECHANISM: PLAIN
      KAFKA_CLUSTERS_0_KAFKACONFIG_0_SECURITY_PROTOCOL: PLAINTEXT
    ports:
      - "8080:8080"
    networks:
      - velox-network
    depends_on:
      - kafka-broker-1
      - kafka-broker-2
      - kafka-broker-3
    restart: unless-stopped

  # Redis Commander for Management
  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: velox-redis-commander
    environment:
      - REDIS_HOSTS=redis:6379
      - REDIS_PASSWORDS=${POSTGRES_PASSWORD:-velox_password}
    ports:
      - "8081:8081"
    networks:
      - velox-network
    depends_on:
      - redis
    restart: unless-stopped

  # pgAdmin for Database Management
  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: velox-pgadmin
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@velox.com
      PGADMIN_DEFAULT_PASSWORD: ${PGADMIN_PASSWORD:-admin}
      PGADMIN_CONFIG_SERVER_MODE: False
      PGADMIN_CONFIG_MASTER_PASSWORD_REQUIRED: False
    ports:
      - "5050:80"
    networks:
      - velox-network
    depends_on:
      - postgres
    restart: unless-stopped
    volumes:
      - pgadmin_data:/var/lib/pgadmin

volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  zookeeper_data:
    driver: local
  zookeeper_logs:
    driver: local
  kafka-1_data:
    driver: local
  kafka-1_logs:
    driver: local
  kafka-2_data:
    driver: local
  kafka-2_logs:
    driver: local
  kafka-3_data:
    driver: local
  kafka-3_logs:
    driver: local
  pgadmin_data:
    driver: local

networks:
  velox-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
```

## Environment Configuration

### Environment Variables File
**File**: `.env`

```env
# Database Configuration
POSTGRES_PASSWORD=velox_secure_password
PGADMIN_PASSWORD=admin_password

# Application Configuration
SECRET_KEY=your-super-secret-key-change-in-production
DEBUG=false
LOG_LEVEL=INFO

# Kafka Configuration
KAFKA_REPLICATION_FACTOR=3
KAFKA_PARTITIONS_PER_TOPIC=6
KAFKA_RETENTION_HOURS=168

# Redis Configuration
REDIS_PASSWORD=redis_secure_password

# Frontend Configuration
REACT_APP_API_BASE_URL=http://localhost:8000/v1
REACT_APP_WS_URL=ws://localhost:8000/ws
REACT_APP_ENVIRONMENT=development
```

## Dockerfiles

### Backend Dockerfile
**File**: `backend/Dockerfile`

```dockerfile
FROM python:3.9-slim

# Set working directory
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create logs directory
RUN mkdir -p /app/logs

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8000/health || exit 1

# Run the application
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
```

### Frontend Dockerfile
**File**: `frontend/Dockerfile`

```dockerfile
# Build stage
FROM node:16-alpine as build

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Copy source code
COPY . .

# Build the application
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy built application
COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration
COPY nginx.conf /etc/nginx/nginx.conf

# Expose port
EXPOSE 3000

# Start nginx
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration
**File**: `frontend/nginx.conf`

```nginx
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    server {
        listen 3000;
        server_name localhost;
        root /usr/share/nginx/html;
        index index.html index.htm;

        # Enable gzip compression
        gzip on;
        gzip_vary on;
        gzip_min_length 1024;
        gzip_types text/plain text/css text/xml text/javascript application/javascript application/xml+rss application/json;

        # Handle client-side routing
        location / {
            try_files $uri $uri/ /index.html;
        }

        # API proxy to backend
        location /api/ {
            proxy_pass http://backend:8000;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # WebSocket proxy
        location /ws/ {
            proxy_pass http://backend:8000;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

## Redis Configuration

### Redis Configuration File
**File**: `config/redis.conf`

```conf
# Network
bind 0.0.0.0
port 6379
protected-mode yes
requirepass redis_secure_password

# Persistence
appendonly yes
appendfilename "appendonly.aof"
save 900 1
save 300 10
save 60 10000

# Memory
maxmemory 256mb
maxmemory-policy allkeys-lru

# Logging
loglevel notice
logfile /var/log/redis/redis.log

# Performance
tcp-keepalive 300
timeout 0
```

## Kafka Topic Configuration

### Kafka Topics Setup Script
**File**: `scripts/setup-kafka-topics.sh`

```bash
#!/bin/bash

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
until nc -z kafka-broker-1 9092; do
  echo "Kafka is unavailable - sleeping"
  sleep 2
done

echo "Kafka is ready - creating topics"

# Create topics with proper configuration
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 6 --topic market-data-ticks
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 4 --topic indicator-updates
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 4 --topic strategy-signals
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 6 --topic order-events
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 4 --topic position-updates
kafka-topics --create --bootstrap-server kafka-broker-1:9092 --replication-factor 3 --partitions 3 --topic risk-alerts

echo "Kafka topics created successfully"
```

## Development Commands

### Start All Services
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Rebuild and start
docker-compose up -d --build
```

### Individual Service Management
```bash
# Start specific service
docker-compose up -d postgres

# Stop specific service
docker-compose stop postgres

# Restart service
docker-compose restart postgres

# View service logs
docker-compose logs -f postgres

# Execute commands in container
docker-compose exec postgres psql -U velox_user -d velox_trading
docker-compose exec backend python -m pytest
```

### Database Management
```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U velox_user -d velox_trading

# Create database migrations
docker-compose exec backend alembic upgrade head

# Backup database
docker-compose exec postgres pg_dump -U velox_user velox_trading > backup.sql
```

### Kafka Management
```bash
# View Kafka topics
docker-compose exec kafka-broker-1 kafka-topics --list --bootstrap-server localhost:9092

# Describe topic configuration
docker-compose exec kafka-broker-1 kafka-topics --describe --topic market-data-ticks --bootstrap-server localhost:9092

# Produce test message
docker-compose exec kafka-broker-1 kafka-console-producer --topic market-data-ticks --bootstrap-server localhost:9092

# Consume test messages
docker-compose exec kafka-broker-1 kafka-console-consumer --topic market-data-ticks --from-beginning --bootstrap-server localhost:9092
```

### Redis Management
```bash
# Connect to Redis CLI
docker-compose exec redis redis-cli -a redis_secure_password

# Monitor Redis
docker-compose exec redis redis-cli --latency-history -i 1

# Check Redis info
docker-compose exec redis redis-cli info
```

## Production Considerations

### Security
- Change default passwords in production
- Use environment-specific configurations
- Enable SSL/TLS for external communications
- Implement proper network isolation
- Regular security updates

### Performance
- Adjust resource limits based on available hardware
- Optimize Kafka partition count for throughput
- Tune PostgreSQL for production workload
- Monitor resource usage and scale accordingly

### Monitoring
- Enable health checks for all services
- Set up log aggregation
- Monitor Kafka cluster health
- Track application performance metrics
- Set up alerting for critical issues

### Backup and Recovery
- Regular database backups
- Kafka topic data backup strategy
- Configuration version control
- Disaster recovery procedures
- Data retention policies

This Docker configuration provides a complete, production-ready environment for the Velox algotrading system with all required services properly containerized and orchestrated.