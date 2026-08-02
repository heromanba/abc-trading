package com.abc.trading;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple benchmark comparing a basic ring buffer (SPSC) vs LinkedBlockingQueue.
 *
 * Benefits of a ring buffer:
 * - Lower GC pressure (preallocated array)
 * - Lower latency and higher throughput for passing objects/messages
 * - Cache-friendly contiguous memory layout
 * - Can be implemented lock-free for SPSC or with efficient coordination for multiple producers/consumers
 *
 * Alternatives:
 * - Blocking queues (LinkedBlockingQueue, ArrayBlockingQueue)
 * - ConcurrentLinkedQueue (lock-free but not bounded)
 * - Disruptor (LMAX Disruptor) — highly optimized ring-buffer-based framework
 * - Message-passing using sockets or off-heap structures
 */
public class RingBufferBenchmark {

    static final int MESSAGE_COUNT = 5_000_000;
    static final int RING_SIZE = 1 << 16; // must be power of two

    // Simple SPSC ring buffer using long[] and volatile indexes
    static class SimpleRingBuffer {
        private final long[] buffer;
        private final int mask;
        private volatile long head = 0; // next to read
        private volatile long tail = 0; // next to write

        SimpleRingBuffer(int size) {
            if (Integer.bitCount(size) != 1) throw new IllegalArgumentException("size must be power of two");
            buffer = new long[size];
            mask = size - 1;
        }

        // offer for producer; busy-spin if full
        void offer(long value) {
            long t;
            while (true) {
                t = tail;
                long h = head;
                if (t - h < buffer.length) break; // has space
                // busy spin
                Thread.onSpinWait();
            }
            buffer[(int)(t & mask)] = value;
            // publish by advancing tail
            tail = t + 1;
        }

        // poll for consumer; busy-spin if empty
        long poll() {
            long h;
            while (true) {
                h = head;
                long t = tail;
                if (h < t) break; // has element
                Thread.onSpinWait();
            }
            long value = buffer[(int)(h & mask)];
            head = h + 1;
            return value;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running benchmark with MESSAGE_COUNT=" + MESSAGE_COUNT);

        // Warmup
        runRingBuffer(RING_SIZE, 100_000);
        runQueue(100_000);

        // Timed runs
        long t1 = runRingBuffer(RING_SIZE, MESSAGE_COUNT);
        long t2 = runQueue(MESSAGE_COUNT);

        System.out.printf("RingBuffer: %.3f ms, throughput=%.3f ops/sec\n", t1 / 1_000_000.0, MESSAGE_COUNT / (t1 / 1_000_000_000.0));
        System.out.printf("LinkedBlockingQueue: %.3f ms, throughput=%.3f ops/sec\n", t2 / 1_000_000.0, MESSAGE_COUNT / (t2 / 1_000_000_000.0));
    }

    static long runRingBuffer(int size, int count) throws Exception {
        final SimpleRingBuffer rb = new SimpleRingBuffer(size);
        Thread consumer = new Thread(() -> {
            long sum = 0;
            for (int i = 0; i < count; i++) {
                sum += rb.poll();
            }
            // consume to avoid optimizing away
            if (sum == Long.MIN_VALUE) System.out.println();
        });

        long start = System.nanoTime();
        consumer.start();

        for (int i = 0; i < count; i++) {
            rb.offer(i);
        }

        consumer.join();
        long end = System.nanoTime();
        return end - start;
    }

    static long runQueue(int count) throws Exception {
        final LinkedBlockingQueue<Long> q = new LinkedBlockingQueue<>();
        Thread consumer = new Thread(() -> {
            long sum = 0;
            try {
                for (int i = 0; i < count; i++) {
                    sum += q.take();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (sum == Long.MIN_VALUE) System.out.println();
        });

        long start = System.nanoTime();
        consumer.start();

        for (int i = 0; i < count; i++) {
            q.put((long)i);
        }

        consumer.join();
        long end = System.nanoTime();
        return end - start;
    }

    /*
     * Benchmark results appended here by automated run.
     *
     * Run: 2026-08-02
     * Output:
     * Running benchmark with MESSAGE_COUNT=5000000
     * RingBuffer: 311.459 ms, throughput=16053469.767 ops/sec
     * LinkedBlockingQueue: 569.992 ms, throughput=8772058.220 ops/sec
     */
}
