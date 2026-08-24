package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.TickScheme;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.data.MarginModelType;
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

    @Test
    void cashAccountSettlesNotionalAndDoesNotLockPositionMargin() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 1.0, AccountType.CASH);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 2, 100.0));

        AccountState reserved = portfolio.accountState("XNAS", 100);
        assertEquals(200.0, reserved.balanceLocked(), 1e-9);
        assertEquals(800.0, reserved.balanceFree(), 1e-9);

        portfolio.applyFill(fill("buy", SignalDirection.BUY, 2, 100.0, 1.0));
        AccountState settled = portfolio.accountState("XNAS", 101);
        assertEquals(799.0, settled.balanceTotal(), 1e-9);
        assertEquals(0.0, settled.balanceLocked(), 1e-9);
        assertEquals(799.0, settled.balanceFree(), 1e-9);
    }

    @Test
    void accountSupportsAdditionalCurrencyBalances() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 1.0);
        portfolio.deposit("XNAS", "EUR", 250.0);

        AccountState state = portfolio.accountState("XNAS", 100);

        assertEquals(2, state.balances().size());
        assertEquals(250.0, state.balances().get("EUR").free(), 1e-9);
    }

    @Test
    void instrumentMarginRatesControlInitialAndMaintenanceRequirements() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS", TickScheme.fixed(0.01),
                "AAPL", "USD", 0.10, 0.05);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 2.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 2, 100.0));
        portfolio.applyFill(fill("buy", SignalDirection.BUY, 2, 100.0, 0.0));

        AccountState state = portfolio.accountState("XNAS", 100);

        assertEquals(10.0, state.marginInitial(), 1e-9);
        assertEquals(5.0, state.marginMaintenance(), 1e-9);
    }

    @Test
    void cashAccountOnlyAllowsSellingHeldPosition() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 1.0, AccountType.CASH);
        RiskEngine risk = new RiskEngine(100, cache, portfolio);

        assertFalse(risk.evaluate(order("sell", SignalDirection.SELL, 1, 100.0)).approved());
        cache.updatePosition("AAPL", 1);
        assertTrue(risk.evaluate(order("sell-held", SignalDirection.SELL, 1, 100.0)).approved());
    }

    @Test
    void convertsQuotedInstrumentMarginIntoAccountCurrency() {
        Cache cache = new Cache();
        cache.addInstrument("DAX", "XEUR", TickScheme.fixed(0.01),
                "DAX", "EUR", 1.0, 0.5);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XEUR", 1_000.0, "USD", 1.0);
        RiskEngine risk = new RiskEngine(100, cache, portfolio);
        OrderIntent order = new OrderIntent("strategy", "DAX", 1, 100, "corr", "eur-buy",
                SignalDirection.BUY, 1, 100.0, 0, 0.0);

        assertFalse(risk.evaluate(order).approved());
        portfolio.applyFxRate(new FxRateUpdate("EUR", "USD", 1.10, 100, 1));
        assertTrue(risk.evaluate(order).approved());
        portfolio.applyOrderIntent(order);

        AccountState state = portfolio.accountState("XEUR", 100);
        assertEquals(110.0, state.balanceLocked(), 1e-9);
        assertEquals(890.0, state.balanceFree(), 1e-9);
    }

        @Test
        void convertsMarginPnlAndCommissionFromTheirOwnCurrencies() {
        Cache cache = new Cache();
        cache.addInstrument("DAX", "XEUR", TickScheme.fixed(0.01),
            "DAX", "EUR", 1.0, 0.5);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XEUR", 1_000.0, "USD", 1.0);
        portfolio.setFxRate("EUR", "USD", 1.10);
        portfolio.applyOrderIntent(new OrderIntent("strategy", "DAX", 1, 100, "corr", "eur-buy",
            SignalDirection.BUY, 1, 100.0, 0, 0.0));
        portfolio.applyFill(new OrderFill("strategy", "DAX", 1, 100, "corr", "eur-buy",
            SignalDirection.BUY, 1, 100.0, 0, 0.0,
            new Commission(2.0, "USD"), LiquiditySide.TAKER, ""));

        assertEquals(998.0, portfolio.accountState("XEUR", 100).balanceTotal(), 1e-9);
        }

    @Test
    void markToMarketProducesUnrealizedPnlAndMarginThresholds() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS", TickScheme.fixed(0.01),
                "AAPL", "USD", 0.10, 0.05);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 50.0, "USD", 1.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 1, 100.0));
        portfolio.applyFill(fill("buy", SignalDirection.BUY, 1, 100.0, 0.0));

        AccountState state = portfolio.applyMarketData(new MarketDataSnapshot(
                "AAPL", 101, 0.01, 0.02, 0.01, 0.01, 0.01, 1));

        assertEquals(-99.99, state.unrealizedPnl(), 1e-9);
        assertEquals(-49.99, state.equity(), 1e-9);
        assertTrue(state.marginCall());
        assertTrue(state.liquidationRequired());
    }

    @Test
    void marginBreachBlocksAdditionalExposureButAllowsReduction() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 149.0, "USD", 1.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 1, 100.0));
        portfolio.applyFill(fill("buy", SignalDirection.BUY, 1, 100.0, 0.0));
        portfolio.applyMarketData(new MarketDataSnapshot(
                "AAPL", 101, 0.01, 0.02, 0.01, 0.01, 0.01, 1));
        RiskEngine risk = new RiskEngine(100, cache, portfolio);

        assertFalse(risk.evaluate(order("more-buy", SignalDirection.BUY, 1, 1.0)).approved());
        assertTrue(risk.evaluate(order("close-buy", SignalDirection.SELL, 1, 1.0)).approved());
    }

    @Test
    void shortMarkToMarketUsesSignedPositionQuantity() {
        Cache cache = cache();
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 1_000.0, "USD", 1.0);
        portfolio.applyOrderIntent(order("sell", SignalDirection.SELL, 2, 100.0));
        portfolio.applyFill(fill("sell", SignalDirection.SELL, 2, 100.0, 0.0));

        AccountState state = portfolio.applyMarketData(new MarketDataSnapshot(
                "AAPL", 101, 90.0, 91.0, 90.5, 90.0, 90.0, 1));

        assertEquals(20.0, state.unrealizedPnl(), 1e-9);
    }

    @Test
    void fixedPerUnitMarginModelControlsDerivativeRequirement() {
        Cache cache = new Cache();
        cache.addInstrument("FUT", "XNAS", TickScheme.fixed(0.01),
            "FUT", "USD", 0.0, 0.0, MarginModelType.FIXED_PER_UNIT, 10.0, 4.0);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 100.0, "USD", 2.0);
        portfolio.applyOrderIntent(new OrderIntent("strategy", "FUT", 1, 100, "corr", "future",
            SignalDirection.BUY, 3, 500.0, 0, 0.0));

        AccountState state = portfolio.accountState("XNAS", 100);

        assertEquals(15.0, state.marginInitial(), 1e-9);
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
