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
public class SoAvsAoSBenchmark {

    private static final int SIZE = 1_000_000;
    private Point[] pointArray;
    private int[] xs;
    private int[] ys;

    private static class Point {
        int x;
        int y;
    }

    @Setup
    public void setup() {
        pointArray = new Point[SIZE];
        xs = new int[SIZE];
        ys = new int[SIZE];
        Random rand = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            Point p = new Point();
            p.x = rand.nextInt();
            p.y = rand.nextInt();
            pointArray[i] = p;
            xs[i] = p.x;
            ys[i] = p.y;
        }
    }

    @Benchmark
    public long testArrayOfStructs() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += pointArray[i].x;
            sum += pointArray[i].y;
        }
        return sum;
    }

    @Benchmark
    public long testStructOfArrays() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += xs[i];
            sum += ys[i];
        }
        return sum;
    }
}
