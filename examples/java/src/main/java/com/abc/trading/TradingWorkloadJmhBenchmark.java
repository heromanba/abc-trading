package com.abc.trading;

import com.abc.trading.data.AggressorSide;
import com.abc.trading.data.Bar;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.OrderBookL3Snapshot;
import com.abc.trading.data.TradeTick;
import com.abc.trading.data.VenueOrder;
import com.abc.trading.execution.OrderIntent;
import com.abc.trading.execution.SignalDirection;
import com.abc.trading.system.NautilusKernel;
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
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public class TradingWorkloadJmhBenchmark {
    @Param({"1", "4"})
    private int ordersPerMarketUpdate;

    private NautilusKernel kernel;
    private long timestamp;
    private final AtomicLong orderSequence = new AtomicLong();

    @Setup(Level.Trial)
    public void setup() {
        kernel = new NautilusKernel();
        kernel.addVenue("XNAS");
        kernel.addInstrument("AAPL", "XNAS");
        kernel.start();
        kernel.runOrderBooksL3(new OrderBookL3Snapshot[] {
            new OrderBookL3Snapshot("AAPL", 999,
                List.of(new VenueOrder("bid-1", SignalDirection.BUY, 99.99, 100, 1)),
                List.of(new VenueOrder("ask-1", SignalDirection.SELL, 100.01, 100, 2)), 1)
        });
        timestamp = 1_000;
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        kernel.close();
    }

    @Benchmark
    public void marketDataAndOrderRoundTrip() {
        long currentTimestamp = timestamp++;
        double price = 100.0 + (currentTimestamp % 100) * 0.01;
        kernel.runMarketData(new MarketDataSnapshot[] {
                new MarketDataSnapshot("AAPL", currentTimestamp, price - 0.01, price + 0.01,
                        price, price, price, currentTimestamp)
        });
        kernel.runTradeTicks(new TradeTick[] {
                new TradeTick("AAPL", currentTimestamp, price, 100,
                        AggressorSide.BUYER, currentTimestamp)
        });
        for (int index = 0; index < ordersPerMarketUpdate; index++) {
            String orderId = "workload-" + orderSequence.incrementAndGet();
            kernel.bus().publish(new OrderIntent(
                    "workload", "AAPL", currentTimestamp, currentTimestamp,
                    orderId + "-corr", orderId, SignalDirection.BUY, 1, price,
                    kernel.portfolio().position("AAPL"), 0.0));
        }
    }

    @Benchmark
    public void barStrategyExecutionRoundTrip() {
        long currentTimestamp = timestamp++;
        double price = 100.0 + (currentTimestamp % 100) * 0.01;
        kernel.runBars(new Bar[] {new Bar("AAPL", currentTimestamp, price, currentTimestamp)});
        String orderId = "bar-workload-" + orderSequence.incrementAndGet();
        kernel.bus().publish(new OrderIntent(
                "workload", "AAPL", currentTimestamp, currentTimestamp,
                orderId + "-corr", orderId, SignalDirection.SELL, 1, price,
                kernel.portfolio().position("AAPL"), 0.0));
    }
}
