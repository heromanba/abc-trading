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

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the benefit of off-heap memory for hot-path data storage.
 *
 * Run with GC profiling to see allocation pressure drop:
 *   java -jar target/benchmarks.jar -prof gc com.abc.trading.OffHeapMemoryBenchmark
 *
 * The off-heap path allocates no Java objects inside the loop, so GC pressure is
 * typically much lower than the heap-allocation path.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class OffHeapMemoryBenchmark {

    private static final int ELEMENTS = 1_024;
    private static final int ITERATIONS = 100_000;

    private int[] heapBuffer;
    private ByteBuffer offHeapBuffer;

    @Setup
    public void setup() {
        heapBuffer = new int[ELEMENTS];
        offHeapBuffer = ByteBuffer.allocateDirect(ELEMENTS * Integer.BYTES);
    }

    @Benchmark
    public long heapAllocationPath() {
        long sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < ELEMENTS; j++) {
                heapBuffer[j] = (i + j) ^ (heapBuffer[j] >>> 1);
                sum += heapBuffer[j];
            }
        }
        return sum;
    }

    @Benchmark
    public long offHeapBufferPath() {
        long sum = 0;
        for (int i = 0; i < ITERATIONS; i++) {
            for (int j = 0; j < ELEMENTS; j++) {
                int value = i + j;
                offHeapBuffer.putInt(j * Integer.BYTES, value);
                sum += offHeapBuffer.getInt(j * Integer.BYTES);
            }
        }
        return sum;
    }
}
