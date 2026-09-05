package com.abc.trading.msgbus;

import java.time.Duration;

/** Configuration for a Redis Streams message-bus backing. */
public record RedisMessageBusConfig(
        String host,
        int port,
        String username,
        String password,
        boolean ssl,
        Duration connectionTimeout,
        Duration responseTimeout,
        String streamKey,
        int batchSize,
        Duration blockTimeout,
        int maxRetries,
        Duration retryDelay) {
    public RedisMessageBusConfig {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be valid");
        if (connectionTimeout == null || connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
        if (responseTimeout == null || responseTimeout.isNegative() || responseTimeout.isZero()) {
            throw new IllegalArgumentException("responseTimeout must be positive");
        }
        if (streamKey == null || streamKey.isBlank()) throw new IllegalArgumentException("streamKey is required");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        if (blockTimeout == null || blockTimeout.isNegative() || blockTimeout.isZero()) {
            throw new IllegalArgumentException("blockTimeout must be positive");
        }
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be non-negative");
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("retryDelay must be positive");
        }
    }

    public RedisMessageBusConfig(String host, int port, String streamKey) {
        this(host, port, null, null, false, Duration.ofSeconds(20), Duration.ofSeconds(20),
                streamKey, 100, Duration.ofMillis(1_000), 3, Duration.ofMillis(100));
    }

    public static RedisMessageBusConfig defaults() {
        return new RedisMessageBusConfig("127.0.0.1", 6379, null, null, false,
                Duration.ofSeconds(20), Duration.ofSeconds(20), "stream", 100,
                Duration.ofMillis(1_000), 3, Duration.ofMillis(100));
    }

    public String redisUri() {
        String scheme = ssl ? "rediss" : "redis";
        String credentials = username == null || username.isBlank()
                ? ""
                : username + (password == null ? "" : ":" + password) + "@";
        return scheme + "://" + credentials + host + ":" + port;
    }
}
