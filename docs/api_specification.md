# API Specification

## Overview

This document outlines the complete API specification for the multi-user algotrading system, including REST endpoints and WebSocket connections for real-time data streaming.

## API Overview

- **Base URL**: `https://api.veloxtrading.com/v1`
- **Authentication**: JWT Bearer Token
- **Content-Type**: `application/json`
- **Rate Limiting**: 100 requests per minute per user
- **WebSocket URL**: `wss://api.veloxtrading.com/ws`
- **Primary Broker**: SMART API for real-time data and trading

## Authentication

All API endpoints (except authentication endpoints) require a valid JWT token in the Authorization header:

```
Authorization: Bearer <jwt_token>
```

## Response Format

### Success Response

```json
{
  "success": true,
  "data": {},
  "message": "Operation completed successfully",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Error Response

```json
{
  "success": false,
  "error": {
    "code": "ERROR_CODE",
    "message": "Error description",
    "details": {}
  },
  "timestamp": "2024-01-01T12:00:00Z"
}
```

## REST Endpoints

### 1. Authentication

#### POST /auth/login
Login user and return JWT token.

**Request Body**:
```json
{
  "username": "string",
  "password": "string"
}
```

**Response**:
```json
{
  "success": true,
  "data": {
    "access_token": "string",
    "refresh_token": "string",
    "token_type": "bearer",
    "expires_in": 3600,
    "user": {
      "user_id": "uuid",
      "username": "string",
      "email": "string",
      "role": "trader|investor|admin"
    }
  }
}
```

#### POST /auth/refresh
Refresh JWT token.

**Request Body**:
```json
{
  "refresh_token": "string"
}
```

#### POST /auth/logout
Logout user and invalidate token.

**Headers**: `Authorization: Bearer <token>`

#### POST /auth/change-password
Change user password.

**Headers**: `Authorization: Bearer <token>`

### 2. User Management

#### GET /users/profile
Get current user profile.

**Headers**: `Authorization: Bearer <token>`

**Response**:
```json
{
  "success": true,
  "data": {
    "user_id": "uuid",
    "username": "string",
    "email": "string",
    "role": "trader|investor|admin",
    "is_active": true,
    "created_at": "2024-01-01T12:00:00Z",
    "last_login": "2024-01-01T12:00:00Z"
  }
}
```

#### PUT /users/profile
Update user profile.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "email": "string",
  "username": "string"
}
```

#### GET /users/risk-settings
Get user risk settings.

**Headers**: `Authorization: Bearer <token>`

#### PUT /users/risk-settings
Update user risk settings.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "daily_loss_limit": 10000.00,
  "max_position_size": 50000.00,
  "max_drawdown_percent": 5.00,
  "max_risk_per_trade": 2.00
}
```

### 3. Broker Management

#### GET /brokers
Get all configured brokers for the user.

**Headers**: `Authorization: Bearer <token>`

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "broker_id": "uuid",
      "name": "Zerodha Kite",
      "api_type": "zerodha",
      "is_active": true,
      "created_at": "2024-01-01T12:00:00Z"
    }
  ]
}
```

