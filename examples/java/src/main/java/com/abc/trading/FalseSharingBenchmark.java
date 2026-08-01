package com.abc.trading;

import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/**
 * 
 * FalseSharingBenchmark
 * 
 * Why false sharing happens
 * False sharing is a hardware-level cache coherence problem, not a java-level bug.
 * 
 * What's happening in this benchmark
 * - NoPaddingState has two volatile long fields next to each other.
 * - On a typical CPU, a cache line is 64 bytes.
 * - Two adjacent long fields often fit into the same cache line.
 * 
 * So even though counter1 and counter2 are separate variables, they are stored in the same 64-byte block.
 * 
 * Why that hurts
 * - Each thread increments a different counter.
 * - The CPU cache coherence protocol works at cache-line granularity
 * - When thread A writes counter1, its core must obtain exclusive ownership of the whole cache line
 * - That invalidates thread B's copy of the same line, even though B is using counter2.
 * - When thread B writes next, it has to fetch the line again and invalidate A's copy.
 * - This causes "ping-pong" traffic between cores and stalls on every write.
 * 
 * Why it is "false" sharing
 * It's called false sharing because:
 * - The two threads are not logically sharing the same variable,
 * - But they are physically sharing the same cache line,
 * - So the cache coherence mechanism treats it as shared.
 * 
 * Why padding fixes it
 * - PaddedState inerts extra long fields between counter1 and counter2.
 * - That pushes counter2 onto a different cache line.
 * - Now threadA and threadB can update their counters independently.
 * - The coherence traffic disappears, and the benchmark becomes faster.
 * 
 * In short
 * False sharing happens because:
 * - CPU caches work in blocks (cache lines),
 * - coherence works on whole lines,
 * - adjacent independent variables can land on the same line,
 * - concurrent writes to different parts of that line cause unnecessary cache invalidations..
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class FalseSharingBenchmark {

    private static final int OPS_PER_INVOCATION = 10_000;

    @State(Scope.Group)
    public static class NoPaddingState {
        public volatile long counter1 = 0;
        public volatile long counter2 = 0;
    }

    @State(Scope.Group)
    public static class PaddedState {
        public volatile long counter1 = 0;
        // padding to separate hot fields onto different cache lines
        public long p1, p2, p3, p4, p5, p6, p7;
        public volatile long counter2 = 0;
    }

    /**
     * @GroupThreads(n) on a benchmark method tells JMH to run n concurrent threads that execute that methods
     * as part of the named group.
     * - In a grouped benchmark JMH runs the methods that share the same @Group("name") simultaneously; @GroupThreads
     * controls how many threads run each method in that group.
     * 
     * Effect on false sharing
     * - False sharing is a multi-core cache-coherence effect: writes to different fields that live on the same 64-byte
     * cache line cause the cache line to be bounced/invalidated between cores.
     * - If two write threads run on separate physical cores, coherence traffic between cores is required and the 
     * false-sharing penalty is visible (and usually larger).
     * - If the threads run on the same core (time-sliced), they share L1 and there is no cross-core coherence traffic,
     * so false sharing is reduced / hidden.
     *
     * 
     * @param state
     * @param affinity
     */
    @Benchmark
    @Group("noPadding")
    @GroupThreads(2)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementNoPadding1(NoPaddingState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter1++;
        }
    }

    @Benchmark
    @Group("noPadding")
    @GroupThreads(2)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementNoPadding2(NoPaddingState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter2++;
        }
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(2)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementPadded1(PaddedState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter1++;
        }
    }

    @Benchmark
    @Group("padded")
    @GroupThreads(2)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementPadded2(PaddedState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter2++;
        }
    }

    @Benchmark
    @Group("noPadding4")
    @GroupThreads(4)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementNoPadding1_4(NoPaddingState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter1++;
        }
    }

    @Benchmark
    @Group("noPadding4")
    @GroupThreads(4)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementNoPadding2_4(NoPaddingState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter2++;
        }
    }

    @Benchmark
    @Group("padded4")
    @GroupThreads(4)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementPadded1_4(PaddedState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter1++;
        }
    }

    @Benchmark
    @Group("padded4")
    @GroupThreads(4)
    @OperationsPerInvocation(OPS_PER_INVOCATION)
    public void incrementPadded2_4(PaddedState state, AffinityState affinity) {
        for (int i = 0; i < OPS_PER_INVOCATION; i++) {
            state.counter2++;
        }
    }

    /**
     * The @State(Scope.Thread) pins each JMH thread to a CPU core in its @Setup(Level.Trial)
     * Wired AffinityState into every benchmark method signature (JMH will instantiate it once per thread)
     * 
     * AffinityState
     */
    @State(Scope.Thread)
    public static class AffinityState {
        private final int cores = Runtime.getRuntime().availableProcessors();

        @Setup(Level.Trial)
        public void setup() {
            int cpu = (int) (Thread.currentThread().getId() % cores);
            ThreadAffinity.pinCurrentThreadToCpu(cpu);
        }
    }
    
        // Latest run (2026-07-31) with per-thread affinity (threads pinned to cores):
        // Summary (important lines):
        // 2-thread group:
        //   FalseSharingBenchmark.noPadding    avgt    3   93.104 ±  63.849  ns/op
        //   FalseSharingBenchmark.padded       avgt    3   38.560 ±  20.227  ns/op
        // 4-thread group:
        //   FalseSharingBenchmark.noPadding4   avgt    3  215.567 ± 137.988  ns/op
        //   FalseSharingBenchmark.padded4      avgt    3   91.548 ±  50.395  ns/op
        // Interpretation:
        //   - When threads are pinned to separate cores, the unpadded (noPadding) cases
        //     show significantly higher per-op latency due to cache-line ping-pong.
        //   - Padding reduces cross-core coherence traffic and lowers latency (~2.4x
        //     improvement observed in these runs).
        // Notes:
        //   - Affinity was applied via ThreadAffinity.pinCurrentThreadToCpu(cpu) where
        //     cpu = threadId % availableProcessors(). This is Linux-specific and
        //     requires `taskset` to be available.
        //   - Results can vary; increase forks/iterations to tighten confidence.

        // System topology and cache info collected on 2026-07-31 (added here):
        // Command: lscpu | grep -E '^CPU\(s\):|Thread\(s\) per core|Core\(s\) per socket|Socket\(s\):'
        // Output:
        // CPU(s):                                  6
        // Thread(s) per core:                      1
        // Core(s) per socket:                      6
        // Socket(s):                               1
        //
        // Command: for d in /sys/devices/system/cpu/cpu0/cache/index*; do echo "----"; echo -n "type: "; cat $d/type; echo -n "size: "; cat $d/size; echo -n "shared_cpu_list: "; cat $d/shared_cpu_list; done
        // Output for cpu0 cache indices:
        // ----
        // type: Data
        // size: 32K
        // shared_cpu_list: 0
        // ----
        // type: Instruction
        // size: 32K
        // shared_cpu_list: 0
        // ----
        // type: Unified
        // size: 256K
        // shared_cpu_list: 0
        // ----
        // type: Unified
        // size: 9216K
        // shared_cpu_list: 0-5
        //
        /**
         * Running on a single core (time-sliced) removes cross-core cache transfers but introduces
         * scheduling/context-switch overhead. results will change for that reason too.
         */
        // Single-core pinning result (taskset -c 0):
        // FalseSharingBenchmark.noPadding                         avgt    3  19.578 ± 2.488  ns/op
        // FalseSharingBenchmark.noPadding:incrementNoPadding1     avgt    3  19.542 ± 2.227  ns/op
        // FalseSharingBenchmark.noPadding:incrementNoPadding2     avgt    3  19.614 ± 2.820  ns/op
        
        // FalseSharingBenchmark.padded                            avgt    3  19.351 ± 0.964  ns/op
        // FalseSharingBenchmark.padded:incrementPadded1           avgt    3  19.347 ± 0.663  ns/op
        // FalseSharingBenchmark.padded:incrementPadded2           avgt    3  19.354 ± 1.364  ns/op

        // FalseSharingBenchmark.noPadding4                        avgt    3  38.888 ± 3.173  ns/op
        // FalseSharingBenchmark.noPadding4:incrementNoPadding1_4  avgt    3  38.884 ± 1.831  ns/op
        // FalseSharingBenchmark.noPadding4:incrementNoPadding2_4  avgt    3  38.892 ± 5.222  ns/op

        // FalseSharingBenchmark.padded4                           avgt    3  38.685 ± 2.649  ns/op
        // FalseSharingBenchmark.padded4:incrementPadded1_4        avgt    3  38.701 ± 2.758  ns/op
        // FalseSharingBenchmark.padded4:incrementPadded2_4        avgt    3  38.670 ± 3.895  ns/op
        //
        // Dual-core pinning result (taskset -c 0,1):
        // FalseSharingBenchmark.noPadding                         avgt    3   81.502 ± 11.710  ns/op
        // FalseSharingBenchmark.noPadding:incrementNoPadding1     avgt    3   81.410 ±  8.734  ns/op
        // FalseSharingBenchmark.noPadding:incrementNoPadding2     avgt    3   81.594 ± 15.368  ns/op

        // FalseSharingBenchmark.padded                            avgt    3   16.596 ± 17.729  ns/op
        // FalseSharingBenchmark.padded:incrementPadded1           avgt    3   16.768 ± 18.075  ns/op
        // FalseSharingBenchmark.padded:incrementPadded2           avgt    3   16.424 ± 17.384  ns/op

        // FalseSharingBenchmark.noPadding4                        avgt    3  166.336 ± 15.538  ns/op
        // FalseSharingBenchmark.noPadding4:incrementNoPadding1_4  avgt    3  166.077 ± 25.202  ns/op
        // FalseSharingBenchmark.noPadding4:incrementNoPadding2_4  avgt    3  166.595 ± 27.049  ns/op

        // FalseSharingBenchmark.padded4                           avgt    3   32.136 ± 47.632  ns/op
        // FalseSharingBenchmark.padded4:incrementPadded1_4        avgt    3   31.789 ± 45.716  ns/op
        // FalseSharingBenchmark.padded4:incrementPadded2_4        avgt    3   32.482 ± 49.592  ns/op
}
