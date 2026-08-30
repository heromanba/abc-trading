package com.abc.trading.adapters.binance;

import com.abc.trading.data.DataClient;
import com.abc.trading.data.Quantity;
import com.abc.trading.execution.ExecutionClient;
import com.abc.trading.execution.LimitOrderIntent;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.TimeInForce;
import com.abc.trading.execution.VenueId;
import com.abc.trading.execution.commands.CancelOrder;
import com.abc.trading.execution.commands.ModifyOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Binance USD-M Futures adapter with Nautilus-aligned market and user-event boundaries.
 * Public market data remains decimal and is delivered to the supplied market handler.
 */
public final class BinanceFuturesAdapter implements DataClient, ExecutionClient {
    public static final String CLIENT_ID = "BINANCE_USDM";
    private static final String API_KEY_HEADER = "X-MBX-APIKEY";

    private final BinanceFuturesConfig config;
    private final BinanceHttpTransport http;
    private final BinanceMarketDataHandler marketHandler;
    private final BinanceExecutionHandler executionHandler;
    private final BinanceMessageMapper mapper = new BinanceMessageMapper();
    private final ObjectMapper json = new ObjectMapper();
    private ScheduledExecutorService scheduler;
    private final Object lifecycleLock = new Object();
    private WebSocket marketSocket;
    private WebSocket userSocket;
    private ScheduledFuture<?> keepaliveTask;
    private String listenKey;
    private boolean started;
    private boolean stopping;

    public BinanceFuturesAdapter(
            BinanceFuturesConfig config,
            BinanceMarketDataHandler marketHandler,
            BinanceExecutionHandler executionHandler) {
        this(config, new JavaBinanceHttpTransport(config.httpBaseUrl(), config.requestTimeout()),
                marketHandler, executionHandler);
    }

    public BinanceFuturesAdapter(
            BinanceFuturesConfig config,
            BinanceHttpTransport http,
            BinanceMarketDataHandler marketHandler,
            BinanceExecutionHandler executionHandler) {
        if (config == null) throw new IllegalArgumentException("config is required");
        if (http == null) throw new IllegalArgumentException("http is required");
        this.config = config;
        this.http = http;
        this.marketHandler = marketHandler == null ? new BinanceMarketDataHandler() { } : marketHandler;
        this.executionHandler = executionHandler == null ? new BinanceExecutionHandler() { } : executionHandler;
        this.scheduler = newScheduler();
    }

    public BinanceFuturesConfig config() { return config; }

    public String exchangeInfoJson() {
        return request("GET", "/fapi/v1/exchangeInfo", Map.of(), false);
    }

    public String depthJson(String symbol, int limit) {
        if (limit <= 0 || limit > 1000) throw new IllegalArgumentException("limit must be in 1..1000");
        return request("GET", "/fapi/v1/depth", Map.of(
                "symbol", symbol.toUpperCase(java.util.Locale.ROOT),
                "limit", Integer.toString(limit)), false);
    }

    public String accountJson() {
        if (!config.authenticated()) throw new IllegalStateException("authenticated credentials are required");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        return request("GET", "/fapi/v2/account", params, true);
    }

    public String accountSnapshotJson() {
        return accountJson();
    }

    public void acceptMarketPayload(String payload) {
        mapper.dispatchMarket(payload, marketHandler);
    }

    public void acceptUserPayload(String payload) {
        mapper.dispatchUser(payload, executionHandler);
    }

    @Override
    public String clientId() { return CLIENT_ID; }

    @Override
    public VenueId venue() { return new VenueId("BINANCE"); }

    public boolean started() {
        synchronized (lifecycleLock) { return started; }
    }

    public String marketStreamUrl() {
        List<String> streams = new ArrayList<>();
        for (String symbol : config.symbols()) {
            String lower = symbol.toLowerCase(java.util.Locale.ROOT);
            streams.add(lower + "@depth@100ms");
            streams.add(lower + "@aggTrade");
            streams.add(lower + "@markPrice@1s");
        }
        return trim(config.publicWebSocketBaseUrl()) + "/stream?streams=" + String.join("/", streams);
    }

    public String userStreamUrl(String key) {
        return config.userStreamUrl(key);
    }

    @Override
    public void start() {
        synchronized (lifecycleLock) {
            if (started) return;
            if (scheduler.isShutdown()) scheduler = newScheduler();
            started = true;
            stopping = false;
        }
        if (!config.connectOnStart()) return;
        connectMarketSocket();
        if (config.authenticated()) connectUserStream();
    }

    @Override
    public void stop() {
        synchronized (lifecycleLock) {
            stopping = true;
            started = false;
        }
        if (keepaliveTask != null) keepaliveTask.cancel(false);
        closeSocket(marketSocket);
        closeSocket(userSocket);
        marketSocket = null;
        userSocket = null;
        if (listenKey != null && config.authenticated()) {
            try {
                request("DELETE", "/fapi/v1/listenKey", Map.of("listenKey", listenKey), false);
            } catch (RuntimeException ignored) { }
        }
        listenKey = null;
        scheduler.shutdownNow();
    }

