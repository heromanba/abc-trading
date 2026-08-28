package com.abc.trading.adapters.binance;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class BinanceQuery {
    private BinanceQuery() { }

    static String encode(Map<String, String> parameters) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (query.length() > 0) query.append('&');
            query.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return query.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
