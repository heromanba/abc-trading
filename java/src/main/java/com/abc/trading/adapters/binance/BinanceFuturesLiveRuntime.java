package com.abc.trading.adapters.binance;

import com.abc.trading.data.DataClient;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.BookLevel;
import com.abc.trading.data.TradeTick;
import com.abc.trading.data.AggressorSide;
import com.abc.trading.data.Quantity;
import com.abc.trading.execution.ExecutionClient;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderFill;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.VenueId;
import com.abc.trading.execution.LiquiditySide;
import com.abc.trading.execution.OrderCanceled;
import com.abc.trading.execution.OrderRejected;
import com.abc.trading.execution.LimitOrderRejected;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.abc.trading.portfolio.AccountStateEvent;
import com.abc.trading.portfolio.AccountState;
import com.abc.trading.portfolio.AccountBalance;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Bridges Binance protocol events into the Java kernel's typed runtime. */
public final class BinanceFuturesLiveRuntime implements DataClient, ExecutionClient {
    private final BinanceFuturesAdapter adapter;
    private final Consumer<Object> eventSink;
    private final Consumer<Object> marketSink;
    private final Consumer<TradeTick> tradeSink;
    private final Map<String, OrderIntent> marketOrders = new LinkedHashMap<>();
    private final Map<String, LimitOrderIntent> limitOrders = new LinkedHashMap<>();
    private final Map<String, Map<String, BigDecimal>> bids = new LinkedHashMap<>();
    private final Map<String, Map<String, BigDecimal>> asks = new LinkedHashMap<>();
    private final Map<String, BinanceInstrumentMetadata> instrumentMetadata = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private long sequence;
    private final Map<String, Long> lastDepthUpdateIds = new LinkedHashMap<>();
    private double last = 1.0;
    private double mark = 1.0;
    private boolean started;
    private BinanceAccountSnapshot accountSnapshot;

    public BinanceFuturesLiveRuntime(BinanceFuturesConfig config, BinanceHttpTransport http,
            Consumer<Object> eventSink) {
        this(config, http, eventSink, eventSink);
    }

    public BinanceFuturesLiveRuntime(BinanceFuturesConfig config, BinanceHttpTransport http,
            Consumer<Object> eventSink, Consumer<Object> marketDataSink) {
        if (eventSink == null) throw new IllegalArgumentException("eventSink is required");
        if (marketDataSink == null) throw new IllegalArgumentException("marketDataSink is required");
        this.eventSink = eventSink;
        this.marketSink = event -> marketDataSink.accept(event);
        this.tradeSink = event -> marketDataSink.accept(event);
        this.adapter = new BinanceFuturesAdapter(config, http,
                new BinanceMarketDataHandler() {
                    @Override public void onDepth(BinanceDepthUpdate update) { handleDepth(update); }
                    @Override public void onTrade(BinanceTradeEvent event) { handleTrade(event); }
                    @Override public void onMarkPrice(BinanceMarkPriceEvent event) { handleMark(event); }
                    @Override public void onError(Throwable error) { eventSink.accept(error); }
                },
                new BinanceExecutionHandler() {
                    @Override public void onOrderUpdate(BinanceOrderUpdate update) { handleOrder(update); }
                    @Override public void onAccountUpdate(BinanceAccountUpdate update) { handleAccountUpdate(update); }
                    @Override public void onError(Throwable error) { eventSink.accept(error); }
                });
    }

    public BinanceFuturesAdapter adapter() { return adapter; }

    public void acceptMarketPayload(String payload) { adapter.acceptMarketPayload(payload); }

    public void acceptUserPayload(String payload) { adapter.acceptUserPayload(payload); }

    @Override public String clientId() { return adapter.clientId(); }
    @Override public VenueId venue() { return adapter.venue(); }
    @Override public void start() {
        if (started) return;
        started = true;
        if (adapter.config().connectOnStart()) refreshInstrumentMetadata();
        if (adapter.config().authenticated()) {
            try {
                accountSnapshot = synchronizeAccount();
                eventSink.accept(new AccountStateEvent(accountSnapshot.toAccountState(venue().value())));
            } catch (RuntimeException error) {
                eventSink.accept(error);
            }
        }
        adapter.start();
    }
    @Override public void stop() { if (started) { started = false; adapter.stop(); } }
    @Override public void reset() { stop(); }

