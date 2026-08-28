package com.abc.trading.adapters.binance;

import java.time.Duration;
import java.util.List;

/** Binance USD-M Futures transport and credential configuration. */
public record BinanceFuturesConfig(
        BinanceEnvironment environment,
        String apiKey,
        String apiSecret,
        List<String> symbols,
        long recvWindowMs,
        Duration requestTimeout,
        Duration reconnectDelay,
        boolean useGtd,
        boolean connectOnStart) {
    public BinanceFuturesConfig {
        if (environment == null) throw new IllegalArgumentException("environment is required");
        if (symbols == null || symbols.isEmpty()) throw new IllegalArgumentException("symbols are required");
        symbols = symbols.stream().map(BinanceFuturesConfig::normalizeSymbol).toList();
        if (recvWindowMs <= 0 || recvWindowMs > 60_000) throw new IllegalArgumentException("recvWindowMs must be in 1..60000");
        if (requestTimeout == null || requestTimeout.isNegative() || requestTimeout.isZero()) {
            throw new IllegalArgumentException("requestTimeout must be positive");
        }
        if (reconnectDelay == null || reconnectDelay.isNegative() || reconnectDelay.isZero()) {
            throw new IllegalArgumentException("reconnectDelay must be positive");
        }
        if ((apiKey == null) != (apiSecret == null)) throw new IllegalArgumentException("apiKey and apiSecret must be provided together");
    }

    public BinanceFuturesConfig(BinanceEnvironment environment, String apiKey, String apiSecret, List<String> symbols) {
        this(environment, apiKey, apiSecret, symbols, 5_000, Duration.ofSeconds(10), Duration.ofSeconds(2), true, true);
    }

    public BinanceFuturesConfig(BinanceEnvironment environment, String apiKey, String apiSecret,
            List<String> symbols, long recvWindowMs, Duration requestTimeout, Duration reconnectDelay) {
        this(environment, apiKey, apiSecret, symbols, recvWindowMs, requestTimeout, reconnectDelay, true);
    }

    public BinanceFuturesConfig(BinanceEnvironment environment, String apiKey, String apiSecret,
            List<String> symbols, long recvWindowMs, Duration requestTimeout, Duration reconnectDelay,
            boolean useGtd) {
        this(environment, apiKey, apiSecret, symbols, recvWindowMs, requestTimeout, reconnectDelay, useGtd, true);
    }

    public boolean authenticated() {
        return apiKey != null && apiSecret != null;
    }

    public String httpBaseUrl() {
        return switch (environment) {
            case LIVE -> "https://fapi.binance.com";
            case TESTNET, DEMO -> "https://demo-fapi.binance.com";
        };
    }

    public String publicWebSocketBaseUrl() {
        return switch (environment) {
            case LIVE -> "wss://fstream.binance.com/public";
            case TESTNET -> "wss://stream.binancefuture.com";
            case DEMO -> "wss://demo-fstream.binance.com";
        };
    }

    public String privateWebSocketBaseUrl() {
        return switch (environment) {
            case LIVE -> "wss://fstream.binance.com/private";
            case TESTNET -> "wss://stream.binancefuture.com";
            case DEMO -> "wss://demo-fstream.binance.com";
        };
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        return symbol.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
