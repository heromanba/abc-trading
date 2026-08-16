package com.abc.trading.execution;

import com.abc.trading.data.Bar;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/** Minimal simulated venue using the latest bar close as the market price. */
public final class SimulatedExchange {
    private final VenueId venue;
    private final Map<String, Double> lastPrices = new LinkedHashMap<>();
    private final List<LimitOrderIntent> pendingLimitOrders = new ArrayList<>();
    private final OrderMatchingEngine matchingEngine = new OrderMatchingEngine();
    private final Consumer<OrderFill> fillHandler;
    private final LatencyModel latencyModel;
    private final FeeModel feeModel;
    private final PriorityQueue<ScheduledCommand> inflightCommands = new PriorityQueue<>();
    private long commandSequence;
    private long currentTimestamp;

    public SimulatedExchange(VenueId venue) {
        this(venue, fill -> { }, StaticLatencyModel.zero(), MakerTakerFeeModel.zero());
    }

    public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler) {
        this(venue, fillHandler, StaticLatencyModel.zero(), MakerTakerFeeModel.zero());
    }

    public SimulatedExchange(
            VenueId venue,
            Consumer<OrderFill> fillHandler,
            LatencyModel latencyModel,
            FeeModel feeModel) {
        this.venue = venue;
        this.fillHandler = fillHandler;
        this.latencyModel = latencyModel;
        this.feeModel = feeModel;
    }

    public VenueId venue() {
        return venue;
    }

    public void processBar(Bar bar) {
        currentTimestamp = bar.tsInit();
        lastPrices.put(bar.symbol(), bar.close());
        drainDueCommands();
        tryMatchLimit(bar.symbol());
    }

    public void submitLimitOrder(LimitOrderIntent order) {
        if (order.quantity() <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (!Double.isFinite(order.limitPrice()) || order.limitPrice() <= 0.0) {
            throw new IllegalArgumentException("limitPrice must be finite and positive");
        }
        schedule(order, latencyModel.getInsertLatencyNs());
    }

    public void submitMarketOrder(OrderIntent order) {
        schedule(order, latencyModel.getInsertLatencyNs());
    }

    public int pendingLimitOrderCount() {
        return pendingLimitOrders.size() + inflightCommands.size();
    }

    public long currentTimestamp() {
        return currentTimestamp;
    }

    public LatencyModel latencyModel() {
        return latencyModel;
    }

    public FeeModel feeModel() {
        return feeModel;
    }

    public double currentPrice(String symbol) {
        Double price = lastPrices.get(symbol);
        if (price == null) {
            throw new IllegalStateException("No current market price for " + symbol + " on " + venue.value());
        }
        return price;
    }

    private void schedule(Object order, long latencyNs) {
        long submittedAt = order instanceof OrderIntent market
                ? market.marketTimestamp()
                : ((LimitOrderIntent) order).marketTimestamp();
        long deliveryTimestamp = Math.addExact(submittedAt, latencyNs);
        if (deliveryTimestamp <= currentTimestamp) {
            processCommand(order);
        } else {
            inflightCommands.add(new ScheduledCommand(deliveryTimestamp, commandSequence++, order));
        }
    }

    private void drainDueCommands() {
        while (!inflightCommands.isEmpty() && inflightCommands.peek().deliveryTimestamp <= currentTimestamp) {
            processCommand(inflightCommands.remove().order);
        }
    }

    private void processCommand(Object order) {
        if (order instanceof OrderIntent market) {
            double fillPrice = currentPrice(market.symbol());
            OrderFill fill = matchingEngine.matchMarketOrder(market, fillPrice)
                    .withCommission(feeModel.calculate(market.quantity(), fillPrice, LiquiditySide.TAKER));
            fillHandler.accept(fill);
            return;
        }
        LimitOrderIntent limit = (LimitOrderIntent) order;
        pendingLimitOrders.add(limit);
        tryMatchLimit(limit.symbol());
    }

    private void tryMatchLimit(String symbol) {
        double marketPrice = currentPrice(symbol);
        List<LimitOrderIntent> filledOrders = new ArrayList<>();
        for (LimitOrderIntent order : pendingLimitOrders) {
            if (!order.symbol().equals(symbol)) continue;
            OrderFill fill = matchingEngine.matchLimitOrder(order, marketPrice);
            if (fill != null) {
                fillHandler.accept(fill.withCommission(
                    feeModel.calculate(order.quantity(), order.limitPrice(), LiquiditySide.MAKER)));
                filledOrders.add(order);
            }
        }
        pendingLimitOrders.removeAll(filledOrders);
    }

    private record ScheduledCommand(long deliveryTimestamp, long sequence, Object order)
            implements Comparable<ScheduledCommand> {
        @Override
        public int compareTo(ScheduledCommand other) {
            int timestampOrder = Long.compare(deliveryTimestamp, other.deliveryTimestamp);
            return timestampOrder != 0 ? timestampOrder : Long.compare(sequence, other.sequence);
        }
    }
}