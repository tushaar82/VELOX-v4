#!/bin/bash

# Velox Algotrading System Setup Script
# This script sets up the development environment for the Velox algotrading system

set -e

echo "🚀 Setting up Velox Algotrading Development Environment..."

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Check if Java 21 is installed
if ! command -v java &> /dev/null; then
    echo "❌ Java is not installed. Please install Java 21 first."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "21" ]; then
    echo "❌ Java 21 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js first."
    exit 1
fi

# Check if npm is installed
if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed. Please install npm first."
    exit 1
fi

echo "✅ All prerequisites are installed!"

# Create logs directory
echo "📁 Creating logs directory..."
mkdir -p logs

# Copy environment file if it doesn't exist
if [ ! -f .env ]; then
    echo "📝 Creating .env file from template..."
    cp .env.example .env
    echo "⚠️  Please edit .env file with your configuration before running the application."
fi

# Install Java dependencies
echo "📦 Installing Java dependencies..."
cd java-services
./mvnw clean install -DskipTests
cd ..

# Install API Gateway dependencies
echo "📦 Installing API Gateway dependencies..."
cd api-gateway
npm install
cd ..

# Install Frontend dependencies
echo "📦 Installing Frontend dependencies..."
cd frontend
npm install
cd ..

# Create Docker network if it doesn't exist
echo "🌐 Creating Docker network..."
docker network create velox-network 2>/dev/null || true

# Build and start infrastructure services
echo "🐳 Building and starting infrastructure services..."
docker-compose up -d postgres zookeeper kafka-1 kafka-2 kafka-3 redis

# Wait for services to be ready
echo "⏳ Waiting for services to be ready..."
sleep 30

# Check if PostgreSQL is ready
echo "🔍 Checking PostgreSQL connection..."
until docker-compose exec postgres pg_isready -U ${DB_USER:-velox_user} -d ${DB_NAME:-velox_algotrading}; do
    echo "Waiting for PostgreSQL..."
    sleep 2
done

# Check if Kafka is ready
echo "🔍 Checking Kafka connection..."
until docker-compose exec kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092; do
    echo "Waiting for Kafka..."
    sleep 2
done

# Create Kafka topics
echo "📋 Creating Kafka topics..."
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic market-data --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic indicators --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic signals --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic orders --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic positions --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic alerts --partitions 6 --replication-factor 3 --if-not-exists

echo "✅ Kafka topics created successfully!"

# Start all services
echo "🐳 Starting all services..."
docker-compose up -d

echo ""
echo "🎉 Velox Algotrading Development Environment is ready!"
echo ""
echo "📊 Service URLs:"
echo "   - API Gateway: http://localhost:${API_GATEWAY_PORT:-8000}"
echo "   - Frontend: http://localhost:${FRONTEND_PORT:-3000}"
echo "   - Kafka UI: http://localhost:8080"
echo "   - Market Data Processor: http://localhost:${MARKET_DATA_PROCESSOR_PORT:-8081}"
echo "   - Indicators Calculator: http://localhost:${INDICATORS_CALCULATOR_PORT:-8082}"
echo "   - Risk Management: http://localhost:${RISK_MANAGEMENT_PORT:-8083}"
echo ""
echo "📝 Useful Commands:"
echo "   - View logs: docker-compose logs -f [service-name]"
echo "   - Stop services: docker-compose down"
echo "   - Restart services: docker-compose restart [service-name]"
echo "   - Run tests: npm test"
echo ""
echo "📚 Documentation: ./docs/"
echo ""
echo "⚠️  Don't forget to:"
echo "   1. Configure your .env file with proper credentials"
echo "   2. Set up your broker API credentials"
echo "   3. Review the security configurations"
echo ""