#### POST /brokers
Add a new broker configuration.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "name": "SMART API",
  "api_type": "smart_api",
  "api_credentials": {
    "api_key": "string",
    "client_id": "string",
    "password": "string",
    "totp": "string"
  }
}
```

#### PUT /brokers/{broker_id}
Update broker configuration.

**Headers**: `Authorization: Bearer <token>`

#### DELETE /brokers/{broker_id}
Remove broker configuration.

**Headers**: `Authorization: Bearer <token>`

#### POST /brokers/{broker_id}/test-connection
Test broker API connection.

**Headers**: `Authorization: Bearer <token>`

### 4. Strategy Management

#### GET /strategies
Get all strategies for the user.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `page`: Page number (default: 1)
- `limit`: Items per page (default: 20)
- `is_active`: Filter by active status
- `strategy_type`: Filter by strategy type

**Response**:
```json
{
  "success": true,
  "data": {
    "strategies": [
      {
        "strategy_id": "uuid",
        "name": "MA Crossover",
        "description": "Moving average crossover strategy",
        "strategy_type": "technical",
        "parameters": {
          "fast_period": 10,
          "slow_period": 20
        },
        "is_active": true,
        "is_running": false,
        "created_at": "2024-01-01T12:00:00Z",
        "last_run": null
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 5,
      "pages": 1
    }
  }
}
```

#### POST /strategies
Create a new strategy.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "name": "RSI Strategy",
  "description": "RSI-based trading strategy",
  "strategy_type": "technical",
  "parameters": {
    "rsi_period": 14,
    "oversold_level": 30,
    "overbought_level": 70
  }
}
```

#### GET /strategies/{strategy_id}
Get strategy details.

**Headers**: `Authorization: Bearer <token>`

#### PUT /strategies/{strategy_id}
Update strategy configuration.

**Headers**: `Authorization: Bearer <token>`

#### DELETE /strategies/{strategy_id}
Delete strategy.

**Headers**: `Authorization: Bearer <token>`

#### POST /strategies/{strategy_id}/start
Start strategy execution.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "mode": "live|dry",
  "broker_id": "uuid",
  "symbols": ["NIFTY", "BANKNIFTY"],
  "risk_settings": {
    "position_size": 10000,
    "stoploss_percent": 2.0
  }
}
```

#### POST /strategies/{strategy_id}/stop
Stop strategy execution.

**Headers**: `Authorization: Bearer <token>`

#### GET /strategies/{strategy_id}/performance
Get strategy performance metrics.

**Headers**: `Authorization: Bearer <token>`

### 5. Market Data

#### GET /market-data/symbols
Get available trading symbols.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `exchange`: Filter by exchange (NSE, BSE)
- `instrument_type`: Filter by instrument type (EQUITY, OPTION, FUTURE)
- `search`: Search symbol name

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "symbol": "NIFTY",
      "name": "NIFTY 50",
      "exchange": "NSE",
      "instrument_type": "INDEX",
      "lot_size": 50,
      "tick_size": 0.05
    }
  ]
}
```

#### GET /market-data/{symbol}/candles
Get historical candle data.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `interval`: Time interval (1min, 5min, 15min, 30min, 1hour, 1day)
- `from_date`: Start date (YYYY-MM-DD)
- `to_date`: End date (YYYY-MM-DD)
- `limit`: Number of candles (max: 1000)

**Response**:
```json
{
  "success": true,
  "data": {
    "symbol": "NIFTY",
    "interval": "5min",
    "candles": [
      {
        "timestamp": "2024-01-01T12:00:00Z",
        "open": 18500.00,
        "high": 18550.00,
        "low": 18480.00,
        "close": 18520.00,
        "volume": 1000000
      }
    ]
  }
}
```

#### GET /market-data/{symbol}/indicators
Get calculated indicators for a symbol.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `indicators`: Comma-separated list of indicators (sma,ema,rsi,macd,bb)
- `periods`: Indicator periods (e.g., 14,21 for RSI and SMA)

### 6. Trading

#### GET /trades
Get all trades for the user.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `page`: Page number
- `limit`: Items per page
- `status`: Filter by status
- `symbol`: Filter by symbol
- `from_date`: Filter by date range
- `to_date`: Filter by date range

**Response**:
```json
{
  "success": true,
  "data": {
    "trades": [
      {
        "trade_id": "uuid",
        "strategy_name": "MA Crossover",
        "symbol": "NIFTY",
        "exchange": "NSE",
        "transaction_type": "BUY",
        "quantity": 50,
        "price": 18500.00,
        "status": "FILLED",
        "order_timestamp": "2024-01-01T12:00:00Z",
        "execution_timestamp": "2024-01-01T12:00:05Z"
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 100,
      "pages": 5
    }
  }
}
```

