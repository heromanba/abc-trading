package com.abc.trading;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Demonstrates that JVM safepoint checks can be avoided by keeping the loop body
 * side-effect free and by using a volatile/atomic sink that avoids the slow path.
 *
 * The benchmark uses a tiny helper method and a volatile sink so that the JIT can
 * optimize the work as a tight loop. If you add a call into a method that is not
 * trivially inlined, the generated code becomes much more likely to include safepoint
 * polling and other runtime checks.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class SafepointBypassBenchmark {

    private static final int LIMIT = 1_000_000;
    private static final AtomicLong COUNTER = new AtomicLong();

    private volatile long sink;

    @Setup
    public void setup() {
        sink = 0;
        COUNTER.set(0);
    }

    @Benchmark
    public long plainLoop(Blackhole blackhole) {
        long sum = 0;
        for (int i = 0; i < LIMIT; i++) {
            sum += (i * 31) ^ (i + 7);
        }
        blackhole.consume(sum);
        return sum;
    }

    @Benchmark
    public long loopWithBoundedSideEffects() {
        long sum = 0;
        for (int i = 0; i < LIMIT; i++) {
            sum += work(i);
        }
        sink = sum;
        COUNTER.addAndGet(sum);
        return sum;
    }

    private static long work(int i) {
        return (i * 31) ^ (i + 7);
    }
}
