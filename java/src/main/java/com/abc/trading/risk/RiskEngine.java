package com.abc.trading.risk;

import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.cache.Cache;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.portfolio.Portfolio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal synchronous risk boundary for trading commands. */
public final class RiskEngine {
    private final int maxQuantity;
    private final Cache cache;
    private final Portfolio portfolio;
    private final Map<String, Double> maxNotionalPerOrder = new LinkedHashMap<>();
    private TradingState tradingState = TradingState.ACTIVE;

    public RiskEngine(int maxQuantity) {
        this(maxQuantity, null);
    }

    public RiskEngine(int maxQuantity, Cache cache) {
        this(maxQuantity, cache, null);
    }

    public RiskEngine(int maxQuantity, Cache cache, Portfolio portfolio) {
        if (maxQuantity <= 0) throw new IllegalArgumentException("maxQuantity must be positive");
        this.maxQuantity = maxQuantity;
        this.cache = cache;
        this.portfolio = portfolio;
    }

    public RiskDecision evaluate(OrderIntent order) {
        if (order == null) return RiskDecision.rejected("order is required");
        if (tradingState == TradingState.HALTED) return RiskDecision.rejected("trading is halted");
        if (cache != null && !cache.hasInstrument(order.symbol())) {
            return RiskDecision.rejected("unknown instrument: " + order.symbol());
        }
        if (order.side() == null || order.side() == SignalDirection.HOLD) {
            return RiskDecision.rejected("order side must be BUY or SELL");
        }
        if (order.quantity() == null || order.quantity().isZero()) return RiskDecision.rejected("quantity must be positive");
        if (order.quantity().asDouble() > maxQuantity) {
            return RiskDecision.rejected("quantity exceeds maxQuantity");
        }
        if (order.trailingOffsetType() == null && (!Double.isFinite(order.price()) || order.price() <= 0.0)) {
            return RiskDecision.rejected("price must be finite and positive");
        }
        Double maxNotional = maxNotionalPerOrder.get(order.symbol());
        if (maxNotional != null && order.quantity().asDouble() * order.price() > maxNotional) {
            return RiskDecision.rejected("notional exceeds maxNotionalPerOrder");
        }
        if (tradingState == TradingState.REDUCING && cache != null) {
            int position = cache.position(order.symbol());
            boolean increasesExposure = position > 0 && order.side() == SignalDirection.BUY
                    || position < 0 && order.side() == SignalDirection.SELL;
            if (increasesExposure) return RiskDecision.rejected("trading is reducing exposure");
        }
        if (portfolio != null && !portfolio.canReserve(order)) {
            return RiskDecision.rejected("insufficient available margin");
        }
        return RiskDecision.allow();
    }

    public RiskDecision evaluate(LimitOrderIntent order) {
        if (order == null) return RiskDecision.rejected("order is required");
        if (tradingState == TradingState.HALTED) return RiskDecision.rejected("trading is halted");
        if (cache != null && !cache.hasInstrument(order.symbol())) {
            return RiskDecision.rejected("unknown instrument: " + order.symbol());
        }
        if (order.side() == null || order.side() == SignalDirection.HOLD) {
            return RiskDecision.rejected("order side must be BUY or SELL");
        }
        if (order.quantity() == null || order.quantity().isZero()) return RiskDecision.rejected("quantity must be positive");
        if (order.quantity().asDouble() > maxQuantity) {
            return RiskDecision.rejected("quantity exceeds maxQuantity");
        }
        if (order.trailingOffsetType() == null && (!Double.isFinite(order.limitPrice()) || order.limitPrice() <= 0.0)) {
            return RiskDecision.rejected("limitPrice must be finite and positive");
        }
        Double maxNotional = maxNotionalPerOrder.get(order.symbol());
        if (maxNotional != null && order.quantity().asDouble() * order.limitPrice() > maxNotional) {
            return RiskDecision.rejected("notional exceeds maxNotionalPerOrder");
        }
        if (tradingState == TradingState.REDUCING && cache != null) {
            int position = cache.position(order.symbol());
            boolean increasesExposure = position > 0 && order.side() == SignalDirection.BUY
                    || position < 0 && order.side() == SignalDirection.SELL;
            if (increasesExposure) return RiskDecision.rejected("trading is reducing exposure");
        }
        if (portfolio != null && !portfolio.canReserve(order)) {
            return RiskDecision.rejected("insufficient available margin");
        }
        return RiskDecision.allow();
    }

    public void setTradingState(TradingState tradingState) {
        if (tradingState == null) throw new IllegalArgumentException("tradingState is required");
        this.tradingState = tradingState;
    }

    public TradingState tradingState() {
        return tradingState;
    }

    public void setMaxNotionalPerOrder(String symbol, double maxNotional) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (!Double.isFinite(maxNotional) || maxNotional <= 0.0) {
            throw new IllegalArgumentException("maxNotional must be finite and positive");
        }
        maxNotionalPerOrder.put(symbol, maxNotional);
    }
}