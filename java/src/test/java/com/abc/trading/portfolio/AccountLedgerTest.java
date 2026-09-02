package com.abc.trading.portfolio;

import com.abc.trading.cache.Cache;
import com.abc.trading.data.TickScheme;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.FxRateUpdate;
import com.abc.trading.data.MarginModelType;
import com.abc.trading.data.DerivativeType;
import com.abc.trading.data.FundingRateUpdate;
import com.abc.trading.data.InstrumentSpec;
import com.abc.trading.data.Quantity;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals(0.0, open.marginInitial(), 1e-9);
        assertEquals(25.0, open.balanceLocked(), 1e-9);
        assertEquals(974.0, open.balanceFree(), 1e-9);

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

        assertEquals(0.0, state.marginInitial(), 1e-9);
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
    void positiveEquityBelowMaintenanceIsLiquidationRequired() {
        Cache cache = new Cache();
        cache.addInstrument("AAPL", "XNAS", TickScheme.fixed(0.01),
                "AAPL", "USD", 0.10, 2.0);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 120.0, "USD", 1.0);
        portfolio.applyOrderIntent(order("buy", SignalDirection.BUY, 1, 100.0));
        portfolio.applyFill(fill("buy", SignalDirection.BUY, 1, 100.0, 0.0));

        AccountState state = portfolio.applyMarketData(new MarketDataSnapshot(
                "AAPL", 101, 51.0, 52.0, 51.0, 51.0, 51.0, 1));

        assertTrue(state.equity() > 0.0);
        assertTrue(state.marginCall());
        assertTrue(state.liquidationRequired());
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

        @Test
        void standardMarginIgnoresLeverageAndInverseMarginUsesReciprocalPrice() {
        Cache cache = new Cache();
        cache.addInstrument("STD", "XNAS", TickScheme.fixed(0.01),
            "STD", "USD", 0.10, 0.05, MarginModelType.STANDARD_NOTIONAL_RATE, 0.0, 0.0);
        cache.addInstrument("INV", "XNAS", TickScheme.fixed(0.01),
            "INV", "USD", 0.10, 0.05, MarginModelType.INVERSE_NOTIONAL_RATE, 0.0, 0.0);
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("XNAS", 100_000.0, "USD", 2.0);
        portfolio.setFxRate("INV", "USD", 1.0);
        portfolio.applyOrderIntent(new OrderIntent("strategy", "STD", 1, 100, "corr", "std",
            SignalDirection.BUY, 10, 100.0, 0, 0.0));
        portfolio.applyOrderIntent(new OrderIntent("strategy", "INV", 1, 100, "corr", "inv",
            SignalDirection.BUY, 10, 100.0, 0, 0.0));

        AccountState state = portfolio.accountState("XNAS", 100);

        assertEquals(100.005, state.balanceLocked(), 1e-9);
        }

        @Test
        void calculatesLinearAndInverseContractPnlWithMultiplier() {
        InstrumentSpec linear = derivative("LINEAR", DerivativeType.LINEAR_FUTURE, "10", "USD");
        InstrumentSpec inverse = derivative("INVERSE", DerivativeType.INVERSE_FUTURE, "2", "BTC");

        assertEquals(new java.math.BigDecimal("200"),
            linear.calculatePnl(new java.math.BigDecimal("2"), new java.math.BigDecimal("100"), new java.math.BigDecimal("110")));
        assertEquals(new java.math.BigDecimal("0.0004"),
            inverse.calculatePnl(new java.math.BigDecimal("-2"), new java.math.BigDecimal("10000"), new java.math.BigDecimal("5000")));
        }

        @Test
        void settlesPerpetualFundingOnceWithLongAndShortSigns() {
        Cache cache = new Cache();
        cache.addInstrument(derivative("PERP", DerivativeType.LINEAR_PERPETUAL, "1", "USDT"));
        Portfolio portfolio = new Portfolio(cache);
        portfolio.configureAccount("BINANCE", 10_000.0, "USDT", 1.0);
        portfolio.applyOrderIntent(new OrderIntent("strategy", "PERP", 1, 100, "open-corr", "open",
            SignalDirection.BUY, Quantity.fromString("1", 0), 100.0, 0, 0.0));
        portfolio.applyFill(new OrderFill("strategy", "PERP", 1, 100, "open-corr", "open",
            SignalDirection.BUY, Quantity.fromString("1", 0), 100.0, java.math.BigDecimal.ZERO, 0.0,
            new Commission(0.0, "USDT"), LiquiditySide.TAKER, ""));
        FundingRateUpdate update = new FundingRateUpdate("PERP", new java.math.BigDecimal("0.01"), 1_000, 1_000, 1);

        FundingPayment payment = portfolio.applyFunding(update);
        assertNotNull(payment);
        assertEquals(0, new java.math.BigDecimal("-1.00").compareTo(payment.amount()));
        assertNull(portfolio.applyFunding(update));
        assertEquals(0, new java.math.BigDecimal("9999.00")
            .compareTo(portfolio.accountState("BINANCE", 1_000).balanceTotalDecimal()));
        }

        @Test
        void isolatedThresholdsDoNotNetAgainstAnotherPosition() {
        InstrumentSpec first = derivative("FIRST", DerivativeType.LINEAR_PERPETUAL, "1", "USD");
        InstrumentSpec second = derivative("SECOND", DerivativeType.LINEAR_PERPETUAL, "1", "USD");
        Cache cache = new Cache();
        cache.addInstrument(first);
        cache.addInstrument(second);

        AccountLedger cross = new AccountLedger();
        cross.configure("X", new java.math.BigDecimal("100"), "USD", java.math.BigDecimal.ONE,
            AccountType.MARGIN, MarginMode.CROSS);
        cross.updatePosition("X", first, java.math.BigDecimal.ONE, 100.0, 1);
        cross.updatePosition("X", second, java.math.BigDecimal.ONE, 100.0, 1);
        cross.updateMarketPrice("X", first, 0.01, 2);
        cross.updateMarketPrice("X", second, 200.0, 2);
        assertFalse(cross.state("X", 2).liquidationRequired());

        AccountLedger isolated = new AccountLedger();
        isolated.configure("X", new java.math.BigDecimal("100"), "USD", java.math.BigDecimal.ONE,
            AccountType.MARGIN, MarginMode.ISOLATED);
        isolated.updatePosition("X", first, java.math.BigDecimal.ONE, 100.0, 1);
        isolated.updatePosition("X", second, java.math.BigDecimal.ONE, 100.0, 1);
        isolated.updateMarketPrice("X", first, 0.01, 2);
        isolated.updateMarketPrice("X", second, 200.0, 2);
        assertTrue(isolated.state("X", 2).liquidationRequired());
        }

        private static InstrumentSpec derivative(String symbol, DerivativeType type, String multiplier,
            String settlementCurrency) {
        return new InstrumentSpec(symbol, "BINANCE", TickScheme.fixed(0.01), "BTC", settlementCurrency,
            0.10, 0.05, MarginModelType.NOTIONAL_RATE, 0.0, 0.0,
            0, java.math.BigDecimal.ONE, 2, new java.math.BigDecimal("0.01"),
            type, new java.math.BigDecimal(multiplier), settlementCurrency);
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
