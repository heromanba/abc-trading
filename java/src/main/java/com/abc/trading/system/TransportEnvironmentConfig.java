package com.abc.trading.system;

import com.abc.trading.msgbus.AeronMessageBusConfig;
import com.abc.trading.msgbus.ExternalTransportSelection;
import com.abc.trading.msgbus.RedisMessageBusConfig;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** Environment-backed deployment configuration for selecting the external transport. */
public record TransportEnvironmentConfig(
        String transport,
        ExternalTransportSelection selection) {
    public TransportEnvironmentConfig {
        if (transport == null || transport.isBlank()) throw new IllegalArgumentException("transport is required");
        if (selection == null) throw new IllegalArgumentException("selection is required");
    }

    public static TransportEnvironmentConfig fromSystemEnvironment() {
        return from(System.getenv());
    }

    public static TransportEnvironmentConfig from(Map<String, String> environment) {
        String transport = value(environment, "ABC_TRADING_TRANSPORT", "NONE").toUpperCase(Locale.ROOT);
        return switch (transport) {
            case "NONE", "DIRECT" -> new TransportEnvironmentConfig(transport, ExternalTransportSelection.none());
            case "REDIS" -> new TransportEnvironmentConfig(transport,
                    ExternalTransportSelection.redis(redisConfig(environment)));
            case "AERON" -> new TransportEnvironmentConfig(transport,
                    ExternalTransportSelection.aeron(aeronConfig(environment)));
            default -> throw new IllegalArgumentException(
                    "ABC_TRADING_TRANSPORT must be NONE, REDIS, or AERON");
        };
    }

    public NautilusKernelConfig kernelConfig() {
        return new NautilusKernelConfig("NautilusKernel", false, false, selection);
    }

    private static RedisMessageBusConfig redisConfig(Map<String, String> environment) {
        String host = value(environment, "ABC_TRADING_REDIS_HOST", "127.0.0.1");
        int port = integer(environment, "ABC_TRADING_REDIS_PORT", 6379);
        String username = optional(environment, "ABC_TRADING_REDIS_USERNAME");
        String password = optional(environment, "ABC_TRADING_REDIS_PASSWORD");
        boolean ssl = bool(environment, "ABC_TRADING_REDIS_SSL", false);
        Duration timeout = Duration.ofMillis(integer(environment, "ABC_TRADING_REDIS_TIMEOUT_MS", 2_000));
        Duration block = Duration.ofMillis(integer(environment, "ABC_TRADING_REDIS_BLOCK_MS", 1_000));
        String stream = value(environment, "ABC_TRADING_REDIS_STREAM", "stream");
        int batch = integer(environment, "ABC_TRADING_REDIS_BATCH_SIZE", 100);
        int retries = integer(environment, "ABC_TRADING_REDIS_MAX_RETRIES", 3);
        Duration retryDelay = Duration.ofMillis(integer(environment, "ABC_TRADING_REDIS_RETRY_DELAY_MS", 100));
        return new RedisMessageBusConfig(host, port, username, password, ssl, timeout, timeout,
                stream, batch, block, retries, retryDelay);
    }

    private static AeronMessageBusConfig aeronConfig(Map<String, String> environment) {
        String channel = value(environment, "ABC_TRADING_AERON_CHANNEL", "aeron:ipc");
        int streamId = integer(environment, "ABC_TRADING_AERON_STREAM_ID", 1001);
        boolean embedded = bool(environment, "ABC_TRADING_AERON_EMBEDDED", true);
        String directory = optional(environment, "ABC_TRADING_AERON_DIRECTORY");
        int fragments = integer(environment, "ABC_TRADING_AERON_FRAGMENT_LIMIT", 10);
        int retries = integer(environment, "ABC_TRADING_AERON_MAX_RETRIES", 100);
        Duration retryDelay = Duration.ofMillis(integer(environment, "ABC_TRADING_AERON_RETRY_DELAY_MS", 1));
        return new AeronMessageBusConfig(channel, streamId, embedded, directory,
                fragments, retries, retryDelay);
    }

    private static String value(Map<String, String> environment, String key, String defaultValue) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String optional(Map<String, String> environment, String key) {
        String value = environment.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static int integer(Map<String, String> environment, String key, int defaultValue) {
        try {
            return Integer.parseInt(value(environment, key, Integer.toString(defaultValue)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer", error);
        }
    }

    private static boolean bool(Map<String, String> environment, String key, boolean defaultValue) {
        String value = value(environment, key, Boolean.toString(defaultValue));
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(key + " must be true or false");
    }
}