    @Override
    public void reset() {
        stop();
    }

    @Override
    public void submitMarketOrder(OrderIntent order) {
        String type = order.trailingOffsetType() != null ? "TRAILING_STOP_MARKET"
            : order.triggerPrice() > 0.0 ? "STOP_MARKET" : "MARKET";
        if ("TRAILING_STOP_MARKET".equals(type) && order.trailingOffset() <= 0.0) {
            throw new IllegalArgumentException("trailingOffset must be positive");
        }
        placeOrder(order.symbol(), order.side(), type, order.quantity(), null,
            order.timeInForce(), order.orderId(), false, 0L,
            order.triggerPrice(), order.activationPrice(),
            order.trailingOffset() / 100.0);
    }

    @Override
    public void submitLimitOrder(LimitOrderIntent order) {
        if (order.trailingOffsetType() != null) {
            throw new IllegalArgumentException("Binance USD-M does not support trailing stop-limit orders");
        }
        String type = order.triggerPrice() > 0.0 ? "STOP" : "LIMIT";
        placeOrder(order.symbol(), order.side(), type, order.quantity(), order.limitPrice(),
                order.timeInForce(), order.orderId(), false, order.expireTimeNs(),
                order.triggerPrice(), 0.0, 0.0);
    }

    public void submitLimitOrderDecimal(String symbol, SignalDirection side, BigDecimal quantity,
            BigDecimal price, String clientOrderId) {
        if (quantity == null || price == null || clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("decimal order values are required");
        }
        placeOrderDecimal(symbol, side, "LIMIT", quantity, price, TimeInForce.GTC,
                clientOrderId, false, 0L, 0.0, 0.0, 0.0);
    }

