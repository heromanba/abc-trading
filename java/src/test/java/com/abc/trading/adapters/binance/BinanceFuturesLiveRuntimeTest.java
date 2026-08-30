package com.abc.trading.adapters.binance;

import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.portfolio.AccountStateEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceFuturesLiveRuntimeTest {
    @Test
    void bridgesBinanceMarketAndUserEventsIntoCoreEvents() {
        List<Object> events = new ArrayList<>();
        BinanceFuturesLiveRuntime runtime = runtime(new FakeHttp());
        BinanceFuturesLiveRuntime wired = new BinanceFuturesLiveRuntime(
                runtime.adapter().config(), new FakeHttp(), events::add);

        wired.acceptMarketPayload("{\"stream\":\"btcusdt@depth@100ms\",\"data\":{\"e\":\"depthUpdate\",\"E\":1000,\"T\":999,\"s\":\"BTCUSDT\",\"U\":10,\"u\":12,\"pu\":9,\"b\":[[\"100.10\",\"2\"]],\"a\":[[\"100.20\",\"3\"]]}}");
        wired.acceptMarketPayload("{\"e\":\"markPriceUpdate\",\"E\":1001,\"s\":\"BTCUSDT\",\"p\":\"100.15\",\"i\":\"100.10\",\"T\":2000}");
        wired.acceptUserPayload("{\"e\":\"ORDER_TRADE_UPDATE\",\"E\":1002,\"o\":{\"s\":\"BTCUSDT\",\"i\":42,\"c\":\"client-1\",\"S\":\"BUY\",\"o\":\"MARKET\",\"f\":\"GTC\",\"x\":\"TRADE\",\"X\":\"FILLED\",\"l\":\"1\",\"L\":\"100.20\",\"n\":\"0\",\"N\":\"USDT\",\"R\":false}}");
        wired.acceptUserPayload("{\"e\":\"ACCOUNT_UPDATE\",\"E\":1003,\"a\":{\"B\":[{\"a\":\"USDT\",\"wb\":\"1000.5\",\"cw\":\"900.25\"}],\"P\":[{\"s\":\"BTCUSDT\",\"up\":\"-2.5\"}]}}");

        assertTrue(events.stream().anyMatch(OrderBookSnapshot.class::isInstance));
        assertTrue(events.stream().anyMatch(MarketDataSnapshot.class::isInstance));
        OrderFill fill = events.stream().filter(OrderFill.class::isInstance)
                .map(OrderFill.class::cast).findFirst().orElseThrow();
        assertEquals("client-1", fill.orderId());
        assertEquals(100.20, fill.price());
        assertEquals(com.abc.trading.data.Quantity.fromString("1", 0), fill.quantity());
        AccountStateEvent account = events.stream().filter(AccountStateEvent.class::isInstance)
            .map(AccountStateEvent.class::cast).findFirst().orElseThrow();
        assertEquals(900.25, account.state().balanceFree(), 1e-9);
        assertEquals(-2.5, account.state().unrealizedPnl(), 1e-9);
    }

    @Test
    void acceptsExactlyIntegralDecimalTradeQuantityWithoutTruncation() {
        List<Object> events = new ArrayList<>();
        BinanceFuturesLiveRuntime runtime = new BinanceFuturesLiveRuntime(
                new BinanceFuturesConfig(BinanceEnvironment.TESTNET, null, null, List.of("BTCUSDT")),
                new FakeHttp(), events::add);

        runtime.acceptMarketPayload("{\"e\":\"aggTrade\",\"E\":1000,\"s\":\"BTCUSDT\",\"a\":7,\"p\":\"100.20\",\"q\":\"1.000\",\"T\":1000,\"m\":true}");

        assertEquals(1, events.stream().filter(com.abc.trading.data.TradeTick.class::isInstance).count());
        assertEquals(com.abc.trading.data.Quantity.fromString("1.000", 3), events.stream().filter(com.abc.trading.data.TradeTick.class::isInstance)
                .map(com.abc.trading.data.TradeTick.class::cast).findFirst().orElseThrow().quantity());
    }

    @Test
    void discoversInstrumentAndSynchronizesAccountThroughRustShapedEndpoints() {
        BinanceFuturesLiveRuntime runtime = runtime(new FakeHttp());

        BinanceInstrumentMetadata instrument = runtime.discoverInstruments().get(0);
        BinanceAccountSnapshot account = runtime.synchronizeAccount();

        assertEquals("BTCUSDT", instrument.symbol());
        assertEquals("BTC", instrument.baseAsset());
        assertEquals("USDT", instrument.quoteAsset());
        assertEquals("0.1", instrument.priceTickSize().toPlainString());
        assertEquals("USDT", account.currency());
        assertEquals("1000.5", account.walletBalance().toPlainString());
        assertEquals("900.25", account.availableBalance().toPlainString());
    }

        @Test
        void authenticatedStartupPublishesInitialAccountStateWithoutOpeningSockets() {
        List<Object> events = new ArrayList<>();
        BinanceFuturesConfig config = new BinanceFuturesConfig(
            BinanceEnvironment.TESTNET, "key", "secret", List.of("BTCUSDT"),
            5_000, Duration.ofSeconds(1), Duration.ofSeconds(1), true, false);
        BinanceFuturesLiveRuntime runtime = new BinanceFuturesLiveRuntime(
            config, new FakeHttp(), events::add);

        runtime.start();
        runtime.stop();

        AccountStateEvent event = events.stream().filter(AccountStateEvent.class::isInstance)
            .map(AccountStateEvent.class::cast).findFirst().orElseThrow();
        assertEquals("USDT", event.state().currency());
        assertEquals(1000.5, event.state().balanceTotal(), 1e-9);
        assertEquals(900.25, event.state().balanceFree(), 1e-9);
        }

    @Test
    void rejectsOrdersThatDoNotMatchExchangeInfoFiltersBeforeRest() {
        CountingHttp http = new CountingHttp();
        BinanceFuturesLiveRuntime runtime = runtime(http);
        runtime.refreshInstrumentMetadata();

        assertThrows(IllegalArgumentException.class, () -> runtime.submitLimitOrder(
                new com.abc.trading.execution.LimitOrderIntent("s", "BTCUSDT", 1, 100,
                        "corr", "bad", com.abc.trading.execution.SignalDirection.BUY,
                        1, 100.05, 0, 0.0)));
        assertEquals(1, http.exchangeInfoRequests);
        assertEquals(0, http.orderRequests);
    }

    @Test
    void reportsDepthGapsAndRebuildsTheAffectedSymbolFromRest() {
        List<Object> events = new ArrayList<>();
        CountingHttp http = new CountingHttp();
        BinanceFuturesLiveRuntime runtime = new BinanceFuturesLiveRuntime(
                new BinanceFuturesConfig(BinanceEnvironment.TESTNET, "key", "secret", List.of("BTCUSDT")),
                http, events::add);

        runtime.acceptMarketPayload("{\"e\":\"depthUpdate\",\"E\":1000,\"T\":999,\"s\":\"BTCUSDT\",\"U\":10,\"u\":12,\"pu\":9,\"b\":[[\"100.0\",\"1\"]],\"a\":[[\"101.0\",\"1\"]]}");
        runtime.acceptMarketPayload("{\"e\":\"depthUpdate\",\"E\":1001,\"T\":1000,\"s\":\"BTCUSDT\",\"U\":20,\"u\":21,\"pu\":99,\"b\":[],\"a\":[]}");

        assertEquals(1, http.depthRequests);
        assertTrue(events.stream().anyMatch(event -> event instanceof IllegalStateException
                && event.toString().contains("depth update gap")));
    }

    private static BinanceFuturesLiveRuntime runtime(BinanceHttpTransport http) {
        return new BinanceFuturesLiveRuntime(
                new BinanceFuturesConfig(BinanceEnvironment.TESTNET, "key", "secret", List.of("BTCUSDT")),
                http, ignored -> { });
    }

    private static class FakeHttp implements BinanceHttpTransport {
        @Override
        public String request(String method, String path, Map<String, String> parameters,
                Map<String, String> headers) {
            if (path.equals("/fapi/v1/exchangeInfo")) {
                return "{\"symbols\":[{\"symbol\":\"BTCUSDT\",\"baseAsset\":\"BTC\",\"quoteAsset\":\"USDT\",\"requiredMarginPercent\":\"5\",\"maintMarginPercent\":\"2.5\",\"filters\":[{\"filterType\":\"PRICE_FILTER\",\"tickSize\":\"0.1\"},{\"filterType\":\"LOT_SIZE\",\"stepSize\":\"0.001\",\"minQty\":\"0.001\"}]}]}";
            }
            if (path.equals("/fapi/v2/account")) {
                return "{\"updateTime\":1000,\"assets\":[{\"asset\":\"USDT\",\"walletBalance\":\"1000.5\",\"availableBalance\":\"900.25\",\"initialMargin\":\"50\",\"maintMargin\":\"25\",\"unrealizedProfit\":\"-2.5\"}]}";
            }
            return "{}";
        }
    }

    private static final class CountingHttp extends FakeHttp {
        private int exchangeInfoRequests;
        private int orderRequests;
        private int depthRequests;

        @Override
        public String request(String method, String path, Map<String, String> parameters,
                Map<String, String> headers) {
            if (path.equals("/fapi/v1/exchangeInfo")) exchangeInfoRequests++;
            if (path.equals("/fapi/v1/order")) orderRequests++;
            if (path.equals("/fapi/v1/depth")) {
                depthRequests++;
                return "{\"lastUpdateId\":15,\"bids\":[[\"100.0\",\"1\"]],\"asks\":[[\"101.0\",\"1\"]]}";
            }
            return super.request(method, path, parameters, headers);
        }
    }
}
