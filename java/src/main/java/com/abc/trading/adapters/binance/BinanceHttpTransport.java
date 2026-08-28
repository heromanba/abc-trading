package com.abc.trading.adapters.binance;

import java.util.Map;

/** Injectable HTTP boundary for Binance REST calls and offline contract tests. */
public interface BinanceHttpTransport {
    String request(String method, String path, Map<String, String> parameters, Map<String, String> headers);
}
