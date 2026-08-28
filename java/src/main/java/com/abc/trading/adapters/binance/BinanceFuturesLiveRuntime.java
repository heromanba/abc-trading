package com.abc.trading.adapters.binance;

import com.abc.trading.data.DataClient;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.BookLevel;
import com.abc.trading.data.TradeTick;
import com.abc.trading.data.AggressorSide;
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
    private final Consumer<MarketDataSnapshot> marketSink;
    private final Consumer<TradeTick> tradeSink;
    private final Consumer<BinanceAccountUpdate> accountSink;
    private final Map<String, OrderIntent> marketOrders = new LinkedHashMap<>();
    private final Map<String, LimitOrderIntent> limitOrders = new LinkedHashMap<>();
    private final Map<String, BigDecimal> bids = new LinkedHashMap<>();
    private final Map<String, BigDecimal> asks = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private long sequence;
    private double last = 1.0;
    private double mark = 1.0;
    private boolean started;

    public BinanceFuturesLiveRuntime(BinanceFuturesConfig config, BinanceHttpTransport http,
            Consumer<Object> eventSink) {
        if (eventSink == null) throw new IllegalArgumentException("eventSink is required");
        this.eventSink = eventSink;
        this.marketSink = event -> eventSink.accept(event);
        this.tradeSink = event -> eventSink.accept(event);
        this.accountSink = event -> eventSink.accept(event);
        this.adapter = new BinanceFuturesAdapter(config, http,
                new BinanceMarketDataHandler() {
                    @Override public void onDepth(BinanceDepthUpdate update) { handleDepth(update); }
                    @Override public void onTrade(BinanceTradeEvent event) { handleTrade(event); }
                    @Override public void onMarkPrice(BinanceMarkPriceEvent event) { handleMark(event); }
                    @Override public void onError(Throwable error) { eventSink.accept(error); }
                },
                new BinanceExecutionHandler() {
                    @Override public void onOrderUpdate(BinanceOrderUpdate update) { handleOrder(update); }
                    @Override public void onAccountUpdate(BinanceAccountUpdate update) { accountSink.accept(update); }
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
        if (adapter.config().authenticated()) {
            try {
                BinanceAccountSnapshot snapshot = synchronizeAccount();
                eventSink.accept(new AccountStateEvent(snapshot.toAccountState(venue().value())));
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
        marketOrders.put(order.orderId(), order);
        adapter.submitMarketOrder(order);
    }

    @Override
    public void submitLimitOrder(LimitOrderIntent order) {
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
            for (JsonNode symbol : root.path("symbols")) result.add(parseInstrument(symbol));
            return List.copyOf(result);
        } catch (Exception error) {
            throw new IllegalStateException("Failed to parse Binance exchangeInfo", error);
        }
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

    private void handleDepth(BinanceDepthUpdate update) {
        update.bids().forEach(level -> updateLevel(bids, level));
        update.asks().forEach(level -> updateLevel(asks, level));
        sequence = Math.max(sequence + 1, update.lastUpdateId());
        try {
            List<BookLevel> bidLevels = bids.entrySet().stream()
                .map(entry -> new BookLevel(Double.parseDouble(entry.getKey()), entry.getValue().intValueExact()))
                .toList();
            List<BookLevel> askLevels = asks.entrySet().stream()
                .map(entry -> new BookLevel(Double.parseDouble(entry.getKey()), entry.getValue().intValueExact()))
                .toList();
            eventSink.accept(new OrderBookSnapshot(update.symbol(), update.eventTimeMs() * 1_000_000L,
                bidLevels, askLevels, sequence));
        } catch (ArithmeticException error) {
            eventSink.accept(new IllegalArgumentException("Core book quantity precision cannot represent Binance level", error));
        }
        publishMarket(update.symbol(), update.eventTimeMs(), update.eventTimeMs());
    }

    private void handleTrade(BinanceTradeEvent event) {
        last = event.price().doubleValue();
        sequence++;
        try {
            tradeSink.accept(new TradeTick(event.symbol(), event.tradeTimeMs(), last,
                    event.quantity().intValueExact(), event.buyerIsMaker() ? AggressorSide.SELLER : AggressorSide.BUYER,
                    sequence));
        } catch (ArithmeticException error) {
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
        int quantity = update.lastQuantity().intValueExact();
        double price = update.lastPrice().doubleValue();
        String strategy = market != null ? market.strategyId() : limit != null ? limit.strategyId() : "BINANCE";
        long input = market != null ? market.inputSequence() : limit != null ? limit.inputSequence() : 0L;
        String correlation = market != null ? market.correlationId() : limit != null ? limit.correlationId() : update.clientOrderId();
        OrderFill fill = new OrderFill(strategy, symbol, input, update.eventTimeMs(), correlation,
                update.clientOrderId(), side, quantity, price, 0, 0.0)
                .withLiquiditySide(LiquiditySide.TAKER)
                .withVenueOrderId(Long.toString(update.orderId()))
                .withCommission(new com.abc.trading.execution.Commission(
                        update.commission().doubleValue(), update.commissionAsset()));
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
        double bid = best(bids, false, last);
        double ask = best(asks, true, last);
        marketSink.accept(new MarketDataSnapshot(symbol, timestampMs * 1_000_000L, bid, ask,
                last, mark, mark, sequence));
    }

    private static void updateLevel(Map<String, BigDecimal> side, BinancePriceLevel level) {
        if (level.quantity().signum() == 0) side.remove(level.price().toPlainString());
        else side.put(level.price().toPlainString(), level.quantity());
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
}
