package com.abc.trading.risk;

import com.abc.trading.cache.Cache;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskEngineTest {
    private static OrderIntent order(String symbol, SignalDirection side, int quantity, double price) {
        return new OrderIntent("strategy", symbol, 1, 100, "correlation", "order", side,
                quantity, price, 0, 0.0);
    }

    @Test
    void validatesBasicOrderRiskAndKnownInstrument() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS");
        RiskEngine risk = new RiskEngine(10, cache);

        assertTrue(risk.evaluate(order("AAPL", SignalDirection.BUY, 10, 100.0)).approved());
        assertEquals("unknown instrument: MSFT", risk.evaluate(order("MSFT", SignalDirection.BUY, 1, 100.0)).reason());
        assertEquals("quantity exceeds maxQuantity", risk.evaluate(order("AAPL", SignalDirection.BUY, 11, 100.0)).reason());
        assertEquals("price must be finite and positive", risk.evaluate(order("AAPL", SignalDirection.BUY, 1, 0.0)).reason());
        assertEquals("order side must be BUY or SELL", risk.evaluate(order("AAPL", SignalDirection.HOLD, 1, 100.0)).reason());
    }

    @Test
    void enforcesNotionalAndTradingState() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS");
        RiskEngine risk = new RiskEngine(100, cache);
        risk.setMaxNotionalPerOrder("AAPL", 500.0);

        assertEquals("notional exceeds maxNotionalPerOrder",
                risk.evaluate(order("AAPL", SignalDirection.BUY, 6, 100.0)).reason());
        risk.setTradingState(TradingState.HALTED);
        assertEquals("trading is halted", risk.evaluate(order("AAPL", SignalDirection.BUY, 1, 100.0)).reason());
    }

    @Test
    void reducingStateRejectsExposureIncreaseButAllowsReduction() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS");
        cache.updatePosition("AAPL", 10);
        RiskEngine risk = new RiskEngine(100, cache);
        risk.setTradingState(TradingState.REDUCING);

        assertEquals("trading is reducing exposure",
                risk.evaluate(order("AAPL", SignalDirection.BUY, 1, 100.0)).reason());
        assertTrue(risk.evaluate(order("AAPL", SignalDirection.SELL, 1, 100.0)).approved());
    }
}