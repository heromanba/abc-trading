package com.abc.trading;

import com.abc.trading.msgbus.AeronMessageBusBacking;
import com.abc.trading.msgbus.AeronMessageBusConfig;
import com.abc.trading.msgbus.BusMessage;
import com.abc.trading.msgbus.DisruptorMessageBus;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.msgbus.MessageHandler;
import com.abc.trading.msgbus.MessageBusRouter;
import com.abc.trading.msgbus.RedisMessageBusBacking;
import com.abc.trading.msgbus.RedisMessageBusConfig;
import com.abc.trading.msgbus.SerializationEncoding;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public class TransportMessagingJmhBenchmark {
    private static final String TOPIC = "data.trade.BINANCE.BTCUSDT";
    private static final String TYPE = "TradeTick";

    @Param({"direct", "disruptor", "aeron", "aeronProcess", "redis"})
    private String transport;

    @Param({"64", "256", "1024"})
    private int payloadSize;

    private MessageBus directBus;
    private DisruptorMessageBus disruptorBus;
    private AeronMessageBusBacking aeronBacking;
    private RedisMessageBusBacking redisBacking;
    private AutoCloseable subscription;
    private Process aeronProcess;
    private final DeliveryTracker tracker = new DeliveryTracker();
    private final AtomicLong nextSequence = new AtomicLong();
    private String redisStream;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        tracker.reset();
        switch (transport) {
            case "direct" -> setupDirect();
            case "disruptor" -> setupDisruptor();
            case "aeron" -> setupAeron();
            case "aeronProcess" -> setupAeronProcess();
            case "redis" -> setupRedis();
            default -> throw new IllegalArgumentException("Unknown transport: " + transport);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (subscription != null) subscription.close();
        if (aeronProcess != null) {
            aeronProcess.destroyForcibly();
            aeronProcess.waitFor(5, TimeUnit.SECONDS);
        }
        if (disruptorBus != null) disruptorBus.close();
        if (aeronBacking != null) aeronBacking.close();
        if (redisBacking != null) redisBacking.close();
    }

    @Benchmark
    public void publishThroughput() {
        publish(payload(nextSequence.getAndIncrement()));
    }

    @Benchmark
    @Threads(4)
    public void publishThroughputMultiProducer() {
        publish(payload(nextSequence.getAndIncrement()));
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    public void publishEndToEnd() throws InterruptedException {
        if (transport.equals("aeronProcess")) {
            throw new IllegalStateException("Cross-process Aeron benchmark is throughput-only");
        }
        long sequence = nextSequence.getAndIncrement();
        publish(payload(sequence));
        if (!tracker.await(sequence, 5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + transport + " delivery");
        }
    }

    private byte[] payload(long sequence) {
        byte[] payload = new byte[payloadSize + Long.BYTES];
        ByteBuffer.wrap(payload).putLong(sequence);
        for (int index = Long.BYTES; index < payload.length; index++) {
            payload[index] = (byte) (index * 31);
        }
        return payload;
    }

    private void publish(byte[] payload) {
        switch (transport) {
            case "direct" -> directBus.publish(payload);
            case "disruptor" -> disruptorBus.publish(TOPIC, payload);
            case "aeron", "aeronProcess" -> aeronBacking.publish(
                    new BusMessage(TOPIC, TYPE, payload, SerializationEncoding.JSON));
            case "redis" -> redisBacking.publish(
                    new BusMessage(TOPIC, TYPE, payload, SerializationEncoding.JSON));
            default -> throw new IllegalStateException("Unknown transport: " + transport);
        }
    }

    private void setupDirect() {
        directBus = new MessageBus(null);
        directBus.subscribe(byte[].class, tracker::accept);
    }

    private void setupDisruptor() {
        MessageBusRouter router = new ExactTopicRouter(event -> tracker.accept((byte[]) event.payload()));
        disruptorBus = new DisruptorMessageBus(router, 4096);
    }

    private void setupAeron() throws InterruptedException {
        aeronBacking = new AeronMessageBusBacking(new AeronMessageBusConfig(
                "aeron:ipc", streamId(), true, null, 10, 10_000, Duration.ofMillis(1)));
        subscription = aeronBacking.subscribe(message -> tracker.accept(message.getPayload()));
        waitForConnection(aeronBacking);
    }

    private void setupAeronProcess() throws Exception {
        int streamId = streamId();
        aeronBacking = new AeronMessageBusBacking(new AeronMessageBusConfig(
                "aeron:ipc", streamId, true, null, 10, 10_000, Duration.ofMillis(1)));
        aeronProcess = new ProcessBuilder(
                javaExecutable(), "-cp", System.getProperty("java.class.path"),
                AeronBenchmarkProcess.class.getName(), aeronBacking.aeronDirectoryName(), Integer.toString(streamId))
                .redirectErrorStream(true)
                .start();
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(aeronProcess.getInputStream(), StandardCharsets.UTF_8))) {
            if (!"READY".equals(output.readLine())) {
                throw new IllegalStateException("Aeron child process did not become ready");
            }
        }
        waitForConnection(aeronBacking);
    }

    private void setupRedis() throws InterruptedException {
        redisStream = "abc-transport-benchmark:" + UUID.randomUUID();
        RedisMessageBusConfig config = new RedisMessageBusConfig(
                "127.0.0.1", 6379, null, null, false, Duration.ofSeconds(2),
                Duration.ofSeconds(2), redisStream, 100, Duration.ofMillis(100),
                3, Duration.ofMillis(25));
        redisBacking = new RedisMessageBusBacking(config);
        subscription = redisBacking.subscribe("benchmark-group", "consumer-" + UUID.randomUUID(),
                message -> tracker.accept(message.getPayload()));
        Thread.sleep(100);
    }

    private void waitForConnection(AeronMessageBusBacking backing) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!backing.isConnected() && System.nanoTime() < deadline) Thread.sleep(1);
        if (!backing.isConnected()) throw new IllegalStateException("Aeron publication did not connect");
    }

    private static int streamId() {
        return 40_000 + Math.floorMod(UUID.randomUUID().hashCode(), 10_000);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("java.home") + "/bin/java";
        return System.getProperty("os.name").toLowerCase().contains("win")
                ? executable + ".exe" : executable;
    }

    private static final class ExactTopicRouter implements MessageBusRouter {
        private final MessageHandler handler;

        private ExactTopicRouter(MessageHandler handler) {
            this.handler = handler;
        }

        @Override
        public void subscribe(String topicPattern, MessageHandler handler) {
            throw new UnsupportedOperationException("benchmark router is fixed");
        }

        @Override
        public void unsubscribe(String topicPattern, MessageHandler handler) {
            throw new UnsupportedOperationException("benchmark router is fixed");
        }

        @Override
        public List<MessageHandler> route(String topic) {
            return TOPIC.equals(topic) ? List.of(handler) : List.of();
        }
    }

    private static final class DeliveryTracker {
        private final AtomicLong delivered = new AtomicLong(-1);

        private void reset() {
            delivered.set(-1);
        }

        private void accept(byte[] payload) {
            delivered.accumulateAndGet(ByteBuffer.wrap(payload).getLong(), Math::max);
        }

        private boolean await(long sequence, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            while (delivered.get() < sequence && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            return delivered.get() >= sequence;
        }
    }
}
