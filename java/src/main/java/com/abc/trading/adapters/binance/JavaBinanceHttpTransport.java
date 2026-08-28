package com.abc.trading.adapters.binance;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** JDK HTTP transport for Binance REST endpoints. */
public final class JavaBinanceHttpTransport implements BinanceHttpTransport {
    private final String baseUrl;
    private final HttpClient client;
    private final Duration timeout;

    public JavaBinanceHttpTransport(String baseUrl, Duration timeout) {
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl is required");
        if (timeout == null || timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout is required");
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.timeout = timeout;
    }

    @Override
    public String request(String method, String path, Map<String, String> parameters, Map<String, String> headers) {
        String query = BinanceQuery.encode(parameters == null ? Map.of() : parameters);
        URI uri = URI.create(baseUrl + path + (query.isEmpty() ? "" : "?" + query));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(timeout);
        if (headers != null) headers.forEach(builder::header);
        builder.method(method.toUpperCase(java.util.Locale.ROOT), HttpRequest.BodyPublishers.noBody());
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BinanceApiException(response.statusCode(), response.body());
            }
            return response.body();
        } catch (IOException error) {
            throw new BinanceTransportException("Binance HTTP request failed", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BinanceTransportException("Binance HTTP request interrupted", error);
        }
    }

    public static final class BinanceApiException extends RuntimeException {
        private final int statusCode;

        public BinanceApiException(int statusCode, String body) {
            super("Binance HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
        }

        public int statusCode() { return statusCode; }
    }

    public static final class BinanceTransportException extends RuntimeException {
        public BinanceTransportException(String message, Throwable cause) { super(message, cause); }
    }
}
