-- Initial database schema for Velox Algotrading System
-- This migration creates all the core tables required for the algotrading platform

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable TimescaleDB extension for time-series data
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Create custom types
CREATE TYPE user_role AS ENUM ('trader', 'investor', 'admin');
CREATE TYPE broker_api_type AS ENUM ('zerodha', 'angel', 'icici');
CREATE TYPE order_type AS ENUM ('MARKET', 'LIMIT', 'SL', 'SL-M');
CREATE TYPE transaction_type AS ENUM ('BUY', 'SELL');
CREATE TYPE instrument_type AS ENUM ('EQUITY', 'FUTURES', 'OPTIONS', 'COMMODITY', 'CURRENCY');
CREATE TYPE order_status AS ENUM ('PENDING', 'OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED');
CREATE TYPE position_status AS ENUM ('OPEN', 'CLOSED', 'PARTIALLY_CLOSED');
CREATE TYPE log_level AS ENUM ('DEBUG', 'INFO', 'WARNING', 'ERROR', 'CRITICAL');
CREATE TYPE market_data_interval AS ENUM ('tick', '1min', '5min', '15min', '30min', '1hour', '1day');
CREATE TYPE position_action AS ENUM ('OPEN', 'CLOSE', 'INCREASE', 'DECREASE');

-- Create update timestamp function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Users table
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role user_role NOT NULL DEFAULT 'trader',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP WITH TIME ZONE
);

-- Create trigger for users table
CREATE TRIGGER update_users_updated_at 
    BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Brokers table
