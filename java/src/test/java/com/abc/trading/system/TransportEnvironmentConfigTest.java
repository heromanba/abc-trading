package com.abc.trading.system;

import com.abc.trading.msgbus.ExternalTransportSelection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransportEnvironmentConfigTest {
    @Test
    void defaultsToNoExternalTransport() {
        TransportEnvironmentConfig config = TransportEnvironmentConfig.from(Map.of());

        assertEquals("NONE", config.transport());
        assertEquals(ExternalTransportSelection.Type.NONE, config.selection().type());
    }

    @Test
    void parsesRedisDeploymentSettings() {
        TransportEnvironmentConfig config = TransportEnvironmentConfig.from(Map.of(
                "ABC_TRADING_TRANSPORT", "redis",
                "ABC_TRADING_REDIS_HOST", "redis.internal",
                "ABC_TRADING_REDIS_PORT", "6380",
                "ABC_TRADING_REDIS_STREAM", "trading-stream",
                "ABC_TRADING_REDIS_SSL", "true"));

        assertEquals(ExternalTransportSelection.Type.REDIS, config.selection().type());
        assertEquals("REDIS", config.transport());
    }

    @Test
    void rejectsInvalidTransport() {
        assertThrows(IllegalArgumentException.class,
                () -> TransportEnvironmentConfig.from(Map.of("ABC_TRADING_TRANSPORT", "kafka")));
    }
}
