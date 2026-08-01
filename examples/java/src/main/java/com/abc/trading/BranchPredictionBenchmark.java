package com.abc.trading;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;
import java.util.Random;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class BranchPredictionBenchmark {

    private static final int SIZE = 1_000_000;
    private int[] predictable;
    private int[] unpredictable;

    @Setup
    public void setup() {
        predictable = new int[SIZE];
        unpredictable = new int[SIZE];
        Random rand = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            predictable[i] = (i % 10 == 0) ? 1 : 0;
            unpredictable[i] = rand.nextInt(2);
        }
    }

    @Benchmark
    public long testPredictableBranch() {
        long sum = 0;
        for (int value : predictable) {
            if (value == 1) {
                sum += 1;
            }
        }
        return sum;
    }

    @Benchmark
    public long testUnpredictableBranch() {
        long sum = 0;
        for (int value : unpredictable) {
            if (value == 1) {
                sum += 1;
            }
        }
        return sum;
    }

    @Benchmark
    public long testBranchlessCount() {
        long sum = 0;
        for (int value : unpredictable) {
            sum += (value == 1) ? 1 : 0;
        }
        return sum;
    }
}
