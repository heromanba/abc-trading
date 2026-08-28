package com.abc.trading.adapters.binance;

public interface BinanceExecutionHandler {
    default void onOrderUpdate(BinanceOrderUpdate update) { }
    default void onAccountUpdate(BinanceAccountUpdate update) { }
    default void onError(Throwable error) { }
}
