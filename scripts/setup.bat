@echo off
REM Velox Algotrading System Setup Script for Windows
REM This script sets up the development environment for the Velox algotrading system

echo 🚀 Setting up Velox Algotrading Development Environment...

REM Check if Docker is installed
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker is not installed. Please install Docker first.
    pause
    exit /b 1
)

REM Check if Docker Compose is installed
docker-compose --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Docker Compose is not installed. Please install Docker Compose first.
    pause
    exit /b 1
)

REM Check if Java is installed
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Java is not installed. Please install Java 21 first.
    pause
    exit /b 1
)

REM Check if Node.js is installed
node --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ Node.js is not installed. Please install Node.js first.
    pause
    exit /b 1
)

REM Check if npm is installed
npm --version >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ npm is not installed. Please install npm first.
    pause
    exit /b 1
)

echo ✅ All prerequisites are installed!

REM Create logs directory
echo 📁 Creating logs directory...
if not exist logs mkdir logs

REM Copy environment file if it doesn't exist
if not exist .env (
    echo 📝 Creating .env file from template...
    copy .env.example .env
    echo ⚠️  Please edit .env file with your configuration before running the application.
)

REM Install Java dependencies
echo 📦 Installing Java dependencies...
cd java-services
call mvnw.cmd clean install -DskipTests
cd ..

REM Install API Gateway dependencies
echo 📦 Installing API Gateway dependencies...
cd api-gateway
call npm install
cd ..

REM Install Frontend dependencies
echo 📦 Installing Frontend dependencies...
cd frontend
call npm install
cd ..

REM Create Docker network if it doesn't exist
echo 🌐 Creating Docker network...
docker network create velox-network 2>nul

REM Build and start infrastructure services
echo 🐳 Building and starting infrastructure services...
docker-compose up -d postgres zookeeper kafka-1 kafka-2 kafka-3 redis

REM Wait for services to be ready
echo ⏳ Waiting for services to be ready...
timeout /t 30 /nobreak

REM Check if PostgreSQL is ready
echo 🔍 Checking PostgreSQL connection...
:check_postgres
docker-compose exec postgres pg_isready -U velox_user -d velox_algotrading >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for PostgreSQL...
    timeout /t 2 /nobreak
    goto check_postgres
)

REM Check if Kafka is ready
echo 🔍 Checking Kafka connection...
:check_kafka
docker-compose exec kafka-1 kafka-broker-api-versions --bootstrap-server localhost:9092 >nul 2>&1
if %errorlevel% neq 0 (
    echo Waiting for Kafka...
    timeout /t 2 /nobreak
    goto check_kafka
)

REM Create Kafka topics
echo 📋 Creating Kafka topics...
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic market-data --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic indicators --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic signals --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic orders --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic positions --partitions 6 --replication-factor 3 --if-not-exists
docker-compose exec kafka-1 kafka-topics --create --bootstrap-server localhost:9092 --topic alerts --partitions 6 --replication-factor 3 --if-not-exists

echo ✅ Kafka topics created successfully!

REM Start all services
echo 🐳 Starting all services...
docker-compose up -d

echo.
echo 🎉 Velox Algotrading Development Environment is ready!
echo.
echo 📊 Service URLs:
echo    - API Gateway: http://localhost:8000
echo    - Frontend: http://localhost:3000
echo    - Kafka UI: http://localhost:8080
echo    - Market Data Processor: http://localhost:8081
echo    - Indicators Calculator: http://localhost:8082
echo    - Risk Management: http://localhost:8083
echo.
echo 📝 Useful Commands:
echo    - View logs: docker-compose logs -f [service-name]
echo    - Stop services: docker-compose down
echo    - Restart services: docker-compose restart [service-name]
echo    - Run tests: npm test
echo.
echo 📚 Documentation: ./docs/
echo.
echo ⚠️  Don't forget to:
echo    1. Configure your .env file with proper credentials
echo    2. Set up your broker API credentials
echo    3. Review the security configurations
echo.
pause