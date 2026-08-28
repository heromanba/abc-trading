package com.abc.trading.adapters.binance;

import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.system.NautilusKernel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BinanceKernelIntegrationTest {
    @Test
    void kernelRoutesBinanceMarketAndUserEventsThroughCoreBus() {
        List<Object> events = new ArrayList<>();
        NautilusKernel kernel = new NautilusKernel();
        kernel.bus().subscribe(MarketDataSnapshot.class, events::add);
        kernel.bus().subscribe(OrderFill.class, events::add);
        BinanceFuturesLiveRuntime runtime = kernel.addBinanceFutures(
            new BinanceFuturesConfig(BinanceEnvironment.TESTNET, null, null, List.of("BTCUSDT"),
                5_000, Duration.ofSeconds(1), Duration.ofSeconds(1), true, false),
                new FakeHttp());
        kernel.addInstrument("BTCUSDT", "BINANCE");
        kernel.start();
        runtime.acceptMarketPayload("{\"e\":\"markPriceUpdate\",\"E\":1000,\"s\":\"BTCUSDT\",\"p\":\"100.15\",\"i\":\"100.10\",\"T\":2000}");
        kernel.bus().publish(new OrderIntent("strategy", "BTCUSDT", 1, 100, "corr", "client-1",
                SignalDirection.BUY, 1, 100.0, 0, 0.0));
        runtime.acceptUserPayload("{\"e\":\"ORDER_TRADE_UPDATE\",\"E\":1001,\"o\":{\"s\":\"BTCUSDT\",\"i\":42,\"c\":\"client-1\",\"S\":\"BUY\",\"o\":\"MARKET\",\"f\":\"GTC\",\"x\":\"TRADE\",\"X\":\"FILLED\",\"l\":\"1\",\"L\":\"100.20\",\"n\":\"0\",\"N\":\"USDT\",\"R\":false}}");
        kernel.stop();

        assertTrue(events.stream().anyMatch(MarketDataSnapshot.class::isInstance));
        OrderFill fill = events.stream().filter(OrderFill.class::isInstance)
                .map(OrderFill.class::cast).findFirst().orElseThrow();
        assertEquals("client-1", fill.orderId());
        assertEquals(42L, Long.parseLong(fill.venueOrderId()));
    }

    private static final class FakeHttp implements BinanceHttpTransport {
        @Override
        public String request(String method, String path, Map<String, String> parameters,
                Map<String, String> headers) {
            if (path.equals("/fapi/v1/listenKey")) return "{\"listenKey\":\"test-key\"}";
            if (path.equals("/fapi/v1/order")) return "{\"symbol\":\"BTCUSDT\",\"orderId\":42,\"clientOrderId\":\"client-1\",\"status\":\"NEW\",\"side\":\"BUY\",\"type\":\"MARKET\"}";
            return "{}";
        }
    }
}
