package com.abc.trading;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class AllocationChurnBenchmark {

    private static final int ITERATIONS = 100_000;
    private static final int BUFFER_SIZE = 256;
    private byte[] reusedBuffer;

    @Setup
    public void setup() {
        reusedBuffer = new byte[BUFFER_SIZE];
    }

    @Benchmark
    public int testAllocateNewBuffer() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] buffer = new byte[BUFFER_SIZE];
            for (int j = 0; j < BUFFER_SIZE; j++) {
                buffer[j] = (byte) j;
                sum += buffer[j];
            }
        }
        return sum;
    }

    @Benchmark
    public int testReuseBuffer() {
        int sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < BUFFER_SIZE; j++) {
                reusedBuffer[j] = (byte) j;
                sum += reusedBuffer[j];
            }
        }
        return sum;
    }
}
