package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.TickScheme;
import com.abc.trading.execution.Commission;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.LiquiditySide;
import com.abc.trading.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLedgerTest {
    @Test
    void reservesInitialMarginAndReportsFreeBalance() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 2.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 2, 100.0));

        AccountState state = portfolio.accountState("XNAS", 100);

        assertEquals(1_000.0, state.balanceTotal(), 1e-9);
        assertEquals(100.0, state.balanceLocked(), 1e-9);
        assertEquals(900.0, state.balanceFree(), 1e-9);
        assertEquals(100.0, state.marginInitial(), 1e-9);
    }

    @Test
    void riskRejectsOrderWhenFreeMarginIsInsufficient() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 100.0, "USD", 1.0);
        RiskEngine risk = new RiskEngine(100, cache, portfolio);

        assertFalse(risk.evaluate(order("too-large", SignalDirection.BUY, 2, 100.0)).approved());
        assertEquals("insufficient available margin",
                risk.evaluate(order("too-large", SignalDirection.BUY, 2, 100.0)).reason());
    }

    @Test
    void fillAppliesCommissionAndPnlThenReleasesPositionMargin() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 2.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 1, 100.0));
        OrderFill buy = fill("buy", SignalDirection.BUY, 1, 100.0, 1.0);
        portfolio.applyFill(buy);

        AccountState open = portfolio.accountState("XNAS", 101);
        assertEquals(999.0, open.balanceTotal(), 1e-9);
        assertEquals(50.0, open.marginInitial(), 1e-9);
        assertEquals(949.0, open.balanceFree(), 1e-9);

        portfolio.applyOrderIntent(order("sell", SignalDirection.SELL, 1, 110.0));
        OrderFill sell = fill("sell", SignalDirection.SELL, 1, 110.0, 1.0);
        portfolio.applyFill(sell);

        AccountState closed = portfolio.accountState("XNAS", 102);
        assertEquals(1_008.0, closed.balanceTotal(), 1e-9);
        assertEquals(0.0, closed.marginInitial(), 1e-9);
        assertEquals(1_008.0, closed.balanceFree(), 1e-9);
    }

    private static Cache cache() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS", TickScheme.fixed(0.01));
        return cache;
    }

    private static OrderIntent order(String id, SignalDirection side, int quantity, double price) {
        return new OrderIntent("strategy", "AAPL", 1, 100, id + "-corr", id, side,
                quantity, price, 0, 0.0);
    }

    private static OrderFill fill(String id, SignalDirection side, int quantity, double price, double commission) {
        return new OrderFill("strategy", "AAPL", 1, 100, id + "-corr", id, side,
                quantity, price, 0, 0.0, new Commission(commission, "USD"), LiquiditySide.TAKER, "");
    }
}
