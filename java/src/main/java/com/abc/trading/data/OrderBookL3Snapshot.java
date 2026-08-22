package com.abc.trading.data;

import com.abc.trading.execution.SignalDirection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable individual-order book snapshot, analogous to Nautilus L3_MBO data. */
public record OrderBookL3Snapshot(
        String symbol,
        long tsInit,
        List<VenueOrder> bids,
        List<VenueOrder> asks,
        long sequence) {
    public OrderBookL3Snapshot {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        bids = sort(bids, SignalDirection.BUY);
        asks = sort(asks, SignalDirection.SELL);
        validateIds(bids, asks);
    }

    private static List<VenueOrder> sort(List<VenueOrder> orders, SignalDirection side) {
        if (orders == null) throw new IllegalArgumentException("orders are required");
        List<VenueOrder> copy = new ArrayList<>(orders);
        Comparator<VenueOrder> comparator = side == SignalDirection.BUY
                ? Comparator.comparingDouble(VenueOrder::price).reversed()
                : Comparator.comparingDouble(VenueOrder::price);
        copy.sort(comparator.thenComparingLong(VenueOrder::sequence)
                .thenComparing(VenueOrder::orderId));
        return List.copyOf(copy);
    }

    private static void validateIds(List<VenueOrder> bids, List<VenueOrder> asks) {
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (VenueOrder order : bids) if (!ids.add(order.orderId())) throw new IllegalArgumentException("duplicate venue order: " + order.orderId());
        for (VenueOrder order : asks) if (!ids.add(order.orderId())) throw new IllegalArgumentException("duplicate venue order: " + order.orderId());
    }
}