    public boolean cancelOrder(String symbol, String clientOrderId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.toUpperCase(java.util.Locale.ROOT));
        params.put("origClientOrderId", clientOrderId);
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        try {
            request("DELETE", "/fapi/v1/order", params, true);
            return true;
        } catch (RuntimeException error) {
            executionHandler.onError(error);
            return false;
        }
    }

    @Override
    public boolean cancelOrder(CancelOrder command) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", command.symbol().toUpperCase(java.util.Locale.ROOT));
        params.put("origClientOrderId", command.clientOrderId());
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        try {
            request("DELETE", "/fapi/v1/order", params, true);
            return true;
        } catch (RuntimeException error) {
            executionHandler.onError(error);
            return false;
        }
    }

    @Override
    public int cancelAllOrders(String symbol, long timestampNs) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.toUpperCase(java.util.Locale.ROOT));
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        try {
            String response = request("DELETE", "/fapi/v1/allOpenOrders", params, true);
            JsonNode node = json.readTree(response);
            return node.isArray() ? node.size() : 0;
        } catch (Exception error) {
            executionHandler.onError(error);
            return 0;
        }
    }

    @Override
    public boolean modifyOrder(ModifyOrder command) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", command.symbol().toUpperCase(java.util.Locale.ROOT));
        params.put("origClientOrderId", command.clientOrderId());
        if (command.quantity() != null) params.put("quantity", command.quantity().asDecimal().stripTrailingZeros().toPlainString());
        if (command.price() != null) params.put("price", decimal(command.price()));
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        try {
            String response = request("PUT", "/fapi/v1/order", params, true);
            executionHandler.onOrderUpdate(parseOrderResponse(json.readTree(response)));
            return true;
        } catch (Exception error) {
            executionHandler.onError(error);
            return false;
        }
    }

    @Override
    public void executeLiquidation(OrderIntent order) {
        placeOrder(order.symbol(), order.side(), "MARKET", order.quantity(), null,
                TimeInForce.IOC, order.orderId(), true, 0L, 0.0, 0.0, 0.0);
    }

    private void placeOrder(String symbol, SignalDirection side, String type, Quantity quantity,
            Double price, TimeInForce timeInForce, String clientOrderId, boolean reduceOnly,
            long expireTimeNs, double stopPrice, double activationPrice, double callbackRate) {
            placeOrderDecimal(symbol, side, type, quantity.asDecimal(),
                price == null ? null : BigDecimal.valueOf(price), timeInForce, clientOrderId,
                reduceOnly, expireTimeNs, stopPrice, activationPrice, callbackRate);
            }

            private void placeOrderDecimal(String symbol, SignalDirection side, String type, BigDecimal quantity,
                BigDecimal price, TimeInForce timeInForce, String clientOrderId, boolean reduceOnly,
                long expireTimeNs, double stopPrice, double activationPrice, double callbackRate) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol.toUpperCase(java.util.Locale.ROOT));
        params.put("side", side.name());
        params.put("type", type);
        params.put("quantity", quantity.stripTrailingZeros().toPlainString());
        if (price != null) {
            params.put("price", price.stripTrailingZeros().toPlainString());
            params.put("timeInForce", binanceTimeInForce(timeInForce));
            if (timeInForce == TimeInForce.GTD && config.useGtd()) {
                params.put("goodTillDate", Long.toString(Math.max(System.currentTimeMillis() + 1_000L,
                        expireTimeNs / 1_000_000L)));
            }
        }
        if (stopPrice > 0.0) params.put("stopPrice", decimal(stopPrice));
        if (activationPrice > 0.0) params.put("activationPrice", decimal(activationPrice));
        if (callbackRate > 0.0) params.put("callbackRate", decimal(callbackRate));
        params.put("newClientOrderId", clientOrderId);
        if (reduceOnly) params.put("reduceOnly", "true");
        params.put("timestamp", Long.toString(System.currentTimeMillis()));
        params.put("recvWindow", Long.toString(config.recvWindowMs()));
        try {
            String response = request("POST", "/fapi/v1/order", params, true);
            executionHandler.onOrderUpdate(parseOrderResponse(json.readTree(response)));
        } catch (Exception error) {
            executionHandler.onError(error);
        }
    }

    private String request(String method, String path, Map<String, String> input, boolean signed) {
        Map<String, String> params = new LinkedHashMap<>(input);
        Map<String, String> headers = new LinkedHashMap<>();
        if (config.apiKey() != null) headers.put(API_KEY_HEADER, config.apiKey());
        if (signed) params.put("signature", BinanceHmacSigner.sign(config.apiSecret(), params));
        return http.request(method, path, params, headers);
    }

    private void connectMarketSocket() {
        openSocket(marketStreamUrl(), true);
    }

    private void connectUserStream() {
        try {
            JsonNode response = json.readTree(request("POST", "/fapi/v1/listenKey", Map.of(), false));
            listenKey = response.path("listenKey").asText(null);
            if (listenKey == null || listenKey.isBlank()) throw new IllegalStateException("Binance listenKey missing");
            openSocket(userStreamUrl(listenKey), false);
            keepaliveTask = scheduler.scheduleAtFixedRate(this::keepaliveUserStream, 30, 30, TimeUnit.MINUTES);
        } catch (Exception error) {
            executionHandler.onError(error);
        }
    }

    private void keepaliveUserStream() {
        if (listenKey == null || stopping) return;
        try {
            request("PUT", "/fapi/v1/listenKey", Map.of("listenKey", listenKey), false);
        } catch (RuntimeException error) {
            executionHandler.onError(error);
        }
    }

    private void openSocket(String url, boolean market) {
        HttpClient.newHttpClient().newWebSocketBuilder().connectTimeout(config.requestTimeout())
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    private final StringBuilder message = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        if (market) marketSocket = webSocket; else userSocket = webSocket;
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        message.append(data);
                        if (last) {
                            if (market) mapper.dispatchMarket(message.toString(), marketHandler);
                            else mapper.dispatchUser(message.toString(), executionHandler);
                            message.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (market) marketHandler.onError(error); else executionHandler.onError(error);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        scheduleReconnect(market);
                        return null;
                    }
                }).exceptionally(error -> {
                    if (market) marketHandler.onError(error); else executionHandler.onError(error);
                    scheduleReconnect(market);
                    return null;
                });
    }

    private void scheduleReconnect(boolean market) {
        synchronized (lifecycleLock) {
            if (!started || stopping) return;
        }
        scheduler.schedule(() -> {
            if (market) connectMarketSocket();
            else if (listenKey != null) openSocket(userStreamUrl(listenKey), false);
        }, config.reconnectDelay().toMillis(), TimeUnit.MILLISECONDS);
    }

    private BinanceOrderUpdate parseOrderResponse(JsonNode node) {
        return new BinanceOrderUpdate(
                node.path("symbol").asText(""), System.currentTimeMillis(), node.path("orderId").asLong(0),
                node.path("clientOrderId").asText(""), node.path("side").asText(""), node.path("type").asText(""),
                node.path("timeInForce").asText(""), "NEW", node.path("status").asText("NEW"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", node.path("reduceOnly").asBoolean(false));
    }

    private static String binanceTimeInForce(TimeInForce timeInForce) {
        return switch (timeInForce) {
            case GTC, DAY -> "GTC";
            case GTD -> "GTD";
            case IOC -> "IOC";
            case FOK -> "FOK";
        };
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static ScheduledExecutorService newScheduler() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "binance-usdm-adapter");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static String trim(String value) { return value.replaceAll("/+$", ""); }

    private static void closeSocket(WebSocket socket) {
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
    }
}
