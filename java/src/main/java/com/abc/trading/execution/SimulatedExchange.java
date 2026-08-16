package com.abc.trading.execution;

import com.abc.trading.data.Bar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Minimal simulated venue using the latest bar close as the market price. */
public final class SimulatedExchange {
    private final VenueId venue;
    private final Map<String, Double> lastPrices = new LinkedHashMap<>();
    private final List<LimitOrderIntent> pendingLimitOrders = new ArrayList<>();
    private final OrderMatchingEngine matchingEngine = new OrderMatchingEngine();
    private final Consumer<OrderFill> fillHandler;

    public SimulatedExchange(VenueId venue) {
        this(venue, fill -> { });
    }

    public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler) {
        this.venue = venue;
        this.fillHandler = fillHandler;
    }

    public VenueId venue() {
        return venue;
    }

    public void processBar(Bar bar) {
        lastPrices.put(bar.symbol(), bar.close());
        List<LimitOrderIntent> filledOrders = new ArrayList<>();
        for (LimitOrderIntent order : pendingLimitOrders) {
            if (!order.symbol().equals(bar.symbol())) continue;
            OrderFill fill = matchingEngine.matchLimitOrder(order, bar.close());
            if (fill != null) {
                fillHandler.accept(fill);
                filledOrders.add(order);
            }
        }
        pendingLimitOrders.removeAll(filledOrders);
    }

    public void submitLimitOrder(LimitOrderIntent order) {
        if (order.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(order.limitPrice()) || order.limitPrice() <= 0.0) {
            throw new IllegalArgumentException("limitPrice must be finite and positive");
        }
        pendingLimitOrders.add(order);
    }

    public int pendingLimitOrderCount() {
        return pendingLimitOrders.size();
    }

    public double currentPrice(String symbol) {
        Double price = lastPrices.get(symbol);
        if (price == null) {
            throw new IllegalStateException("No current market price for " + symbol + " on " + venue.value());
        }
        return price;
    }
}