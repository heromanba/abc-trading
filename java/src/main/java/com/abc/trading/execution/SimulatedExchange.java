package com.abc.trading.execution;

import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.BookLevel;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.OrderBookDelta;
import com.abc.trading.data.BookAction;
import com.abc.trading.data.OrderBookL3Snapshot;
import com.abc.trading.data.OrderBookL3Delta;
import com.abc.trading.data.VenueOrder;
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
    private final Map<String, BookState> books = new LinkedHashMap<>();
    private final Map<String, L3BookState> l3Books = new LinkedHashMap<>();
    private final Map<String, WorkingOrder> workingOrders = new LinkedHashMap<>();
    private final Consumer<OrderFill> fillHandler;
    private final Consumer<Object> lifecycleHandler;
    private final LatencyModel latencyModel;
    private final FeeModel feeModel;
    private final BiFunction<String, Double, Double> tickSizeProvider;
    private final PriorityQueue<ScheduledCommand> inflightCommands = new PriorityQueue<>();
    private long commandSequence;
    private long workingOrderSequence;
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
        marketData.put(snapshot.symbol(), snapshot);
        processOrderBook(OrderBookSnapshot.fromMarketData(snapshot));
    }

    public void processOrderBook(OrderBookSnapshot snapshot) {
        currentTimestamp = snapshot.tsInit();
        double bid = snapshot.bids().isEmpty() ? snapshot.asks().get(0).price() : snapshot.bids().get(0).price();
        double ask = snapshot.asks().isEmpty() ? snapshot.bids().get(0).price() : snapshot.asks().get(0).price();
        double midpoint = (bid + ask) / 2.0;
        marketData.put(snapshot.symbol(), new MarketDataSnapshot(
                snapshot.symbol(), snapshot.tsInit(), bid, ask, midpoint, midpoint, midpoint, snapshot.sequence()));
        lastPrices.put(snapshot.symbol(), midpoint);
        books.put(snapshot.symbol(), BookState.from(snapshot));
        expireDueOrders();
        drainDueCommands();
        processTriggers(snapshot.symbol());
        tryMatchOrders(snapshot.symbol());
    }

    public void processOrderBookDelta(OrderBookDelta delta) {
        BookState book = books.get(delta.symbol());
        if (book == null) throw new IllegalStateException("No order-book snapshot for " + delta.symbol());
        book.apply(delta);
        currentTimestamp = delta.tsInit();
        double bid = book.bestBid();
        double ask = book.bestAsk();
        double midpoint = (bid + ask) / 2.0;
        marketData.put(delta.symbol(), new MarketDataSnapshot(
                delta.symbol(), delta.tsInit(), bid, ask, midpoint, midpoint, midpoint, delta.sequence()));
        lastPrices.put(delta.symbol(), midpoint);
        expireDueOrders();
        drainDueCommands();
        processTriggers(delta.symbol());
        tryMatchOrders(delta.symbol());
    }

    public void processOrderBookL3(OrderBookL3Snapshot snapshot) {
        currentTimestamp = snapshot.tsInit();
        L3BookState book = L3BookState.from(snapshot);
        l3Books.put(snapshot.symbol(), book);
        for (WorkingOrder order : workingOrders.values()) {
            if (order.symbol.equals(snapshot.symbol()) && order.limit && order.resting) {
                order.queueInitialized = true;
                order.queueAhead = 0;
                order.queueAheadOrders.clear();
                book.registerQueueOrder(order);
            }
        }
        updateMarketDataFromL3(snapshot.symbol(), snapshot.tsInit(), snapshot.sequence());
        expireDueOrders();
        drainDueCommands();
        processTriggers(snapshot.symbol());
        tryMatchOrders(snapshot.symbol());
    }

    public void processOrderBookL3Delta(OrderBookL3Delta delta) {
        L3BookState book = l3Books.get(delta.symbol());
        if (book == null) throw new IllegalStateException("No L3 order-book snapshot for " + delta.symbol());
        book.adjustQueues(delta);
        book.apply(delta);
        currentTimestamp = delta.tsInit();
        updateMarketDataFromL3(delta.symbol(), delta.tsInit(), delta.sequence());
        expireDueOrders();
        drainDueCommands();
        processTriggers(delta.symbol());
        tryMatchOrders(delta.symbol());
    }

    private void updateMarketDataFromL3(String symbol, long timestamp, long sequence) {
        L3BookState book = l3Books.get(symbol);
        double bid = book.bestBid();
        double ask = book.bestAsk();
        if (bid <= 0.0) bid = ask;
        if (!Double.isFinite(ask)) ask = bid;
        double midpoint = (bid + ask) / 2.0;
        marketData.put(symbol, new MarketDataSnapshot(symbol, timestamp, bid, ask,
                midpoint, midpoint, midpoint, sequence));
        lastPrices.put(symbol, midpoint);
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
        if (order != null) {
            unregisterQueueOrder(order);
            return true;
        }
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
        if (order.resting) {
            order.queueInitialized = false;
            order.queueAhead = 0;
            order.queueAheadOrders.clear();
        }
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
        workingOrder.insertionSequence = workingOrderSequence++;
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
        tryMatchOrder(workingOrder);
        if (workingOrder.timeInForce == TimeInForce.IOC && workingOrders.containsKey(workingOrder.orderId)) {
            cancelWorkingOrder(workingOrder, currentTimestamp);
        }
    }

    private void tryMatchLimit(String symbol) {
        tryMatchOrders(symbol);
    }

    private void tryMatchOrders(String symbol) {
        List<WorkingOrder> candidates = new ArrayList<>();
        for (WorkingOrder order : workingOrders.values()) {
            if ((!order.stop || order.triggered) && order.symbol.equals(symbol)) {
                candidates.add(order);
            }
        }
        candidates.sort((left, right) -> {
            if (left.limit && right.limit && left.price != right.price) {
                int priceOrder = left.side == SignalDirection.BUY
                        ? Double.compare(right.price, left.price)
                        : Double.compare(left.price, right.price);
                if (priceOrder != 0) return priceOrder;
            }
            return Long.compare(left.insertionSequence, right.insertionSequence);
        });
        for (WorkingOrder order : candidates) {
            if (!workingOrders.containsKey(order.orderId)) continue;
            tryMatchOrder(order);
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
            tryMatchOrder(order);
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

    private void fill(WorkingOrder order, double price, int fillQuantity, LiquiditySide liquiditySide) {
        fill(order, price, fillQuantity, liquiditySide, "");
        }

        private void fill(WorkingOrder order, double price, int fillQuantity,
            LiquiditySide liquiditySide, String venueOrderId) {
        if (fillQuantity <= 0) return;
        OrderFill fill = new OrderFill(order.strategyId, order.symbol, order.inputSequence,
                currentTimestamp, order.correlationId, order.orderId, order.side, fillQuantity,
                price, order.currentPosition, order.realizedPnl)
                .withLiquiditySide(liquiditySide)
                .withVenueOrderId(venueOrderId)
                .withCommission(feeModel.calculate(fillQuantity, price, liquiditySide));
        order.filledQuantity += fillQuantity;
        fillHandler.accept(fill);
        if (order.filledQuantity == order.quantity) {
            workingOrders.remove(order.orderId);
            unregisterQueueOrder(order);
        }
    }

    private void tryMatchOrder(WorkingOrder order) {
        if (order.stop && !order.triggered) return;
        L3BookState l3Book = l3Books.get(order.symbol);
        if (l3Book != null) {
            tryMatchL3Order(order, l3Book);
            return;
        }
        BookState book = books.get(order.symbol);
        if (book == null) return;
        boolean crossed;
        if (!order.limit) {
            crossed = true;
        } else if (order.side == SignalDirection.BUY) {
            crossed = book.bestAsk() <= order.price;
        } else {
            crossed = book.bestBid() >= order.price;
        }
        if (!crossed && order.limit) {
            order.resting = true;
            return;
        }
        LiquiditySide liquiditySide = order.resting ? LiquiditySide.MAKER : LiquiditySide.TAKER;
        int budget = maxFillQuantity;
        while (budget > 0 && order.quantity > order.filledQuantity) {
            BookLevel level = book.bestLevel(order.side == SignalDirection.BUY);
            if (level == null) break;
            boolean eligible = !order.limit || (order.side == SignalDirection.BUY
                    ? level.price() <= order.price : level.price() >= order.price);
            if (!eligible) break;
            int fillQuantity = Math.min(Math.min(level.quantity(), order.quantity - order.filledQuantity), budget);
            fill(order, level.price(), fillQuantity, liquiditySide);
            book.consume(order.side == SignalDirection.BUY, fillQuantity);
            budget -= fillQuantity;
        }
        if (order.quantity > order.filledQuantity) {
            order.resting = order.limit;
        }
    }

    private void tryMatchL3Order(WorkingOrder order, L3BookState book) {
        if (order.limit) {
            double best = order.side == SignalDirection.BUY ? book.bestAsk() : book.bestBid();
            boolean crossed = order.side == SignalDirection.BUY ? best <= order.price : best >= order.price;
            if (!crossed) {
                order.resting = true;
                if (!order.queueInitialized) book.snapshotQueue(order);
                book.registerQueueOrder(order);
                return;
            }
            if (order.resting && order.queueInitialized && order.queueAhead > 0) return;
        }
        LiquiditySide liquiditySide = order.resting ? LiquiditySide.MAKER : LiquiditySide.TAKER;
        int budget = maxFillQuantity;
        while (budget > 0 && order.quantity > order.filledQuantity) {
            VenueOrder venueOrder = book.bestOrder(order.side == SignalDirection.BUY);
            if (venueOrder == null) break;
            boolean eligible = !order.limit || (order.side == SignalDirection.BUY
                    ? venueOrder.price() <= order.price : venueOrder.price() >= order.price);
            if (!eligible) break;
            int fillQuantity = Math.min(Math.min(venueOrder.quantity(), order.quantity - order.filledQuantity), budget);
            fill(order, venueOrder.price(), fillQuantity, liquiditySide, venueOrder.orderId());
            book.consume(order.side == SignalDirection.BUY, fillQuantity);
            budget -= fillQuantity;
        }
        if (order.quantity > order.filledQuantity) {
            order.resting = order.limit;
            if (order.resting && !order.queueInitialized) book.snapshotQueue(order);
            if (order.resting) book.registerQueueOrder(order);
        }
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
        if (workingOrders.remove(order.orderId) != null) {
            unregisterQueueOrder(order);
            emitCanceled(order, timestamp);
        }
    }

    private void expireWorkingOrder(WorkingOrder order, long timestamp) {
        if (workingOrders.remove(order.orderId) != null) {
            unregisterQueueOrder(order);
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
        if (offsetType == null || offsetType == TrailingOffsetType.PRICE_TIER) {
            throw new IllegalArgumentException("unsupported trailing offset type");
        }
    }

    private void unregisterQueueOrder(WorkingOrder order) {
        L3BookState book = l3Books.get(order.symbol);
        if (book != null) book.unregisterQueueOrder(order);
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
            case TICKS -> order.trailingOffset * tickSizeProvider.apply(order.symbol, marketPrice);
            case PRICE_TIER -> throw new IllegalArgumentException("PRICE_TIER is not supported by Nautilus Rust");
        };
        return order.side == SignalDirection.BUY ? marketPrice + offset : marketPrice - offset;
    }

    private double calculateTrailingLimit(WorkingOrder order, double marketPrice) {
        double offset = switch (order.trailingOffsetType) {
            case PRICE -> order.limitOffset;
            case BASIS_POINTS -> marketPrice * order.limitOffset / 10_000.0;
            case TICKS -> order.limitOffset * tickSizeProvider.apply(order.symbol, marketPrice);
            case PRICE_TIER -> throw new IllegalArgumentException("PRICE_TIER is not supported by Nautilus Rust");
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
        private boolean resting;
        private boolean queueInitialized;
        private int queueAhead;
        private final LinkedHashMap<String, Integer> queueAheadOrders = new LinkedHashMap<>();
        private long insertionSequence;

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

    private static final class L3BookState {
        private final List<MutableVenueOrder> bids;
        private final List<MutableVenueOrder> asks;

        private L3BookState(List<MutableVenueOrder> bids, List<MutableVenueOrder> asks) {
            this.bids = bids;
            this.asks = asks;
        }

        private static L3BookState from(OrderBookL3Snapshot snapshot) {
            List<MutableVenueOrder> bids = new ArrayList<>();
            for (VenueOrder order : snapshot.bids()) bids.add(new MutableVenueOrder(order));
            List<MutableVenueOrder> asks = new ArrayList<>();
            for (VenueOrder order : snapshot.asks()) asks.add(new MutableVenueOrder(order));
            return new L3BookState(bids, asks);
        }

        private void apply(OrderBookL3Delta delta) {
            if (delta.action() == BookAction.CLEAR) {
                bids.clear();
                asks.clear();
                return;
            }
            List<MutableVenueOrder> levels = levels(delta.side());
            MutableVenueOrder existing = find(levels, delta.orderId());
            switch (delta.action()) {
                case ADD -> {
                    if (existing != null) throw new IllegalStateException("venue order already exists: " + delta.orderId());
                    levels.add(new MutableVenueOrder(new VenueOrder(delta.orderId(), delta.side(),
                            delta.price(), delta.quantity(), delta.sequence())));
                }
                case UPDATE -> {
                    if (existing == null) throw new IllegalStateException("unknown venue order: " + delta.orderId());
                    existing.price = delta.price();
                    existing.quantity = delta.quantity();
                    if (existing.quantity == 0) levels.remove(existing);
                }
                case DELETE -> {
                    if (existing != null) levels.remove(existing);
                }
                case CLEAR -> { }
            }
            sort(levels, delta.side());
        }

        private void snapshotQueue(WorkingOrder clientOrder) {
            List<MutableVenueOrder> levels = levels(clientOrder.side);
            clientOrder.queueAhead = 0;
            clientOrder.queueAheadOrders.clear();
            for (MutableVenueOrder venueOrder : levels) {
                if (Double.compare(venueOrder.price, clientOrder.price) != 0) continue;
                clientOrder.queueAhead += venueOrder.quantity;
                clientOrder.queueAheadOrders.put(venueOrder.orderId, venueOrder.quantity);
            }
            clientOrder.queueInitialized = true;
        }

        private void adjustQueues(OrderBookL3Delta delta) {
            if (delta.action() == BookAction.CLEAR) {
                clearQueues();
                return;
            }
            for (WorkingOrder clientOrder : currentQueueOrders) {
                if (!clientOrder.queueInitialized || clientOrder.side != delta.side()
                        || Double.compare(clientOrder.price, delta.price()) != 0
                        && !clientOrder.queueAheadOrders.containsKey(delta.orderId())) continue;
                Integer previous = clientOrder.queueAheadOrders.get(delta.orderId());
                switch (delta.action()) {
                    case DELETE -> {
                        if (previous != null) {
                            clientOrder.queueAhead -= previous;
                            clientOrder.queueAheadOrders.remove(delta.orderId());
                        }
                    }
                    case UPDATE -> {
                        if (previous != null) {
                            if (Double.compare(clientOrder.price, delta.price()) != 0) {
                                clientOrder.queueAhead -= previous;
                                clientOrder.queueAheadOrders.remove(delta.orderId());
                            } else {
                                clientOrder.queueAhead += delta.quantity() - previous;
                                clientOrder.queueAheadOrders.put(delta.orderId(), delta.quantity());
                            }
                        }
                    }
                    case ADD, CLEAR -> { }
                }
                if (clientOrder.queueAhead < 0) clientOrder.queueAhead = 0;
            }
        }

        private final List<WorkingOrder> currentQueueOrders = new ArrayList<>();

        private void registerQueueOrder(WorkingOrder order) {
            if (!currentQueueOrders.contains(order)) currentQueueOrders.add(order);
        }

        private void unregisterQueueOrder(WorkingOrder order) {
            currentQueueOrders.remove(order);
        }

        private void clearQueues() {
            for (WorkingOrder order : currentQueueOrders) {
                order.queueAhead = 0;
                order.queueAheadOrders.clear();
            }
        }

        private List<MutableVenueOrder> levels(SignalDirection side) {
            return side == SignalDirection.BUY ? bids : asks;
        }

        private static MutableVenueOrder find(List<MutableVenueOrder> levels, String orderId) {
            for (MutableVenueOrder order : levels) if (order.orderId.equals(orderId)) return order;
            return null;
        }

        private static void sort(List<MutableVenueOrder> levels, SignalDirection side) {
            levels.sort(side == SignalDirection.BUY
                    ? (left, right) -> compare(left, right, true)
                    : (left, right) -> compare(left, right, false));
        }

        private static int compare(MutableVenueOrder left, MutableVenueOrder right, boolean bids) {
            int priceOrder = bids ? Double.compare(right.price, left.price) : Double.compare(left.price, right.price);
            return priceOrder != 0 ? priceOrder
                    : Long.compare(left.sequence, right.sequence) != 0
                    ? Long.compare(left.sequence, right.sequence) : left.orderId.compareTo(right.orderId);
        }

        private VenueOrder bestOrder(boolean buying) {
            List<MutableVenueOrder> levels = buying ? asks : bids;
            return levels.isEmpty() ? null : levels.get(0).value();
        }

        private double bestBid() { return bids.isEmpty() ? 0.0 : bids.get(0).price; }
        private double bestAsk() { return asks.isEmpty() ? Double.POSITIVE_INFINITY : asks.get(0).price; }

        private void consume(boolean buying, int quantity) {
            List<MutableVenueOrder> levels = buying ? asks : bids;
            MutableVenueOrder order = levels.get(0);
            order.quantity -= quantity;
            if (order.quantity == 0) levels.remove(0);
        }
    }

    private static final class MutableVenueOrder {
        private final String orderId;
        private final SignalDirection side;
        private final long sequence;
        private double price;
        private int quantity;

        private MutableVenueOrder(VenueOrder order) {
            this.orderId = order.orderId();
            this.side = order.side();
            this.price = order.price();
            this.quantity = order.quantity();
            this.sequence = order.sequence();
        }

        private VenueOrder value() {
            return new VenueOrder(orderId, side, price, quantity, sequence);
        }
    }

    private static final class BookState {
        private final List<MutableLevel> bids;
        private final List<MutableLevel> asks;

        private BookState(List<MutableLevel> bids, List<MutableLevel> asks) {
            this.bids = bids;
            this.asks = asks;
        }

        private static BookState from(OrderBookSnapshot snapshot) {
            List<MutableLevel> bids = new ArrayList<>();
            for (BookLevel level : snapshot.bids()) bids.add(new MutableLevel(level));
            List<MutableLevel> asks = new ArrayList<>();
            for (BookLevel level : snapshot.asks()) asks.add(new MutableLevel(level));
            return new BookState(bids, asks);
        }

        private void apply(OrderBookDelta delta) {
            if (delta.action() == BookAction.CLEAR) {
                levels(delta.side()).clear();
                return;
            }
            List<MutableLevel> levels = levels(delta.side());
            MutableLevel existing = null;
            for (MutableLevel level : levels) {
                if (Double.compare(level.price, delta.price()) == 0) {
                    existing = level;
                    break;
                }
            }
            switch (delta.action()) {
                case ADD -> {
                    if (existing == null) levels.add(new MutableLevel(new BookLevel(delta.price(), delta.quantity())));
                    else existing.quantity += delta.quantity();
                }
                case UPDATE -> {
                    if (existing == null) levels.add(new MutableLevel(new BookLevel(delta.price(), delta.quantity())));
                    else existing.quantity = delta.quantity();
                }
                case DELETE -> {
                    if (existing != null) levels.remove(existing);
                }
                case CLEAR -> { }
            }
            levels.sort(delta.side() == SignalDirection.BUY
                    ? (left, right) -> Double.compare(right.price, left.price)
                    : (left, right) -> Double.compare(left.price, right.price));
        }

        private List<MutableLevel> levels(SignalDirection side) {
            return side == SignalDirection.BUY ? bids : asks;
        }

        private BookLevel bestLevel(boolean buying) {
            List<MutableLevel> levels = buying ? asks : bids;
            return levels.isEmpty() ? null : levels.get(0).value();
        }

        private double bestAsk() {
            if (asks.isEmpty()) return Double.POSITIVE_INFINITY;
            return asks.get(0).price;
        }

        private double bestBid() {
            if (bids.isEmpty()) return Double.NEGATIVE_INFINITY;
            return bids.get(0).price;
        }

        private void consume(boolean buying, int quantity) {
            List<MutableLevel> levels = buying ? asks : bids;
            MutableLevel level = levels.get(0);
            level.quantity -= quantity;
            if (level.quantity == 0) levels.remove(0);
        }
    }

    private static final class MutableLevel {
        private final double price;
        private int quantity;

        private MutableLevel(BookLevel level) {
            this.price = level.price();
            this.quantity = level.quantity();
        }

        private BookLevel value() {
            return new BookLevel(price, quantity);
        }
    }
}