#### GET /trades/{trade_id}
Get trade details.

**Headers**: `Authorization: Bearer <token>`

#### POST /orders
Place a new order.

**Headers**: `Authorization: Bearer <token>`

**Request Body**:
```json
{
  "symbol": "NIFTY",
  "exchange": "NSE",
  "instrument_type": "OPTION",
  "transaction_type": "BUY",
  "order_type": "LIMIT",
  "quantity": 50,
  "price": 18500.00,
  "trigger_price": 18450.00,
  "stoploss": 18400.00,
  "target": 18600.00,
  "broker_id": "uuid",
  "strategy_id": "uuid"
}
```

#### PUT /orders/{order_id}
Modify an existing order.

**Headers**: `Authorization: Bearer <token>`

#### DELETE /orders/{order_id}
Cancel an order.

**Headers**: `Authorization: Bearer <token>`

### 7. Positions

#### GET /positions
Get current positions.

**Headers**: `Authorization: Bearer <token>`

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "position_id": "uuid",
      "symbol": "NIFTY",
      "exchange": "NSE",
      "quantity": 50,
      "average_price": 18500.00,
      "current_price": 18520.00,
      "unrealized_pnl": 1000.00,
      "realized_pnl": 0.00,
      "status": "OPEN",
      "opened_at": "2024-01-01T12:00:00Z"
    }
  ]
}
```

#### GET /positions/{position_id}
Get position details.

**Headers**: `Authorization: Bearer <token>`

#### POST /positions/{position_id}/close
Close a position.

**Headers**: `Authorization: Bearer <token>`

### 8. Analytics

#### GET /analytics/performance
Get overall performance metrics.

**Headers**: `Authorization: Bearer <token>`

**Query Parameters**:
- `from_date`: Start date
- `to_date`: End date
- `strategy_id`: Filter by strategy

**Response**:
```json
{
  "success": true,
  "data": {
    "total_trades": 100,
    "winning_trades": 60,
    "losing_trades": 40,
    "win_rate": 60.00,
    "total_pnl": 50000.00,
    "max_drawdown": 10000.00,
    "sharpe_ratio": 1.5,
    "average_trade": 500.00,
    "profit_factor": 1.8
  }
}
```

#### GET /analytics/pnl
Get P&L breakdown.

**Headers**: `Authorization: Bearer <token>`

#### GET /analytics/trades
Get detailed trade analytics.

**Headers**: `Authorization: Bearer <token>`

## WebSocket Connections

### Authentication

WebSocket connections require authentication via query parameter:

```
wss://api.veloxtrading.com/ws?token=<jwt_token>
```

### Channels

#### 1. Market Data Channel

**Subscribe**: `market-data:{symbol}`

**Message Format**:
```json
{
  "channel": "market-data:NIFTY",
  "type": "tick|candle|forming_candle",
  "data": {
    "timestamp": "2024-01-01T12:00:00.123Z",
    "price": 18500.00,
    "volume": 1000,
    "open": 18500.00,
    "high": 18550.00,
    "low": 18480.00,
    "close": 18520.00,
    "is_forming": true
  }
}
```

#### 2. Indicators Channel

**Subscribe**: `indicators:{symbol}:{indicator}`

**Message Format**:
```json
{
  "channel": "indicators:NIFTY:rsi",
  "data": {
    "timestamp": "2024-01-01T12:00:00.123Z",
    "value": 45.5,
    "period": 14,
    "is_realtime": true,
    "forming_candle": {
      "open": 18500.00,
      "high": 18550.00,
      "low": 18480.00,
      "close": 18520.00,
      "volume": 1000
    }
  }
}
```

#### 3. Positions Channel

**Subscribe**: `positions`

**Message Format**:
```json
{
  "channel": "positions",
  "type": "update|open|close",
  "data": {
    "position_id": "uuid",
    "symbol": "NIFTY",
    "quantity": 50,
    "current_price": 18520.00,
    "unrealized_pnl": 1000.00,
    "status": "OPEN"
  }
}
```

#### 4. Orders Channel

**Subscribe**: `orders`

**Message Format**:
```json
{
  "channel": "orders",
  "type": "placed|filled|cancelled|rejected",
  "data": {
    "order_id": "uuid",
    "symbol": "NIFTY",
    "status": "FILLED",
    "filled_quantity": 50,
    "average_price": 18500.00
  }
}
```

#### 5. Strategy Channel

**Subscribe**: `strategy:{strategy_id}`

**Message Format**:
```json
{
  "channel": "strategy:uuid",
  "type": "started|stopped|signal|error",
  "data": {
    "signal": "BUY|SELL|HOLD",
    "symbol": "NIFTY",
    "price": 18500.00,
    "confidence": 0.85
  }
}
```

#### 6. System Alerts Channel

**Subscribe**: `alerts`

**Message Format**:
```json
{
  "channel": "alerts",
  "type": "risk|system|broker",
  "severity": "info|warning|error|critical",
  "message": "Daily loss limit reached",
  "data": {
    "current_loss": 10000.00,
    "limit": 10000.00
  }
}
```

### WebSocket API

#### Subscribe to Channel

```json
{
  "action": "subscribe",
  "channel": "market-data:NIFTY"
}
```

#### Unsubscribe from Channel

```json
{
  "action": "unsubscribe",
  "channel": "market-data:NIFTY"
}
```

#### List Subscriptions

```json
{
  "action": "list"
}
```

## Error Codes

| Code | Description |
|------|-------------|
| AUTH_001 | Invalid credentials |
| AUTH_002 | Token expired |
| AUTH_003 | Invalid token |
| AUTH_004 | Insufficient permissions |
| USER_001 | User not found |
| USER_002 | User already exists |
| BROKER_001 | Broker not found |
| BROKER_002 | Invalid broker credentials |
| BROKER_003 | Broker API error |
| STRATEGY_001 | Strategy not found |
| STRATEGY_002 | Invalid strategy parameters |
| STRATEGY_003 | Strategy already running |
| TRADE_001 | Invalid order parameters |
| TRADE_002 | Insufficient margin |
| TRADE_003 | Market closed |
| TRADE_004 | Order rejected |
| RISK_001 | Risk limit exceeded |
| RISK_002 | Position size exceeded |
| SYSTEM_001 | Internal server error |
| SYSTEM_002 | Database error |
| SYSTEM_003 | Rate limit exceeded |

## Rate Limiting

- **Authentication endpoints**: 5 requests per minute
- **Market data endpoints**: 60 requests per minute
- **Trading endpoints**: 30 requests per minute
- **Other endpoints**: 100 requests per minute

## SDK Examples

### Java SDK Example

```java
import com.velox.trading.VeloxClient;

public class TradingExample {
    public static void main(String[] args) {
        // Initialize client
        VeloxClient client = new VeloxClient("your_api_key");
        
        // Authenticate
        client.login("username", "password");
        
        // Get market data
        List<CandleData> candles = client.getMarketData("NIFTY", "5min");
        
        // Place order
        Order order = client.placeOrder(
            "NIFTY",
            TransactionType.BUY,
            50,
            OrderType.MARKET
        );
        
        // Subscribe to real-time data
        client.subscribe("market-data:NIFTY", new MarketDataListener() {
            @Override
            public void onTick(TickData data) {
                System.out.println("Received tick: " + data);
            }
        });
    }
}
```

### JavaScript SDK Example

```javascript
import { VeloxClient } from 'velox-trading-js';

const client = new VeloxClient();

// Authenticate
await client.login('username', 'password');

// Get positions
const positions = await client.getPositions();

// Subscribe to WebSocket
client.ws.subscribe('positions', (data) => {
    console.log('Position update:', data);
});
```

This API specification provides a comprehensive interface for all functionality required by the multi-user algotrading system, ensuring secure, efficient, and real-time access to all trading operations and data.