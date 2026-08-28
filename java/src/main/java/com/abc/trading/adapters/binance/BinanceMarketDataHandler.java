package com.abc.trading.adapters.binance;

public interface BinanceMarketDataHandler {
    default void onDepth(BinanceDepthUpdate update) { }
    default void onTrade(BinanceTradeEvent event) { }
    default void onMarkPrice(BinanceMarkPriceEvent event) { }
    default void onError(Throwable error) { }
}
