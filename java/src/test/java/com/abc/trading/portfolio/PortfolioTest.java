package com.abc.trading.portfolio;

import java.math.BigDecimal;
import com.abc.trading.cache.Cache;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.data.Quantity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PortfolioTest {
    @Test
    void storesFractionalPositionWithoutIntegerConversion() {
        Portfolio portfolio = new Portfolio(new Cache());
        OrderFill fill = new OrderFill(
                "strategy", "BTCUSDT", 1, 100, "buy", "fractional-order",
                SignalDirection.BUY, Quantity.fromString("0.001", 3), 100.0, BigDecimal.ZERO, 0.0);

        PositionUpdate update = portfolio.applyFill(fill);

        assertEquals(new BigDecimal("0.001"), portfolio.position("BTCUSDT"));
        assertEquals(new BigDecimal("0.001"), update.position());
    }

    @Test
    void calculatesRealizedPnlWhenAPositionCloses() {
        Portfolio portfolio = new Portfolio(new Cache());
        OrderFill buy = new OrderFill(
                "strategy", "AAPL", 1, 100, "buy", "order-1",
                SignalDirection.BUY, 10, 100.0, 10, 0.0);
        OrderFill sell = new OrderFill(
                "strategy", "AAPL", 2, 200, "sell", "order-2",
                SignalDirection.SELL, 10, 110.0, 0, 0.0);

        portfolio.applyFill(buy);
        PositionUpdate update = portfolio.applyFill(sell);

        assertEquals(BigDecimal.ZERO, portfolio.position("AAPL"));
        assertEquals(100.0, update.realizedPnl());
        assertEquals(100.0, portfolio.realizedPnl("AAPL"));
    }
}
