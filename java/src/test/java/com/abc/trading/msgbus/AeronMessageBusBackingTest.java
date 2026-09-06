package com.abc.trading.msgbus;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AeronMessageBusBackingTest {
    @Test
    void rejectsPublicationAfterShutdown() {
        AeronMessageBusBacking backing = new AeronMessageBusBacking(AeronMessageBusConfig.embedded());
        backing.close();

        assertThrows(IllegalStateException.class, () -> backing.publish(
                new BusMessage("topic", "type", new byte[]{1}, SerializationEncoding.JSON)));
    }

    @Test
    void roundTripsBinaryEnvelopeFields() {
        BusMessage message = new BusMessage("data.trade.BINANCE.BTCUSDT", "QuoteTick",
                new byte[]{0, 1, (byte) 255}, SerializationEncoding.JSON);

        assertEquals(message.getTopic(), AeronMessageBusBacking.decode(
                AeronMessageBusBacking.encode(message)).getTopic());
        BusMessage decoded = AeronMessageBusBacking.decode(AeronMessageBusBacking.encode(message));
        assertEquals(message.getPayloadType(), decoded.getPayloadType());
        assertEquals(message.getEncoding(), decoded.getEncoding());
        assertArrayEquals(message.getPayload(), decoded.getPayload());
    }

    @Test
    void deliversOrderedMessagesThroughAeronIpcAndTypedMessageBus() throws Exception {
        int streamId = 2_000 + Math.floorMod(UUID.randomUUID().hashCode(), 10_000);
        AeronMessageBusConfig embeddedConfig = new AeronMessageBusConfig(
                "aeron:ipc", streamId, true, null, 10, 1_000, Duration.ofMillis(1));
        CountDownLatch delivered = new CountDownLatch(20);
        List<Integer> sequences = new ArrayList<>();
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();

        try (AeronMessageBusBacking senderBacking = new AeronMessageBusBacking(embeddedConfig);
                AeronMessageBusBacking receiverBacking = new AeronMessageBusBacking(
                new AeronMessageBusConfig("aeron:ipc", streamId, false,
                    senderBacking.aeronDirectoryName(), 10, 1_000, Duration.ofMillis(1)))) {
            MessageBus sender = new MessageBus(new JacksonSerializer(), senderBacking);
            MessageBus receiver = new MessageBus(new JacksonSerializer(), receiverBacking);
            receiver.registerExternalType(ExternalPayload.class);
            receiver.subscribe(ExternalPayload.class, payload -> {
                try {
                    sequences.add(payload.sequence);
                    delivered.countDown();
                } catch (Throwable error) {
                    callbackFailure.compareAndSet(null, error);
                }
            });
            try (AeronMessageBusBacking.AeronSubscription subscription = receiverBacking.subscribe(receiver)) {
                assertTrue(awaitConnected(senderBacking),
                    () -> "Aeron receiver failure=" + receiverBacking.failure()
                        + ", directory=" + senderBacking.aeronDirectoryName());
                for (int sequence = 0; sequence < 20; sequence++) {
                    sender.publishExternal("events.aeron", new ExternalPayload("payload", sequence));
                }
                assertTrue(delivered.await(5, TimeUnit.SECONDS));
                assertEquals(null, callbackFailure.get());
                assertEquals(java.util.stream.IntStream.range(0, 20).boxed().toList(), sequences);
            }
        }
    }

    @Test
    void deliversAcrossAeronJavaProcesses() throws Exception {
        int streamId = 20_000 + Math.floorMod(UUID.randomUUID().hashCode(), 10_000);
        AeronMessageBusConfig config = new AeronMessageBusConfig(
                "aeron:ipc", streamId, true, null, 10, 10_000, Duration.ofMillis(1));
        try (AeronMessageBusBacking sender = new AeronMessageBusBacking(config)) {
            Process probe = new ProcessBuilder(
                    javaExecutable(), "-cp", System.getProperty("java.class.path"),
                    AeronProcessProbe.class.getName(), sender.aeronDirectoryName(), Integer.toString(streamId))
                    .redirectErrorStream(true)
                    .start();
            try {
                BufferedReader output = new BufferedReader(
                        new InputStreamReader(probe.getInputStream(), StandardCharsets.UTF_8));
                assertEquals("READY", output.readLine());
                sender.publish(new BusMessage("events.process", "TestPayload",
                        "hello".getBytes(StandardCharsets.UTF_8), SerializationEncoding.JSON));
                assertEquals("RECEIVED:events.process:hello", output.readLine());
            } finally {
                probe.destroyForcibly();
                probe.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private static boolean awaitConnected(AeronMessageBusBacking backing) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!backing.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        return backing.isConnected();
    }

    private static String javaExecutable() {
        String executable = System.getProperty("java.home") + "/bin/java";
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? executable + ".exe" : executable;
    }

    public static final class ExternalPayload {
        public String value;
        public int sequence;

        public ExternalPayload() {
        }

        private ExternalPayload(String value, int sequence) {
            this.value = value;
            this.sequence = sequence;
        }
    }
}
