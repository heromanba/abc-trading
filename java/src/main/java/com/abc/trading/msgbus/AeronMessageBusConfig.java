package com.abc.trading.msgbus;

import java.time.Duration;

/** Configuration for Aeron IPC or directory-based inter-process messaging. */
public record AeronMessageBusConfig(
        String channel,
        int streamId,
        boolean embeddedDriver,
        String aeronDirectory,
        int fragmentLimit,
        int maxRetries,
        Duration retryDelay) {
    public AeronMessageBusConfig {
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("channel is required");
        if (streamId <= 0) throw new IllegalArgumentException("streamId must be positive");
        if (embeddedDriver && aeronDirectory != null && aeronDirectory.isBlank()) {
            throw new IllegalArgumentException("aeronDirectory must not be blank");
        }
        if (!embeddedDriver && (aeronDirectory == null || aeronDirectory.isBlank())) {
            throw new IllegalArgumentException("aeronDirectory is required without an embedded driver");
        }
        if (fragmentLimit < 1) throw new IllegalArgumentException("fragmentLimit must be positive");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be non-negative");
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) {
            throw new IllegalArgumentException("retryDelay must be positive");
        }
    }

    public AeronMessageBusConfig(String channel, int streamId, boolean embeddedDriver) {
        this(channel, streamId, embeddedDriver, null, 10, 100, Duration.ofMillis(1));
    }

    public static AeronMessageBusConfig embedded() {
        return new AeronMessageBusConfig("aeron:ipc", 1001, true);
    }

    public static AeronMessageBusConfig ipc(String aeronDirectory) {
        return new AeronMessageBusConfig("aeron:ipc", 1001, false, aeronDirectory,
                10, 100, Duration.ofMillis(1));
    }
}
