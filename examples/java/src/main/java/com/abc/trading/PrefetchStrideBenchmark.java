package com.abc.trading;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class PrefetchStrideBenchmark {

    private static final int SIZE = 8_000_000;
    private int[] data;

    @Param({"1", "8", "16", "64", "256"})
    private int stride;

    @Setup
    public void setup() {
        data = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            data[i] = i;
        }
    }

    @Benchmark
    public long testStrideAccess() {
        long sum = 0;
        for (int i = 0; i < SIZE; i += stride) {
            sum += data[i];
        }
        return sum;
    }

    @Benchmark
    public long testLookaheadAccess() {
        long sum = 0;
        int lookahead = Math.min(stride, 16);
        for (int i = 0; i + lookahead < SIZE; i += stride) {
            sum += data[i];
            sum += data[i + lookahead];
        }
        return sum;
    }
}