CREATE TABLE brokers (
    broker_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    api_type broker_api_type NOT NULL,
    api_credentials_encrypted JSONB NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create trigger for brokers table
CREATE TRIGGER update_brokers_updated_at 
    BEFORE UPDATE ON brokers 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Strategies table
CREATE TABLE strategies (
    strategy_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parameters JSONB NOT NULL DEFAULT '{}',
    strategy_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    is_running BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_run TIMESTAMP WITH TIME ZONE
);

-- Create trigger for strategies table
CREATE TRIGGER update_strategies_updated_at 
    BEFORE UPDATE ON strategies 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Risk settings table
CREATE TABLE risk_settings (
    risk_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    daily_loss_limit DECIMAL(15,2) NOT NULL,
    max_position_size DECIMAL(15,2) NOT NULL,
    max_drawdown_percent DECIMAL(5,2) NOT NULL,
    max_risk_per_trade DECIMAL(5,2) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create trigger for risk_settings table
CREATE TRIGGER update_risk_settings_updated_at 
    BEFORE UPDATE ON risk_settings 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- User sessions table
CREATE TABLE user_sessions (
    session_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    ip_address INET,
    user_agent TEXT
);

-- Market data table (TimescaleDB hypertable)
CREATE TABLE market_data (
    data_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    symbol VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    open DECIMAL(10,2) NOT NULL,
    high DECIMAL(10,2) NOT NULL,
    low DECIMAL(10,2) NOT NULL,
    close DECIMAL(10,2) NOT NULL,
    volume BIGINT NOT NULL,
    oi DECIMAL(15,2),
    interval market_data_interval NOT NULL DEFAULT '1min',
    tick_data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create hypertable for market data
SELECT create_hypertable('market_data', 'timestamp');

-- Indicator values table
CREATE TABLE indicator_values (
    indicator_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    data_id UUID NOT NULL REFERENCES market_data(data_id) ON DELETE CASCADE,
    indicator_name VARCHAR(50) NOT NULL,
    values JSONB NOT NULL,
    calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Trades table
CREATE TABLE trades (
    trade_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    strategy_id UUID NOT NULL REFERENCES strategies(strategy_id) ON DELETE CASCADE,
    broker_id UUID NOT NULL REFERENCES brokers(broker_id) ON DELETE RESTRICT,
    symbol VARCHAR(50) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    instrument_type instrument_type NOT NULL,
    order_type order_type NOT NULL,
    transaction_type transaction_type NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10,2),
    trigger_price DECIMAL(10,2),
    stoploss DECIMAL(10,2),
    target DECIMAL(10,2),
    status order_status NOT NULL DEFAULT 'PENDING',
    order_id VARCHAR(100),
    parent_order_id VARCHAR(100),
    order_timestamp TIMESTAMP WITH TIME ZONE,
    execution_timestamp TIMESTAMP WITH TIME ZONE,
    average_price DECIMAL(10,2),
    filled_quantity INTEGER DEFAULT 0,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create trigger for trades table
CREATE TRIGGER update_trades_updated_at 
    BEFORE UPDATE ON trades 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Positions table
CREATE TABLE positions (
    position_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trade_id UUID NOT NULL REFERENCES trades(trade_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    symbol VARCHAR(50) NOT NULL,
    exchange VARCHAR(20) NOT NULL,
    instrument_type instrument_type NOT NULL,
    quantity INTEGER NOT NULL,
    average_price DECIMAL(10,2) NOT NULL,
    current_price DECIMAL(10,2),
    unrealized_pnl DECIMAL(15,2) DEFAULT 0,
    realized_pnl DECIMAL(15,2) DEFAULT 0,
    status position_status NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create trigger for positions table
CREATE TRIGGER update_positions_updated_at 
    BEFORE UPDATE ON positions 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Position history table
CREATE TABLE position_history (
    history_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    position_id UUID NOT NULL REFERENCES positions(position_id) ON DELETE CASCADE,
    price DECIMAL(10,2) NOT NULL,
    quantity INTEGER NOT NULL,
    pnl DECIMAL(15,2) NOT NULL,
    action position_action NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Trade logs table
CREATE TABLE trade_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trade_id UUID NOT NULL REFERENCES trades(trade_id) ON DELETE CASCADE,
    log_level log_level NOT NULL,
    message TEXT NOT NULL,
    data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Strategy performance table
CREATE TABLE strategy_performance (
    performance_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    strategy_id UUID NOT NULL REFERENCES strategies(strategy_id) ON DELETE CASCADE,
    date DATE NOT NULL,
    total_trades INTEGER DEFAULT 0,
    winning_trades INTEGER DEFAULT 0,
    losing_trades INTEGER DEFAULT 0,
    total_pnl DECIMAL(15,2) DEFAULT 0,
    max_drawdown DECIMAL(15,2) DEFAULT 0,
    sharpe_ratio DECIMAL(10,4),
    win_rate DECIMAL(5,2),
    metrics JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(strategy_id, date)
);

-- Create trigger for strategy_performance table
CREATE TRIGGER update_strategy_performance_updated_at 
    BEFORE UPDATE ON strategy_performance 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- System logs table
CREATE TABLE system_logs (
    log_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    component VARCHAR(50) NOT NULL,
    log_level log_level NOT NULL,
    message TEXT NOT NULL,
    data JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_is_active ON users(is_active);

CREATE INDEX idx_brokers_user_id ON brokers(user_id);
CREATE INDEX idx_brokers_api_type ON brokers(api_type);
CREATE INDEX idx_brokers_is_active ON brokers(is_active);

CREATE INDEX idx_strategies_user_id ON strategies(user_id);
CREATE INDEX idx_strategies_type ON strategies(strategy_type);
CREATE INDEX idx_strategies_is_active ON strategies(is_active);
CREATE INDEX idx_strategies_is_running ON strategies(is_running);

CREATE INDEX idx_risk_settings_user_id ON risk_settings(user_id);

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_token_hash ON user_sessions(token_hash);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

CREATE INDEX idx_market_data_symbol ON market_data(symbol);
CREATE INDEX idx_market_data_timestamp ON market_data(timestamp);
CREATE INDEX idx_market_data_symbol_timestamp ON market_data(symbol, timestamp);
CREATE INDEX idx_market_data_interval ON market_data(interval);

CREATE INDEX idx_indicator_values_data_id ON indicator_values(data_id);
CREATE INDEX idx_indicator_values_name ON indicator_values(indicator_name);
CREATE INDEX idx_indicator_values_calculated_at ON indicator_values(calculated_at);

CREATE INDEX idx_trades_strategy_id ON trades(strategy_id);
CREATE INDEX idx_trades_broker_id ON trades(broker_id);
CREATE INDEX idx_trades_symbol ON trades(symbol);
CREATE INDEX idx_trades_status ON trades(status);
CREATE INDEX idx_trades_order_timestamp ON trades(order_timestamp);
CREATE INDEX idx_trades_execution_timestamp ON trades(execution_timestamp);

CREATE INDEX idx_positions_trade_id ON positions(trade_id);
CREATE INDEX idx_positions_user_id ON positions(user_id);
CREATE INDEX idx_positions_symbol ON positions(symbol);
CREATE INDEX idx_positions_status ON positions(status);
CREATE INDEX idx_positions_opened_at ON positions(opened_at);

CREATE INDEX idx_position_history_position_id ON position_history(position_id);
CREATE INDEX idx_position_history_timestamp ON position_history(timestamp);

CREATE INDEX idx_trade_logs_trade_id ON trade_logs(trade_id);
CREATE INDEX idx_trade_logs_level ON trade_logs(log_level);
CREATE INDEX idx_trade_logs_created_at ON trade_logs(created_at);

CREATE INDEX idx_strategy_performance_strategy_id ON strategy_performance(strategy_id);
CREATE INDEX idx_strategy_performance_date ON strategy_performance(date);

CREATE INDEX idx_system_logs_component ON system_logs(component);
CREATE INDEX idx_system_logs_level ON system_logs(log_level);
CREATE INDEX idx_system_logs_created_at ON system_logs(created_at);