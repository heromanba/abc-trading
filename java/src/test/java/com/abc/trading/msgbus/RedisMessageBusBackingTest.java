package com.abc.trading.msgbus;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.StreamEntryID;
import redis.clients.jedis.resps.StreamEntry;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisMessageBusBackingTest {
    @Test
    void encodesAndDecodesTheRustCompatibleStreamFields() {
        byte[] payload = new byte[]{0, 1, (byte) 255};
        BusMessage message = new BusMessage("data.trade.BINANCE.BTCUSDT", "QuoteTick",
                payload, SerializationEncoding.JSON);

        Map<String, String> fields = RedisMessageBusBacking.encode(message);
        StreamEntry entry = new StreamEntry(new StreamEntryID("1-0"), fields);
        BusMessage decoded = RedisMessageBusBacking.decode(entry);

        assertEquals(message.getTopic(), decoded.getTopic());
        assertEquals(message.getPayloadType(), decoded.getPayloadType());
        assertEquals(message.getEncoding(), decoded.getEncoding());
        assertArrayEquals(payload, decoded.getPayload());
    }

    @Test
    void rejectsPublicationAfterClose() {
        RedisMessageBusBacking backing = new RedisMessageBusBacking(
                new RedisMessageBusConfig("127.0.0.1", 6379, "test-closed"));
        backing.close();

        assertThrows(IllegalStateException.class, () -> backing.publish(
                new BusMessage("topic", "type", new byte[]{1}, SerializationEncoding.JSON)));
    }

    @Test
    void publishesAcknowledgesAndRetriesPendingMessages() throws Exception {
        Assumptions.assumeTrue(redisAvailable());
        String suffix = UUID.randomUUID().toString();
        String stream = "abc-trading-test:" + suffix;
        String group = "group-" + suffix;
        String consumer = "consumer-" + suffix;
        RedisMessageBusConfig config = new RedisMessageBusConfig(
            "127.0.0.1", 6379, null, null, false,
                Duration.ofSeconds(2), Duration.ofSeconds(2), stream, 10, Duration.ofMillis(100),
                3, Duration.ofMillis(25));
        BusMessage message = new BusMessage("data.test", "TestPayload",
                new byte[]{7, 8, 9}, SerializationEncoding.JSON);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<BusMessage> received = new AtomicReference<>();

        try (RedisMessageBusBacking backing = new RedisMessageBusBacking(config)) {
            try (RedisMessageBusBacking.RedisSubscription subscription = backing.subscribe(
                    group, consumer, candidate -> {
                        if (attempts.incrementAndGet() == 1) {
                            throw new IllegalStateException("retry once");
                        }
                        received.set(candidate);
                        delivered.countDown();
                    })) {
                backing.publish(message);
                assertTrue(delivered.await(5, TimeUnit.SECONDS),
                    () -> "Redis consumer failure: " + backing.failure());
                assertEquals(2, attempts.get());
                assertArrayEquals(message.getPayload(), received.get().getPayload());
                Thread.sleep(250);
                assertEquals(2, attempts.get());
            }
        }
        try (JedisPooled redis = new JedisPooled("127.0.0.1", 6379)) {
            redis.del(stream);
        }
    }

    private static boolean redisAvailable() {
        try (JedisPooled redis = new JedisPooled("127.0.0.1", 6379)) {
            return "PONG".equals(redis.ping());
        } catch (RuntimeException error) {
            return false;
        }
    }
}
