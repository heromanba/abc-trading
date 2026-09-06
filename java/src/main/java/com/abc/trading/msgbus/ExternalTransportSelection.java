package com.abc.trading.msgbus;

import java.util.Objects;

/** Explicit runtime selection for optional external message transport. */
public final class ExternalTransportSelection {
    public enum Type {
        NONE,
        REDIS,
        AERON
    }

    private final Type type;
    private final RedisMessageBusConfig redisConfig;
    private final AeronMessageBusConfig aeronConfig;

    private ExternalTransportSelection(Type type, RedisMessageBusConfig redisConfig,
            AeronMessageBusConfig aeronConfig) {
        this.type = Objects.requireNonNull(type, "type");
        this.redisConfig = redisConfig;
        this.aeronConfig = aeronConfig;
    }

    public static ExternalTransportSelection none() {
        return new ExternalTransportSelection(Type.NONE, null, null);
    }

    public static ExternalTransportSelection redis(RedisMessageBusConfig config) {
        return new ExternalTransportSelection(Type.REDIS, Objects.requireNonNull(config, "config"), null);
    }

    public static ExternalTransportSelection aeron(AeronMessageBusConfig config) {
        return new ExternalTransportSelection(Type.AERON, null, Objects.requireNonNull(config, "config"));
    }

    public Type type() {
        return type;
    }

    public MessageBusBacking createBacking() {
        return switch (type) {
            case NONE -> null;
            case REDIS -> new RedisMessageBusBacking(redisConfig);
            case AERON -> new AeronMessageBusBacking(aeronConfig);
        };
    }
}
