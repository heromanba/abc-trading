package com.abc.trading.adapters.binance;

import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.TriggerType;
import com.abc.trading.execution.TrailingOffsetType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceFuturesAdapterTest {
    @Test
    void usesNautilusUsdMTestnetRoutesAndNormalizedSymbols() {
        BinanceFuturesConfig config = new BinanceFuturesConfig(
                BinanceEnvironment.TESTNET, "key", "secret", List.of("btcusdt"));
        BinanceFuturesAdapter adapter = new BinanceFuturesAdapter(config, new FakeHttp(), null, null);

        assertEquals(List.of("BTCUSDT"), config.symbols());
        assertEquals("https://demo-fapi.binance.com", config.httpBaseUrl());
        assertEquals("wss://stream.binancefuture.com", config.publicWebSocketBaseUrl());
        assertTrue(adapter.marketStreamUrl().contains("btcusdt@depth@100ms"));
        assertTrue(adapter.marketStreamUrl().contains("btcusdt@aggTrade"));
        assertTrue(adapter.marketStreamUrl().contains("btcusdt@markPrice@1s"));
        assertEquals("wss://stream.binancefuture.com/ws?listenKey=listen-key", adapter.userStreamUrl("listen-key"));
    }

    @Test
    void signsBinanceQueryWithHmacSha256() {
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8",
                BinanceHmacSigner.sign("key", "The quick brown fox jumps over the lazy dog"));
    }

    @Test
    void mapsMarketAndUserPayloadsWithoutLosingDecimalValues() {
        List<BinanceDepthUpdate> depths = new ArrayList<>();
        List<BinanceTradeEvent> trades = new ArrayList<>();
        List<BinanceMarkPriceEvent> marks = new ArrayList<>();
        List<BinanceOrderUpdate> orders = new ArrayList<>();
        List<BinanceAccountUpdate> accounts = new ArrayList<>();
        BinanceMessageMapper mapper = new BinanceMessageMapper();
        mapper.dispatchMarket("{\"stream\":\"btcusdt@depth@100ms\",\"data\":{\"e\":\"depthUpdate\",\"E\":1000,\"T\":999,\"s\":\"BTCUSDT\",\"U\":10,\"u\":12,\"pu\":9,\"b\":[[\"100.10\",\"0.125\"]],\"a\":[[\"100.20\",\"2.500\"]]}}", new BinanceMarketDataHandler() {
            @Override public void onDepth(BinanceDepthUpdate update) { depths.add(update); }
        });
        mapper.dispatchMarket("{\"e\":\"aggTrade\",\"E\":1001,\"s\":\"BTCUSDT\",\"a\":7,\"p\":\"100.20\",\"q\":\"0.125\",\"T\":1000,\"m\":true}", new BinanceMarketDataHandler() {
            @Override public void onTrade(BinanceTradeEvent event) { trades.add(event); }
        });
        mapper.dispatchMarket("{\"e\":\"markPriceUpdate\",\"E\":1002,\"s\":\"BTCUSDT\",\"p\":\"99.90\",\"i\":\"99.80\",\"T\":2000}", new BinanceMarketDataHandler() {
            @Override public void onMarkPrice(BinanceMarkPriceEvent event) { marks.add(event); }
        });
        mapper.dispatchUser("{\"e\":\"ORDER_TRADE_UPDATE\",\"E\":1003,\"o\":{\"s\":\"BTCUSDT\",\"i\":42,\"c\":\"client-1\",\"S\":\"BUY\",\"o\":\"LIMIT\",\"f\":\"GTC\",\"x\":\"TRADE\",\"X\":\"PARTIALLY_FILLED\",\"l\":\"0.125\",\"L\":\"100.20\",\"n\":\"0.01\",\"N\":\"USDT\",\"R\":false}}", new BinanceExecutionHandler() {
            @Override public void onOrderUpdate(BinanceOrderUpdate update) { orders.add(update); }
        });
        mapper.dispatchUser("{\"e\":\"ACCOUNT_UPDATE\",\"E\":1004,\"a\":{\"B\":[{\"a\":\"USDT\",\"wb\":\"1000.5\",\"cw\":\"900.25\"}],\"P\":[{\"s\":\"BTCUSDT\",\"up\":\"-2.5\"}]}}", new BinanceExecutionHandler() {
            @Override public void onAccountUpdate(BinanceAccountUpdate update) { accounts.add(update); }
        });

        assertEquals(new BigDecimal("0.125"), depths.get(0).bids().get(0).quantity());
        assertEquals(12, depths.get(0).lastUpdateId());
        assertEquals("SELLER", trades.get(0).aggressorSide());
        assertEquals(new BigDecimal("99.90"), marks.get(0).markPrice());
        assertTrue(orders.get(0).isTrade());
        assertEquals(new BigDecimal("0.125"), orders.get(0).lastQuantity());
        assertEquals(new BigDecimal("1000.5"), accounts.get(0).walletBalances().get("USDT"));
        assertEquals(new BigDecimal("-2.5"), accounts.get(0).unrealizedPnl().get("BTCUSDT"));
    }

    @Test
    void submitsSignedMarketAndLimitRequestsAndMapsResponses() {
        FakeHttp http = new FakeHttp();
        List<BinanceOrderUpdate> updates = new ArrayList<>();
        BinanceFuturesAdapter adapter = new BinanceFuturesAdapter(
                new BinanceFuturesConfig(BinanceEnvironment.TESTNET, "key", "secret", List.of("BTCUSDT")),
                http, new BinanceMarketDataHandler() { }, new BinanceExecutionHandler() {
                    @Override public void onOrderUpdate(BinanceOrderUpdate update) { updates.add(update); }
                });

        adapter.submitMarketOrder(new OrderIntent("s", "BTCUSDT", 1, 100, "corr", "market-1",
                SignalDirection.BUY, 2, 100.0, 0, 0.0, TimeInForce.GTC, 0L,
                0.0, com.abc.trading.execution.TriggerType.NO_TRIGGER, 0.0, 0.0, null));
        assertEquals("POST", http.methods.get(0));
        assertEquals("/fapi/v1/order", http.paths.get(0));
        assertTrue(http.parameters.get(0).containsKey("signature"));
        assertEquals("market-1", http.parameters.get(0).get("newClientOrderId"));
        assertEquals(123, updates.get(0).orderId());
    }

        @Test
        void mapsRustShapedConditionalOrdersToBinanceFuturesTypes() {
        FakeHttp http = new FakeHttp();
        BinanceFuturesAdapter adapter = new BinanceFuturesAdapter(
            new BinanceFuturesConfig(BinanceEnvironment.TESTNET, "key", "secret", List.of("BTCUSDT")),
            http, null, null);

        adapter.submitMarketOrder(new OrderIntent("s", "BTCUSDT", 1, 100, "corr", "stop-1",
            SignalDirection.SELL, 1, 100.0, 0, 0.0, TimeInForce.GTC, 0L,
            90.0, TriggerType.MARK_PRICE, 0.0, 0.0, null));
        adapter.submitMarketOrder(new OrderIntent("s", "BTCUSDT", 1, 100, "corr", "trail-1",
            SignalDirection.SELL, 1, 100.0, 0, 0.0, TimeInForce.GTC, 0L,
            0.0, TriggerType.LAST_PRICE, 95.0, 1.5, TrailingOffsetType.PRICE));

        assertEquals("STOP_MARKET", http.parameters.get(0).get("type"));
        assertEquals("90", http.parameters.get(0).get("stopPrice"));
        assertEquals("TRAILING_STOP_MARKET", http.parameters.get(1).get("type"));
        assertEquals("0.015", http.parameters.get(1).get("callbackRate"));
        assertEquals("95", http.parameters.get(1).get("activationPrice"));
        }

    private static final class FakeHttp implements BinanceHttpTransport {
        private final List<String> methods = new ArrayList<>();
        private final List<String> paths = new ArrayList<>();
        private final List<Map<String, String>> parameters = new ArrayList<>();

        @Override
        public String request(String method, String path, Map<String, String> parameters,
                Map<String, String> headers) {
            methods.add(method);
            paths.add(path);
            this.parameters.add(Map.copyOf(parameters));
            if (path.endsWith("/order")) {
                return "{\"symbol\":\"BTCUSDT\",\"orderId\":123,\"clientOrderId\":\""
                        + parameters.get("newClientOrderId") + "\",\"status\":\"NEW\",\"side\":\"BUY\",\"type\":\"MARKET\"}";
            }
            return "{}";
        }
    }
}
