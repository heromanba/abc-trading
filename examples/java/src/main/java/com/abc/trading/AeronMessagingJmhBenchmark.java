package com.abc.trading;

import com.abc.trading.msgbus.AeronMessageBusBacking;
import com.abc.trading.msgbus.AeronMessageBusConfig;
import com.abc.trading.msgbus.BusMessage;
import com.abc.trading.msgbus.SerializationEncoding;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public class AeronMessagingJmhBenchmark {
    private AeronMessageBusBacking backing;
    private AeronMessageBusBacking.AeronSubscription subscription;
    private BusMessage message;
    private final AtomicLong consumed = new AtomicLong();

    @Setup(Level.Trial)
    public void setup() throws InterruptedException {
        AeronMessageBusConfig config = new AeronMessageBusConfig(
                "aeron:ipc", 30_001, true, null, 10, 10_000, Duration.ofMillis(1));
        backing = new AeronMessageBusBacking(config);
        subscription = backing.subscribe(candidate -> consumed.incrementAndGet());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!backing.isConnected() && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        if (!backing.isConnected()) throw new IllegalStateException("Aeron publication did not connect");
        message = new BusMessage("data.trade.BINANCE.BTCUSDT", "TradeTick",
                new byte[]{1, 2, 3, 4}, SerializationEncoding.JSON);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (subscription != null) subscription.close();
        if (backing != null) backing.close();
    }

    @Benchmark
    public void aeronIpcHandoff() {
        backing.publish(message);
    }
}
