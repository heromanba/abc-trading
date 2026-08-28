package com.abc.trading.adapters.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps Binance WebSocket JSON payloads using the same field vocabulary as Nautilus. */
public final class BinanceMessageMapper {
    private final ObjectMapper mapper = new ObjectMapper();

    public void dispatchMarket(String payload, BinanceMarketDataHandler handler) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode data = root.has("data") ? root.get("data") : root;
            String event = text(data, "e");
            switch (event) {
                case "depthUpdate" -> handler.onDepth(depth(data));
                case "aggTrade" -> handler.onTrade(trade(data));
                case "markPriceUpdate" -> handler.onMarkPrice(mark(data));
                default -> { }
            }
        } catch (RuntimeException error) {
            handler.onError(error);
        } catch (Exception error) {
            handler.onError(new IllegalArgumentException("Invalid Binance market payload", error));
        }
    }

    public void dispatchUser(String payload, BinanceExecutionHandler handler) {
        try {
            JsonNode root = mapper.readTree(payload);
            String event = text(root, "e");
            switch (event) {
                case "ORDER_TRADE_UPDATE" -> handler.onOrderUpdate(order(root.get("o"), root));
                case "ACCOUNT_UPDATE" -> handler.onAccountUpdate(account(root.get("a"), root));
                default -> { }
            }
        } catch (RuntimeException error) {
            handler.onError(error);
        } catch (Exception error) {
            handler.onError(new IllegalArgumentException("Invalid Binance user payload", error));
        }
    }

    private BinanceDepthUpdate depth(JsonNode data) {
        return new BinanceDepthUpdate(text(data, "s"), longValue(data, "E"), longValue(data, "T"),
                longValue(data, "U"), longValue(data, "u"), longValue(data, "pu", 0),
                levels(data.get("b")), levels(data.get("a")));
    }

    private BinanceTradeEvent trade(JsonNode data) {
        return new BinanceTradeEvent(text(data, "s"), longValue(data, "E"), longValue(data, "T"),
                longValue(data, "a"), decimal(data, "p"), decimal(data, "q"), bool(data, "m"));
    }

    private BinanceMarkPriceEvent mark(JsonNode data) {
        return new BinanceMarkPriceEvent(text(data, "s"), longValue(data, "E"),
                decimal(data, "p"), decimal(data, "i"), longValue(data, "T", 0));
    }

    private BinanceOrderUpdate order(JsonNode data, JsonNode envelope) {
        return new BinanceOrderUpdate(text(data, "s"), longValue(envelope, "E"), longValue(data, "i"),
                text(data, "c"), text(data, "S"), text(data, "o"), text(data, "f"), text(data, "x"),
                text(data, "X"), decimal(data, "l"), decimal(data, "L"), decimal(data, "n", "0"),
                text(data, "N", ""), bool(data, "R"));
    }

    private BinanceAccountUpdate account(JsonNode data, JsonNode envelope) {
        Map<String, BigDecimal> wallet = new LinkedHashMap<>();
        Map<String, BigDecimal> margin = new LinkedHashMap<>();
        Map<String, BigDecimal> pnl = new LinkedHashMap<>();
        for (JsonNode balance : data.path("B")) {
            String asset = text(balance, "a");
            wallet.put(asset, decimal(balance, "wb"));
            margin.put(asset, decimal(balance, "cw"));
        }
        for (JsonNode position : data.path("P")) {
            pnl.put(text(position, "s"), decimal(position, "up"));
        }
        return new BinanceAccountUpdate(longValue(envelope, "E"), wallet, margin, pnl);
    }

    private static List<BinancePriceLevel> levels(JsonNode value) {
        List<BinancePriceLevel> result = new ArrayList<>();
        if (value != null) for (JsonNode level : value) {
            if (!level.isArray() || level.size() < 2) throw new IllegalArgumentException("Invalid Binance price level");
            result.add(new BinancePriceLevel(new BigDecimal(level.get(0).asText()), new BigDecimal(level.get(1).asText())));
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) { return text(node, field, null); }
    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException("Missing Binance field: " + field);
        }
        return value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) { return decimal(node, field, null); }
    private static BigDecimal decimal(JsonNode node, String field, String fallback) {
        String value = text(node, field, fallback);
        return new BigDecimal(value);
    }

    private static long longValue(JsonNode node, String field) { return longValue(node, field, Long.MIN_VALUE); }
    private static long longValue(JsonNode node, String field, long fallback) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            if (fallback != Long.MIN_VALUE) return fallback;
            throw new IllegalArgumentException("Missing Binance field: " + field);
        }
        return value.asLong();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.asBoolean();
    }
}
