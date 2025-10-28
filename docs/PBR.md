i want to develop a multi-user algotrading system for NSE INDIA, specilised in intraday options and stock trading
act like trader who analyses multiple candlesticks charts along with indicators in realtime. he trades and observes the open position for safe exit. sometimes he trails stoploss. remember this happens in realtime
indicators calculationm is based on historical candles plus current forming candle (formed by tick by tick) so that indcator values updated tick by tick.
once the position is open , the open trades are observed to take profit.
it must be accurate and robust
multi-broker: implement broker adapter so that i can easily plug and play others brokers
user will select live mode or dry mode
everything is logged and saved in database.
user must be able to analyse strategies tick by tick.
as risk management, user will enter maximum loss he can afford for that day, when loss or drawdown reaches that amount all trades will get closed.
add strategy adapter so that we can plug and play strategies.
add investor account who can only see the trades
dashbaord to analyse the trades and overall system
