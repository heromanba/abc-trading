package com.abc.trading.cachemiss;

import java.util.Locale;
import java.util.Random;

/**
 * Small cache-miss example for showing the cost of poor spatial locality.
 *
 * <p>Two workloads operate on the same data:
 * <ul>
 *   <li>{@code sequentialScan()} walks a contiguous int array in order.</li>
 *   <li>{@code randomPointerChase()} jumps through the array using a randomized next-index table.</li>
 * </ul>
 *
 * <p>The random walk defeats hardware prefetching and produces many more cache misses.
 * The sequential walk benefits from cache lines and prefetchers.
 */
public final class CacheMissExample {

    private static final int SIZE = 8_000_000;
    private static final int ITERATIONS = 5;

    private final int[] values = new int[SIZE];
    private final int[] nextIndex = new int[SIZE];

    public static void main(String[] args) {
        CacheMissExample example = new CacheMissExample();
        example.setup();
        example.run();
    }

    private void setup() {
        Random random = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            values[i] = i;
            nextIndex[i] = i;
        }

        // Fisher-Yates shuffle to create a randomized next-hop table.
        for (int i = SIZE - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = nextIndex[i];
            nextIndex[i] = nextIndex[j];
            nextIndex[j] = tmp;
        }
    }

    private void run() {
        // Warm up the JIT a little before timing.
        for (int i = 0; i < 3; i++) {
            sequentialScan();
            randomPointerChase();
        }

        long seqTotal = 0;
        long rndTotal = 0;
        long seqChecksum = 0;
        long rndChecksum = 0;

        for (int i = 0; i < ITERATIONS; i++) {
            TimedResult seq = timed(this::sequentialScan);
            TimedResult rnd = timed(this::randomPointerChase);
            seqTotal += seq.nanos;
            rndTotal += rnd.nanos;
            seqChecksum ^= seq.checksum;
            rndChecksum ^= rnd.checksum;
        }

        double seqAvgMs = seqTotal / (double) ITERATIONS / 1_000_000.0;
        double rndAvgMs = rndTotal / (double) ITERATIONS / 1_000_000.0;

        System.out.println("=== Cache Miss Example ===");
        System.out.printf(Locale.ROOT, "Sequential scan avg: %.3f ms%n", seqAvgMs);
        System.out.printf(Locale.ROOT, "Random pointer chase avg: %.3f ms%n", rndAvgMs);
        System.out.printf(Locale.ROOT, "Slowdown: %.2fx%n", rndAvgMs / seqAvgMs);
        System.out.printf(Locale.ROOT, "Checksums: seq=%d rnd=%d%n", seqChecksum, rndChecksum);
    }

    private long sequentialScan() {
        long sum = 0;
        for (int i = 0; i < SIZE; i++) {
            sum += values[i];
        }
        return sum;
    }

    private long randomPointerChase() {
        long sum = 0;
        int index = 0;
        for (int i = 0; i < SIZE; i++) {
            index = nextIndex[index];
            sum += values[index];
        }
        return sum;
    }

    private TimedResult timed(LongSupplierWithResult fn) {
        long start = System.nanoTime();
        long checksum = fn.getAsLong();
        long nanos = System.nanoTime() - start;
        return new TimedResult(nanos, checksum);
    }

    @FunctionalInterface
    private interface LongSupplierWithResult {
        long getAsLong();
    }

    private record TimedResult(long nanos, long checksum) {
    }
}