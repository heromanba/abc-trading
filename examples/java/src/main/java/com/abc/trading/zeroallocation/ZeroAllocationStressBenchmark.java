package com.abc.trading.zeroallocation;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Sustained allocation-pressure comparison for a reusable byte-buffer record
 * versus allocating a normal Java object for every message.
 *
 * <p>Run with JMH's GC profiler to compare allocation rate and collection
 * counts:
 * {@code java -jar target/...-shaded.jar ZeroAllocationStressBenchmark -prof gc}
 *
 * <p>This benchmark deliberately does not call {@code QuoteFlyweight.set}
 * because that method currently creates a temporary byte array through
 * {@code String.getBytes}. The zero-allocation path below writes fixed ASCII
 * bytes directly into the already allocated buffer.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgsAppend = {
        "-Xms2g",
        "-Xmx2g",
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+DebugNonSafepoints"
})
public class ZeroAllocationStressBenchmark {

    private static final int SYMBOL_BYTES = 24;
    private static final int RECORD_SIZE = SYMBOL_BYTES + Double.BYTES + Double.BYTES + Long.BYTES;
    // `EURUSD` followed by two NUL bytes in big-endian byte order. The
    // remaining 16 symbol bytes are cleared with two long stores.
    private static final long EURUSD_PREFIX = 0x4555_5255_5344_0000L;

    @Param({"100000", "1000000"})
    private int messages;

    private ByteBuffer storage;
    private long sequence;

    @Setup(Level.Iteration)
    public void setup() {
        storage = ByteBuffer.allocateDirect(RECORD_SIZE);
        sequence = 0;
    }

    @TearDown(Level.Iteration)
    public void tearDown() {
        storage = null;
    }

    /**
     * Reuses one off-heap record and one flyweight view for the complete batch.
     * No Java object or temporary byte array is created in the timed loop.
     */
    @Benchmark
    public long zeroAllocationBatch() {
        long checksum = 0;
        for (int i = 0; i < messages; i++) {
            writeReusableQuote(i);
            checksum += storage.getLong(0);
            checksum += storage.getLong(Long.BYTES);
            checksum += storage.getLong(Long.BYTES * 2);
            checksum += Double.doubleToRawLongBits(storage.getDouble(SYMBOL_BYTES + Double.BYTES));
            checksum += storage.getLong(SYMBOL_BYTES + Double.BYTES + Double.BYTES);
            checksum += Double.doubleToRawLongBits(storage.getDouble(SYMBOL_BYTES));
        }
        return checksum ^ sequence;
    }

    /**
     * Allocates a Quote object for every message. The Blackhole consumes each
     * object so escape analysis cannot remove the allocation as dead code.
     */
    @Benchmark
    public long objectAllocationBatch(Blackhole blackhole) {
        long checksum = 0;
        for (int i = 0; i < messages; i++) {
            AllocatedQuote quote = new AllocatedQuote(
                    "EURUSD",
                    1.2345 + (i & 15) * 0.0001,
                    1.2346 + (i & 15) * 0.0001,
                    sequence++);
            blackhole.consume(quote);
            checksum += quote.timestamp;
            checksum += Double.doubleToRawLongBits(quote.bid);
                checksum += Double.doubleToRawLongBits(quote.ask);
                checksum += quote.symbol.hashCode();
        }
        return checksum;
    }

    private void writeReusableQuote(int index) {
        int base = 0;
        storage.putLong(base, EURUSD_PREFIX);
        storage.putLong(base + Long.BYTES, 0L);
        storage.putLong(base + Long.BYTES * 2, 0L);
        storage.putDouble(base + SYMBOL_BYTES, 1.2345 + (index & 15) * 0.0001);
        storage.putDouble(base + SYMBOL_BYTES + Double.BYTES,
                1.2346 + (index & 15) * 0.0001);
        storage.putLong(base + SYMBOL_BYTES + Double.BYTES + Double.BYTES, sequence++);
    }

    private static final class AllocatedQuote {
        private final String symbol;
        private final double bid;
        private final double ask;
        private final long timestamp;

        private AllocatedQuote(String symbol, double bid, double ask, long timestamp) {
            this.symbol = symbol;
            this.bid = bid;
            this.ask = ask;
            this.timestamp = timestamp;
        }
    }
}