    @Override
    public void submitMarketOrder(OrderIntent order) {
        validateOrder(order.symbol(), order.quantity().asDecimal(), null);
        marketOrders.put(order.orderId(), order);
        adapter.submitMarketOrder(order);
    }

    @Override
    public void submitLimitOrder(LimitOrderIntent order) {
        validateOrder(order.symbol(), order.quantity().asDecimal(), BigDecimal.valueOf(order.limitPrice()));
        limitOrders.put(order.orderId(), order);
        adapter.submitLimitOrder(order);
    }

    @Override public boolean cancelOrder(CancelOrder command) { return adapter.cancelOrder(command); }
    @Override public int cancelAllOrders(String symbol, long timestampNs) { return adapter.cancelAllOrders(symbol, timestampNs); }
    @Override public boolean modifyOrder(ModifyOrder command) { return adapter.modifyOrder(command); }
    @Override public void executeLiquidation(OrderIntent order) { marketOrders.put(order.orderId(), order); adapter.executeLiquidation(order); }

    public BinanceInstrumentMetadata parseInstrument(JsonNode symbol) {
        BigDecimal tick = filter(symbol, "PRICE_FILTER", "tickSize");
        BigDecimal step = filter(symbol, "LOT_SIZE", "stepSize");
        BigDecimal min = filter(symbol, "LOT_SIZE", "minQty");
        return new BinanceInstrumentMetadata(symbol.path("symbol").asText(), symbol.path("baseAsset").asText(),
                symbol.path("quoteAsset").asText(), tick, step, min,
                decimal(symbol, "requiredMarginPercent", "0").divide(BigDecimal.valueOf(100)),
                decimal(symbol, "maintMarginPercent", "0").divide(BigDecimal.valueOf(100)));
    }

    public List<BinanceInstrumentMetadata> discoverInstruments() {
        try {
            JsonNode root = mapper.readTree(adapter.exchangeInfoJson());
            List<BinanceInstrumentMetadata> result = new ArrayList<>();
            for (JsonNode symbol : root.path("symbols")) {
                BinanceInstrumentMetadata metadata = parseInstrument(symbol);
                instrumentMetadata.put(metadata.symbol(), metadata);
                result.add(metadata);
            }
            return List.copyOf(result);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to parse Binance exchangeInfo", error);
        }
    }

    public BinanceInstrumentMetadata instrumentMetadata(String symbol) {
        return instrumentMetadata.get(symbol.toUpperCase(java.util.Locale.ROOT));
    }

    public void refreshInstrumentMetadata() {
        discoverInstruments();
    }

    public BinanceAccountSnapshot synchronizeAccount() {
        try {
            JsonNode root = mapper.readTree(adapter.accountSnapshotJson());
            String currency = root.path("assets").get(0).path("asset").asText("USDT");
            JsonNode asset = null;
            for (JsonNode candidate : root.path("assets")) {
                if (currency.equals(candidate.path("asset").asText())) { asset = candidate; break; }
            }
            if (asset == null) throw new IllegalStateException("Binance account asset missing");
            return new BinanceAccountSnapshot(root.path("updateTime").asLong(), currency,
                    decimal(asset, "walletBalance", "0"), decimal(asset, "availableBalance", "0"),
                    decimal(asset, "initialMargin", "0"), decimal(asset, "maintMargin", "0"),
                    decimal(asset, "unrealizedProfit", "0"));
        } catch (Exception error) {
            throw new IllegalStateException("Failed to parse Binance account", error);
        }
    }

    private void handleAccountUpdate(BinanceAccountUpdate update) {
        String currency = accountSnapshot == null ? update.walletBalances().keySet().stream().findFirst().orElse("USDT")
                : accountSnapshot.currency();
        BigDecimal total = update.walletBalances().get(currency);
        BigDecimal free = update.marginBalances().get(currency);
        if (total == null || free == null) {
            eventSink.accept(new IllegalArgumentException("Binance account update is missing " + currency));
            return;
        }
        BigDecimal locked = total.subtract(free);
        if (locked.signum() < 0) {
            eventSink.accept(new IllegalArgumentException("Binance account update has free balance above wallet balance"));
            return;
        }
        BigDecimal unrealized = update.unrealizedPnl().values().stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal initialMargin = accountSnapshot == null ? BigDecimal.ZERO : accountSnapshot.totalInitialMargin();
        BigDecimal maintenanceMargin = accountSnapshot == null ? BigDecimal.ZERO : accountSnapshot.totalMaintenanceMargin();
        AccountBalance balance = new AccountBalance(currency, total, locked, free);
        AccountState state = new AccountState(venue().value(), currency, total, locked,
            free, initialMargin, maintenanceMargin, update.eventTimeMs() * 1_000_000L,
            Map.of(currency, balance), unrealized, total.add(unrealized), false, false);
        eventSink.accept(new AccountStateEvent(state));
    }

