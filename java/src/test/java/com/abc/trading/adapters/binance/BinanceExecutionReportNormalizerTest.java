package com.abc.trading.adapters.binance;

import com.abc.trading.execution.ExecutionReport;
import com.abc.trading.execution.OrderEventType;
import com.abc.trading.execution.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BinanceExecutionReportNormalizerTest {
    @Test
    void normalizesTradeAndPreservesExchangeIdentity() {
        BinanceOrderUpdate update = new BinanceOrderUpdate(
                "BTCUSDT", 1000, 42, "client-1", "BUY", "MARKET", "GTC",
                "TRADE", "PARTIALLY_FILLED", new BigDecimal("0.125"),
                new BigDecimal("100.20"), new BigDecimal("0.01"), "USDT", false);

        ExecutionReport report = BinanceExecutionReportNormalizer.normalize(update);

        assertEquals("42", report.exchangeOrderId());
        assertEquals("client-1", report.clientOrderId());
        assertEquals(OrderEventType.PARTIALLY_FILLED, report.eventType());
        assertEquals(OrderStatus.PARTIALLY_FILLED, report.status());
        assertEquals(new BigDecimal("100.20"), report.lastPrice().asDecimal());
        assertEquals(1_000_000_000L, report.eventTimeNs());
    }

    @Test
    void normalizesCancelAsTerminalLifecycleEvent() {
        BinanceOrderUpdate update = new BinanceOrderUpdate(
                "BTCUSDT", 2000, 42, "client-1", "BUY", "LIMIT", "GTC",
                "CANCELED", "CANCELED", BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ZERO, "USDT", false);

        ExecutionReport report = BinanceExecutionReportNormalizer.normalize(update);

        assertEquals(OrderEventType.CANCELED, report.eventType());
        assertEquals(OrderStatus.CANCELED, report.status());
    }
}
