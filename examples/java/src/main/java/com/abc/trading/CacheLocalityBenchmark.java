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
public class CacheLocalityBenchmark {

    private static final int SIZE = 1_000_000;
    
    // Standard approach: Array of references to objects scattered in heap
    private static class Point { int x; }
    private Point[] objectArray;

    // Low latency approach: Contiguous primitive memory flat layout
    private int[] flatArray;

    @Setup
    public void setup() {
        objectArray = new Point[SIZE];
        flatArray = new int[SIZE];
        Random rand = new Random();
        
        for (int i = 0; i < SIZE; i++) {
            objectArray[i] = new Point();
            int val = rand.nextInt();
            objectArray[i].x = val;
            flatArray[i] = val;
        }
    }

    @Benchmark
    public long testStandardObjectArray() {
        long sum = 0;
        // Causes CPU cache misses due to pointer chasing
        for (int i = 0; i < SIZE; i++) {
            sum += objectArray[i].x; 
        }
        return sum;
    }

    @Benchmark
    public long testFlatPrimitiveArray() {
        long sum = 0;
        // Perfect cache utilization via sequential hardware prefetching
        for (int i = 0; i < SIZE; i++) {
            sum += flatArray[i];
        }
        return sum;
    }
}
// Benchmark                                       Mode  Cnt        Score        Error  Units
// CacheLocalityBenchmark.testFlatPrimitiveArray   avgt    3   202211.496 ±  93302.018  ns/op
// CacheLocalityBenchmark.testStandardObjectArray  avgt    3  1307562.652 ± 561873.683  ns/op