    private void handleDepth(BinanceDepthUpdate update) {
        String symbol = update.symbol().toUpperCase(java.util.Locale.ROOT);
        long lastDepthUpdateId = lastDepthUpdateIds.getOrDefault(symbol, 0L);
        if (lastDepthUpdateId != 0 && update.previousUpdateId() != 0
                && update.previousUpdateId() != lastDepthUpdateId) {
            eventSink.accept(new IllegalStateException("Binance depth update gap for " + symbol
                + ": expected pu=" + lastDepthUpdateId + ", received " + update.previousUpdateId()));
            resyncDepth(update.symbol());
            return;
        }
        Map<String, BigDecimal> symbolBids = bids.computeIfAbsent(symbol, ignored -> new LinkedHashMap<>());
        Map<String, BigDecimal> symbolAsks = asks.computeIfAbsent(symbol, ignored -> new LinkedHashMap<>());
        update.bids().forEach(level -> updateLevel(symbolBids, level));
        update.asks().forEach(level -> updateLevel(symbolAsks, level));
        sequence = Math.max(sequence + 1, update.lastUpdateId());
        try {
            List<BookLevel> bidLevels = symbolBids.entrySet().stream()
                .map(entry -> new BookLevel(Double.parseDouble(entry.getKey()), toQuantity(entry.getValue())))
                .toList();
            List<BookLevel> askLevels = symbolAsks.entrySet().stream()
                .map(entry -> new BookLevel(Double.parseDouble(entry.getKey()), toQuantity(entry.getValue())))
                .toList();
            marketSink.accept(new OrderBookSnapshot(update.symbol(), update.eventTimeMs() * 1_000_000L,
                bidLevels, askLevels, sequence));
        } catch (RuntimeException error) {
            eventSink.accept(new IllegalArgumentException("Core book quantity precision cannot represent Binance level", error));
        }
        publishMarket(update.symbol(), update.eventTimeMs(), update.eventTimeMs());
        lastDepthUpdateIds.put(symbol, update.lastUpdateId());
    }

    private void handleTrade(BinanceTradeEvent event) {
        last = event.price().doubleValue();
        sequence++;
        try {
                tradeSink.accept(new TradeTick(event.symbol(), event.tradeTimeMs(), last,
                    toQuantity(event.quantity()), event.buyerIsMaker() ? AggressorSide.SELLER : AggressorSide.BUYER,
                    sequence));
            } catch (RuntimeException error) {
            eventSink.accept(new IllegalArgumentException(
                    "Core trade quantity precision cannot represent Binance quantity", error));
        }
        publishMarket(event.symbol(), event.eventTimeMs(), event.tradeTimeMs());
    }

    private void handleMark(BinanceMarkPriceEvent event) {
        mark = event.markPrice().doubleValue();
        publishMarket(event.symbol(), event.eventTimeMs(), event.eventTimeMs());
    }

    private void handleOrder(BinanceOrderUpdate update) {
        OrderIntent market = marketOrders.get(update.clientOrderId());
        LimitOrderIntent limit = limitOrders.get(update.clientOrderId());
        if (!update.isTrade()) {
            if (market != null || limit != null) publishTerminalOrderEvent(update, market, limit);
            return;
        }
        String symbol = update.symbol();
        SignalDirection side = "BUY".equals(update.side()) ? SignalDirection.BUY : SignalDirection.SELL;
        Quantity quantity = toQuantity(update.lastQuantity());
        double price = update.lastPrice().doubleValue();
        String strategy = market != null ? market.strategyId() : limit != null ? limit.strategyId() : "BINANCE";
        long input = market != null ? market.inputSequence() : limit != null ? limit.inputSequence() : 0L;
        String correlation = market != null ? market.correlationId() : limit != null ? limit.correlationId() : update.clientOrderId();
        OrderFill fill = new OrderFill(strategy, symbol, input, update.eventTimeMs(), correlation,
                update.clientOrderId(), side, quantity, price, BigDecimal.ZERO, 0.0)
                .withLiquiditySide(LiquiditySide.TAKER)
                .withVenueOrderId(Long.toString(update.orderId()))
                .withCommission(new com.abc.trading.execution.Commission(
                    update.commission(), update.commissionAsset()));
        eventSink.accept(fill);
        if (update.isTerminal()) { marketOrders.remove(update.clientOrderId()); limitOrders.remove(update.clientOrderId()); }
    }

