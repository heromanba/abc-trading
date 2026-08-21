package com.abc.trading.execution;

import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/** Deterministic simulated venue with working-order lifecycle and matching. */
public final class SimulatedExchange {
    private final VenueId venue;
    private final Map<String, Double> lastPrices = new LinkedHashMap<>();
    private final Map<String, MarketDataSnapshot> marketData = new LinkedHashMap<>();
    private final Map<String, WorkingOrder> workingOrders = new LinkedHashMap<>();
    private final Consumer<OrderFill> fillHandler;
    private final Consumer<Object> lifecycleHandler;
    private final LatencyModel latencyModel;
    private final FeeModel feeModel;
    private final BiFunction<String, Double, Double> tickSizeProvider;
    private final PriorityQueue<ScheduledCommand> inflightCommands = new PriorityQueue<>();
    private long commandSequence;
    private long currentTimestamp;
    private int maxFillQuantity = Integer.MAX_VALUE;

    public SimulatedExchange(VenueId venue) {
        this(venue, fill -> { }, event -> { }, StaticLatencyModel.zero(), MakerTakerFeeModel.zero());
    }

    public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler) {
        this(venue, fillHandler, event -> { }, StaticLatencyModel.zero(), MakerTakerFeeModel.zero());
    }

    public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler,
            LatencyModel latencyModel, FeeModel feeModel) {
        this(venue, fillHandler, event -> { }, latencyModel, feeModel);
    }

    public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler,
            Consumer<Object> lifecycleHandler, LatencyModel latencyModel, FeeModel feeModel) {
        this(venue, fillHandler, lifecycleHandler, latencyModel, feeModel, (symbol, price) -> 0.01);
        }

        public SimulatedExchange(VenueId venue, Consumer<OrderFill> fillHandler,
            Consumer<Object> lifecycleHandler, LatencyModel latencyModel, FeeModel feeModel,
            BiFunction<String, Double, Double> tickSizeProvider) {
        this.venue = venue;
        this.fillHandler = fillHandler;
        this.lifecycleHandler = lifecycleHandler;
        this.latencyModel = latencyModel;
        this.feeModel = feeModel;
        this.tickSizeProvider = tickSizeProvider;
    }

    public VenueId venue() { return venue; }

    public void processBar(Bar bar) {
        processMarketData(MarketDataSnapshot.fromBar(bar));
    }

    public void processMarketData(MarketDataSnapshot snapshot) {
        currentTimestamp = snapshot.tsInit();
        lastPrices.put(snapshot.symbol(), snapshot.last());
        marketData.put(snapshot.symbol(), snapshot);
        expireDueOrders();
        drainDueCommands();
        processTriggers(snapshot.symbol());
        tryMatchMarket(snapshot.symbol());
        tryMatchLimit(snapshot.symbol());
    }

    public void submitLimitOrder(LimitOrderIntent order) {
        validateQuantity(order.quantity());
        if (order.trailingOffsetType() == null) validatePrice(order.limitPrice());
        else if (!Double.isFinite(order.limitPrice()) || order.limitPrice() < 0.0) {
            throw new IllegalArgumentException("limitPrice must be finite and non-negative");
        }
        schedule(order, latencyModel.getInsertLatencyNs());
    }

    public void submitMarketOrder(OrderIntent order) {
        validateQuantity(order.quantity());
        if (order.trailingOffsetType() == null) validatePrice(order.price());
        else if (!Double.isFinite(order.price()) || order.price() < 0.0) {
            throw new IllegalArgumentException("price must be finite and non-negative");
        }
        schedule(order, latencyModel.getInsertLatencyNs());
    }

    public boolean cancelOrder(CancelOrder command) {
        WorkingOrder order = workingOrders.remove(command.clientOrderId());
        if (order != null) return true;
        return inflightCommands.removeIf(item -> item.orderId().equals(command.clientOrderId()));
    }

    public boolean modifyOrder(ModifyOrder command) {
        WorkingOrder order = workingOrders.get(command.clientOrderId());
        if (order == null || (!order.limit && !order.trailing)) return false;
        int nextQuantity = command.quantity() == null ? order.quantity : command.quantity();
        if (nextQuantity < order.filledQuantity || nextQuantity <= 0) return false;
        double nextPrice = command.price() == null ? order.price : command.price();
        if (order.limit) validatePrice(nextPrice);
        if (command.triggerPrice() != null) {
            validatePrice(command.triggerPrice());
            order.triggerPrice = command.triggerPrice();
        }
        order.quantity = nextQuantity;
        order.price = nextPrice;
        return true;
    }

    public void setMaxFillQuantity(int maxFillQuantity) {
        if (maxFillQuantity <= 0) throw new IllegalArgumentException("maxFillQuantity must be positive");
        this.maxFillQuantity = maxFillQuantity;
    }

    public int pendingLimitOrderCount() {
        int count = 0;
        for (WorkingOrder order : workingOrders.values()) if (order.limit) count++;
        for (ScheduledCommand command : inflightCommands) {
            if (command.order instanceof LimitOrderIntent) count++;
        }
        return count;
    }

    public long currentTimestamp() { return currentTimestamp; }

    public LatencyModel latencyModel() { return latencyModel; }

    public FeeModel feeModel() { return feeModel; }

    public double currentPrice(String symbol) {
        Double price = lastPrices.get(symbol);
        if (price == null) throw new IllegalStateException("No current market price for " + symbol + " on " + venue.value());
        return price;
    }

    private void schedule(Object order, long latencyNs) {
        long submittedAt = order instanceof OrderIntent market
                ? market.marketTimestamp() : ((LimitOrderIntent) order).marketTimestamp();
        long deliveryTimestamp = Math.addExact(submittedAt, latencyNs);
        if (deliveryTimestamp <= currentTimestamp) processCommand(order);
        else inflightCommands.add(new ScheduledCommand(deliveryTimestamp, commandSequence++, order));
    }

    private void drainDueCommands() {
        while (!inflightCommands.isEmpty() && inflightCommands.peek().deliveryTimestamp <= currentTimestamp) {
            processCommand(inflightCommands.remove().order);
        }
    }

    private void processCommand(Object order) {
        WorkingOrder workingOrder = WorkingOrder.from(order);
        if (isExpired(workingOrder, currentTimestamp)) {
            expireWorkingOrder(workingOrder, currentTimestamp);
            return;
        }
        if (workingOrder.stop || workingOrder.trailing) validateTriggerType(workingOrder.triggerType);
        if (workingOrder.trailing) validateTrailingOffsetType(workingOrder.trailingOffsetType);
        if (workingOrder.stop && workingOrder.timeInForce == TimeInForce.FOK
                && maxFillQuantity < workingOrder.quantity) {
            emitCanceled(workingOrder, currentTimestamp);
            return;
        }
        workingOrders.put(workingOrder.orderId, workingOrder);
        if (workingOrder.stop || workingOrder.trailing) {
            if (shouldTrigger(workingOrder)) trigger(workingOrder);
            return;
        }
        boolean crossed = !workingOrder.limit || (workingOrder.side == SignalDirection.BUY
            ? currentPrice(workingOrder.symbol) <= workingOrder.price
            : currentPrice(workingOrder.symbol) >= workingOrder.price);
        if (workingOrder.timeInForce == TimeInForce.FOK
            && (maxFillQuantity < workingOrder.quantity || !crossed)) {
            emitCanceled(workingOrder, currentTimestamp);
            return;
        }
        if (workingOrder.limit) tryMatchLimit(workingOrder.symbol);
        else fill(workingOrder, currentPrice(workingOrder.symbol), LiquiditySide.TAKER);
        if (workingOrder.timeInForce == TimeInForce.IOC && workingOrders.containsKey(workingOrder.orderId)) {
            cancelWorkingOrder(workingOrder, currentTimestamp);
        }
    }

    private void tryMatchLimit(String symbol) {
        List<WorkingOrder> candidates = new ArrayList<>();
        for (WorkingOrder order : workingOrders.values()) {
            if (order.limit && (!order.stop || order.triggered) && order.symbol.equals(symbol)) candidates.add(order);
        }
        for (WorkingOrder order : candidates) {
            if (!workingOrders.containsKey(order.orderId)) continue;
            boolean crossed = order.side == SignalDirection.BUY
                    ? currentPrice(symbol) <= order.price : currentPrice(symbol) >= order.price;
            if (crossed) fill(order, order.price, LiquiditySide.MAKER);
            if (order.timeInForce == TimeInForce.IOC && workingOrders.containsKey(order.orderId)) {
                cancelWorkingOrder(order, currentTimestamp);
            }
        }
    }

    private void tryMatchMarket(String symbol) {
        List<WorkingOrder> candidates = new ArrayList<>();
        for (WorkingOrder order : workingOrders.values()) {
            if (!order.limit && (!order.stop || order.triggered) && order.symbol.equals(symbol)) {
                candidates.add(order);
            }
        }
        for (WorkingOrder order : candidates) {
            if (!workingOrders.containsKey(order.orderId)) continue;
            fill(order, currentPrice(symbol), LiquiditySide.TAKER);
            if (order.timeInForce == TimeInForce.IOC && workingOrders.containsKey(order.orderId)) {
                cancelWorkingOrder(order, currentTimestamp);
            }
        }
    }

    private void processTriggers(String symbol) {
        List<WorkingOrder> candidates = new ArrayList<>();
        for (WorkingOrder order : workingOrders.values()) {
            if ((order.stop || order.trailing) && !order.triggered && order.symbol.equals(symbol)) candidates.add(order);
        }
        for (WorkingOrder order : candidates) {
            if (workingOrders.containsKey(order.orderId) && shouldTrigger(order)) trigger(order);
        }
    }

    private void trigger(WorkingOrder order) {
        order.triggered = true;
        if (order.limit) {
                lifecycleHandler.accept(new OrderTriggered(order.orderId, order.strategyId, order.symbol,
                    order.inputSequence, currentTimestamp, order.triggerPrice));
            tryMatchLimit(order.symbol);
        } else {
            fill(order, currentPrice(order.symbol), LiquiditySide.TAKER);
        }
        if (order.timeInForce == TimeInForce.IOC && workingOrders.containsKey(order.orderId)) {
            cancelWorkingOrder(order, currentTimestamp);
        }
    }

    private boolean isStopMatched(WorkingOrder order) {
        MarketDataSnapshot snapshot = marketData.get(order.symbol);
        if (snapshot == null) throw new IllegalStateException("No market data for " + order.symbol);
        if (order.trailing && !order.activated) {
            double activationMarket = order.side == SignalDirection.BUY ? snapshot.ask() : snapshot.bid();
            boolean activate = order.activationPrice <= 0.0 || (order.side == SignalDirection.BUY
                    ? activationMarket <= order.activationPrice : activationMarket >= order.activationPrice);
            if (!activate) return false;
            order.activated = true;
        }
        if (order.trailing) {
            double trailMarket = trailingMarketPrice(order, snapshot);
            double nextTrigger = calculateTrailingTrigger(order, trailMarket);
            if (order.triggerPrice <= 0.0
                    || (order.side == SignalDirection.BUY ? nextTrigger < order.triggerPrice : nextTrigger > order.triggerPrice)) {
                order.triggerPrice = nextTrigger;
            }
            if (order.limit && order.limitOffset != 0.0) {
                double nextLimit = calculateTrailingLimit(order, trailMarket);
                if (order.price <= 0.0
                        || (order.side == SignalDirection.BUY ? nextLimit < order.price : nextLimit > order.price)) {
                    order.price = nextLimit;
                }
            }
        }
        double marketPrice = switch (order.triggerType) {
            case LAST_PRICE, DOUBLE_LAST -> snapshot.last();
            case MARK_PRICE -> snapshot.mark();
            case INDEX_PRICE -> snapshot.index();
            case BID_ASK, DOUBLE_BID_ASK, DEFAULT -> order.side == SignalDirection.BUY
                    ? snapshot.ask() : snapshot.bid();
            case LAST_OR_BID_ASK -> snapshot.last();
            case MID_POINT -> (snapshot.bid() + snapshot.ask()) / 2.0;
            case NO_TRIGGER -> throw new IllegalArgumentException("Stop order trigger type is required");
        };
        return order.side == SignalDirection.BUY
                ? marketPrice >= order.triggerPrice
                : marketPrice <= order.triggerPrice;
    }

    private boolean shouldTrigger(WorkingOrder order) {
        boolean matched = isStopMatched(order);
        boolean result = switch (order.triggerType) {
            case DOUBLE_LAST, DOUBLE_BID_ASK -> order.previousTriggerMatch && matched;
            default -> matched;
        };
        order.previousTriggerMatch = matched;
        return result;
    }

    private void fill(WorkingOrder order, double price, LiquiditySide liquiditySide) {
        int remaining = order.quantity - order.filledQuantity;
        int fillQuantity = Math.min(remaining, maxFillQuantity);
        if (fillQuantity <= 0) return;
        OrderFill fill = new OrderFill(order.strategyId, order.symbol, order.inputSequence,
                currentTimestamp, order.correlationId, order.orderId, order.side, fillQuantity,
                price, order.currentPosition, order.realizedPnl)
                .withCommission(feeModel.calculate(fillQuantity, price, liquiditySide));
        order.filledQuantity += fillQuantity;
        fillHandler.accept(fill);
        if (order.filledQuantity == order.quantity) workingOrders.remove(order.orderId);
    }

    private void expireDueOrders() {
        List<WorkingOrder> expired = new ArrayList<>();
        LocalDate currentDate = date(currentTimestamp);
        for (WorkingOrder order : workingOrders.values()) {
            if (isExpired(order, currentTimestamp, currentDate)) expired.add(order);
        }
        for (WorkingOrder order : expired) expireWorkingOrder(order, currentTimestamp);
    }

    private void cancelWorkingOrder(WorkingOrder order, long timestamp) {
        if (workingOrders.remove(order.orderId) != null) emitCanceled(order, timestamp);
    }

    private void expireWorkingOrder(WorkingOrder order, long timestamp) {
        if (workingOrders.remove(order.orderId) != null) {
            lifecycleHandler.accept(new OrderExpired(order.strategyId, order.symbol, order.orderId,
                    order.side, order.quantity - order.filledQuantity, order.price, timestamp));
        }
    }

    private void emitCanceled(WorkingOrder order, long timestamp) {
        lifecycleHandler.accept(new OrderCanceled(new CancelOrder(
                order.strategyId, order.symbol, order.orderId,
                "venue-cancel-" + order.orderId + "-" + timestamp, timestamp)));
    }

    private static boolean isExpired(WorkingOrder order, long timestamp) {
        return isExpired(order, timestamp, date(timestamp));
    }

    private static boolean isExpired(WorkingOrder order, long timestamp, LocalDate currentDate) {
        boolean gtdExpired = order.timeInForce == TimeInForce.GTD && timestamp >= order.expireTimeNs;
        boolean dayExpired = order.timeInForce == TimeInForce.DAY
                && currentDate.isAfter(date(order.submittedAt));
        return gtdExpired || dayExpired;
    }

    private static LocalDate date(long timestampNs) {
        return Instant.ofEpochSecond(timestampNs / 1_000_000_000L,
                timestampNs % 1_000_000_000L).atZone(ZoneOffset.UTC).toLocalDate();
    }

    private static void validateQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }

    private static void validatePrice(double price) {
        if (!Double.isFinite(price) || price <= 0.0) throw new IllegalArgumentException("price must be finite and positive");
    }

    private static void validateTriggerType(TriggerType triggerType) {
        if (triggerType == null || triggerType == TriggerType.NO_TRIGGER) {
            throw new IllegalArgumentException("Stop order trigger type is required");
        }
    }

    private static void validateTrailingOffsetType(TrailingOffsetType offsetType) {
        if (offsetType == null) {
            throw new IllegalArgumentException("unsupported trailing offset type");
        }
    }

    private static double trailingMarketPrice(WorkingOrder order, MarketDataSnapshot snapshot) {
        return switch (order.triggerType) {
            case LAST_PRICE, DOUBLE_LAST -> snapshot.last();
            case MARK_PRICE -> snapshot.mark();
            case INDEX_PRICE -> snapshot.index();
            case BID_ASK, DOUBLE_BID_ASK, DEFAULT -> order.side == SignalDirection.BUY
                    ? snapshot.ask() : snapshot.bid();
            case LAST_OR_BID_ASK -> snapshot.last();
            case MID_POINT -> (snapshot.bid() + snapshot.ask()) / 2.0;
            case NO_TRIGGER -> throw new IllegalArgumentException("trailing trigger type is required");
        };
    }

    private double calculateTrailingTrigger(WorkingOrder order, double marketPrice) {
        double offset = switch (order.trailingOffsetType) {
            case PRICE -> order.trailingOffset;
            case BASIS_POINTS -> marketPrice * order.trailingOffset / 10_000.0;
            case TICKS, PRICE_TIER -> order.trailingOffset * tickSizeProvider.apply(order.symbol, marketPrice);
        };
        return order.side == SignalDirection.BUY ? marketPrice + offset : marketPrice - offset;
    }

    private double calculateTrailingLimit(WorkingOrder order, double marketPrice) {
        double offset = switch (order.trailingOffsetType) {
            case PRICE -> order.limitOffset;
            case BASIS_POINTS -> marketPrice * order.limitOffset / 10_000.0;
            case TICKS, PRICE_TIER -> order.limitOffset * tickSizeProvider.apply(order.symbol, marketPrice);
        };
        return order.side == SignalDirection.BUY ? marketPrice + offset : marketPrice - offset;
    }

    private record ScheduledCommand(long deliveryTimestamp, long sequence, Object order)
            implements Comparable<ScheduledCommand> {
        private String orderId() {
            return order instanceof OrderIntent market ? market.orderId() : ((LimitOrderIntent) order).orderId();
        }

        @Override
        public int compareTo(ScheduledCommand other) {
            int timestampOrder = Long.compare(deliveryTimestamp, other.deliveryTimestamp);
            return timestampOrder != 0 ? timestampOrder : Long.compare(sequence, other.sequence);
        }
    }

    private static final class WorkingOrder {
        private final String strategyId;
        private final String symbol;
        private final long inputSequence;
        private final String correlationId;
        private final String orderId;
        private final SignalDirection side;
        private final int currentPosition;
        private final double realizedPnl;
        private final TimeInForce timeInForce;
        private final long expireTimeNs;
        private final long submittedAt;
        private final boolean limit;
        private final boolean stop;
        private final boolean trailing;
        private double triggerPrice;
        private final TriggerType triggerType;
        private final double activationPrice;
        private final double trailingOffset;
        private final TrailingOffsetType trailingOffsetType;
        private final double limitOffset;
        private int quantity;
        private int filledQuantity;
        private double price;
        private boolean triggered;
        private boolean activated;
        private boolean previousTriggerMatch;

        private WorkingOrder(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                String correlationId, String orderId, SignalDirection side, int quantity, double price,
                int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                boolean limit, boolean stop, double triggerPrice, TriggerType triggerType,
                double activationPrice, double trailingOffset, TrailingOffsetType trailingOffsetType,
                double limitOffset) {
            this.strategyId = strategyId;
            this.symbol = symbol;
            this.inputSequence = inputSequence;
            this.correlationId = correlationId;
            this.orderId = orderId;
            this.side = side;
            this.quantity = quantity;
            this.price = price;
            this.currentPosition = currentPosition;
            this.realizedPnl = realizedPnl;
            this.timeInForce = timeInForce;
            this.expireTimeNs = expireTimeNs;
            this.submittedAt = marketTimestamp;
            this.limit = limit;
            this.stop = stop;
            this.trailing = trailingOffsetType != null;
            this.triggerPrice = triggerPrice;
            this.triggerType = triggerType;
            this.activationPrice = activationPrice;
            this.trailingOffset = trailingOffset;
            this.trailingOffsetType = trailingOffsetType;
            this.limitOffset = limitOffset;
        }

        private static WorkingOrder from(Object order) {
            if (order instanceof OrderIntent market) {
                return new WorkingOrder(market.strategyId(), market.symbol(), market.inputSequence(),
                        market.marketTimestamp(), market.correlationId(), market.orderId(), market.side(),
                        market.quantity(), market.price(), market.currentPosition(), market.realizedPnl(),
                        market.timeInForce(), market.expireTimeNs(), false,
                        market.triggerPrice() > 0.0 || market.trailingOffsetType() != null,
                        market.triggerPrice(), market.triggerType(), market.activationPrice(),
                        market.trailingOffset(), market.trailingOffsetType(), 0.0);
            }
            LimitOrderIntent limit = (LimitOrderIntent) order;
            return new WorkingOrder(limit.strategyId(), limit.symbol(), limit.inputSequence(),
                    limit.marketTimestamp(), limit.correlationId(), limit.orderId(), limit.side(),
                    limit.quantity(), limit.limitPrice(), limit.currentPosition(), limit.realizedPnl(),
                    limit.timeInForce(), limit.expireTimeNs(), true,
                    limit.triggerPrice() > 0.0 || limit.trailingOffsetType() != null,
                    limit.triggerPrice(), limit.triggerType(), limit.activationPrice(), limit.trailingOffset(),
                    limit.trailingOffsetType(), limit.limitOffset());
        }
    }
}