    private void publishTerminalOrderEvent(BinanceOrderUpdate update, OrderIntent market,
            LimitOrderIntent limit) {
        String strategy = market != null ? market.strategyId() : limit.strategyId();
        String symbol = update.symbol();
        if ("CANCELED".equals(update.orderStatus()) || "EXPIRED".equals(update.orderStatus())) {
            eventSink.accept(new OrderCanceled(new CancelOrder(strategy, symbol, update.clientOrderId(),
                    "binance-" + update.orderId(), update.eventTimeMs() * 1_000_000L)));
        } else if ("REJECTED".equals(update.orderStatus())) {
            if (market != null) eventSink.accept(new OrderRejected(market, "Binance order rejected"));
            else eventSink.accept(new LimitOrderRejected(limit, "Binance order rejected"));
        }
        if (update.isTerminal()) { marketOrders.remove(update.clientOrderId()); limitOrders.remove(update.clientOrderId()); }
    }

    private void publishMarket(String symbol, long eventTimeMs, long timestampMs) {
        String normalized = symbol.toUpperCase(java.util.Locale.ROOT);
        double bid = best(bids.getOrDefault(normalized, Map.of()), false, last);
        double ask = best(asks.getOrDefault(normalized, Map.of()), true, last);
        marketSink.accept(new MarketDataSnapshot(symbol, timestampMs * 1_000_000L, bid, ask,
                last, mark, mark, sequence));
    }

    private static void updateLevel(Map<String, BigDecimal> side, BinancePriceLevel level) {
        if (level.quantity().signum() == 0) side.remove(level.price().toPlainString());
        else side.put(level.price().toPlainString(), level.quantity());
    }

    private void validateOrder(String symbol, BigDecimal quantity, BigDecimal price) {
        BinanceInstrumentMetadata metadata = instrumentMetadata(symbol);
        if (metadata != null) BinanceOrderValidator.validate(metadata, quantity, price);
    }

    private void resyncDepth(String symbol) {
        try {
            JsonNode root = mapper.readTree(adapter.depthJson(symbol, 1000));
            String normalized = symbol.toUpperCase(java.util.Locale.ROOT);
            Map<String, BigDecimal> symbolBids = new LinkedHashMap<>();
            Map<String, BigDecimal> symbolAsks = new LinkedHashMap<>();
            for (JsonNode level : root.path("bids")) {
                symbolBids.put(level.get(0).asText(), new BigDecimal(level.get(1).asText()));
            }
            for (JsonNode level : root.path("asks")) {
                symbolAsks.put(level.get(0).asText(), new BigDecimal(level.get(1).asText()));
            }
            bids.put(normalized, symbolBids);
            asks.put(normalized, symbolAsks);
            lastDepthUpdateIds.put(normalized, root.path("lastUpdateId").asLong());
        } catch (Exception error) {
            eventSink.accept(new IllegalStateException("Binance depth resynchronization failed", error));
        }
    }

    private static double best(Map<String, BigDecimal> side, boolean lowest, double fallback) {
        return side.keySet().stream().map(BigDecimal::new)
                .min(lowest ? Comparator.naturalOrder() : Comparator.reverseOrder())
                .map(BigDecimal::doubleValue).orElse(fallback);
    }

    private static BigDecimal filter(JsonNode symbol, String type, String field) {
        for (JsonNode value : symbol.path("filters")) if (type.equals(value.path("filterType").asText())) return decimal(value, field, "0.0");
        throw new IllegalArgumentException("Binance filter missing: " + type);
    }

    private static BigDecimal decimal(JsonNode node, String field, String fallback) {
        return new BigDecimal(node.path(field).asText(fallback));
    }

    private static Quantity toQuantity(BigDecimal value) {
        return Quantity.fromDecimal(value, Math.max(0, value.scale()));
    }
}
