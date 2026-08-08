# Java Dispatch Optimization (JMH) Report

- Date: 2026-08-08
- Host/JDK: OpenJDK 21.0.11 (Linux)
- Benchmark class: `com.abc.trading.DispatchOptimizationJmhBenchmark`
- Command:
  - `java -jar target/data-1.0-SNAPSHOT-shaded.jar DispatchOptimizationJmhBenchmark -wi 3 -i 5 -f 1 -bm avgt -tu ns`

## Results

Baseline for speedup is `megamorphicInterface`.

| Method | Score (ns/op) | Error (±) | Speedup vs megamorphic |
|---|---:|---:|---:|
| monomorphicInterface | 0.424 | 0.014 | 11.61x |
| directConcrete | 0.427 | 0.028 | 11.52x |
| splitByTypeNoDispatch | 0.630 | 0.004 | 7.81x |
| taggedSwitch | 0.943 | 0.047 | 5.22x |
| manualInstanceof | 1.501 | 0.043 | 3.28x |
| sealedSwitch | 2.368 | 0.543 | 2.08x |
| megamorphicInterface | 4.921 | 0.083 | 1.00x |

## Results (Longer run + GC profiler)

- Command:
   - `java -jar target/data-1.0-SNAPSHOT-shaded.jar DispatchOptimizationJmhBenchmark -wi 5 -i 8 -f 2 -bm avgt -tu ns -prof gc`
- Raw output file:
   - `examples/java/dispatch_optimization_jmh_run2_gc.txt`

Baseline for speedup is `megamorphicInterface`.

| Method | Score (ns/op) | Error (±) | Speedup vs megamorphic |
|---|---:|---:|---:|
| monomorphicInterface | 0.416 | 0.001 | 12.57x |
| directConcrete | 0.419 | 0.003 | 12.48x |
| splitByTypeNoDispatch | 0.627 | 0.001 | 8.34x |
| taggedSwitch | 0.935 | 0.009 | 5.59x |
| manualInstanceof | 1.787 | 0.007 | 2.93x |
| sealedSwitch | 2.420 | 0.004 | 2.16x |
| megamorphicInterface | 5.228 | 0.286 | 1.00x |

GC profiler highlights (run-2):
- `gc.count`: approximately `0` for all methods.
- `gc.alloc.rate`: approximately `0.007 MB/sec` across methods.
- `gc.alloc.rate.norm`: effectively near-zero (`≈10⁻⁶` to `≈10⁻⁴ B/op`).

## Stability across runs

Delta computed as `(run2 - run1) / run1`.

| Method | Run 1 (ns/op) | Run 2 (ns/op) | Delta |
|---|---:|---:|---:|
| monomorphicInterface | 0.424 | 0.416 | -1.89% |
| directConcrete | 0.427 | 0.419 | -1.87% |
| splitByTypeNoDispatch | 0.630 | 0.627 | -0.48% |
| taggedSwitch | 0.943 | 0.935 | -0.85% |
| manualInstanceof | 1.501 | 1.787 | +19.05% |
| sealedSwitch | 2.368 | 2.420 | +2.20% |
| megamorphicInterface | 4.921 | 5.228 | +6.24% |

Interpretation:
- Top ordering is consistent across both runs: monomorphic/direct are fastest; megamorphic is slowest.
- Most methods are stable within ~2%, indicating good repeatability on this machine.
- `manualInstanceof` showed larger drift in the longer run, so treat it as workload-sensitive.

## What this shows

- Monomorphic interface calls are effectively as fast as direct concrete calls on this JVM/CPU.
- Megamorphic interface dispatch is much slower due to reduced inlining/devirtualization opportunities.
- Manual and structural dispatch (`instanceof`, `switch`) recover some performance, but still trail monomorphic/direct calls.
- Splitting work by concrete type (or otherwise homogenizing call sites) gives strong gains while keeping code explicit.

## Additional optimization techniques to explore

1. Keep call sites monomorphic in hot paths
   - Avoid storing mixed strategy types in one interface-typed list in critical loops.
   - Partition handlers by concrete type or route once, then execute typed loops.

2. Devirtualization-friendly hierarchy design
   - Use `final` classes/methods where polymorphic extension is not needed.
   - Prefer small, closed hierarchies (`sealed` + known implementations) for optimizer clarity.

3. Prevent accidental megamorphism
   - Avoid proxies/reflection-based wrappers in the hot message path.
   - Be careful with plugin-style dynamic loading around tight loops.

4. Keep methods tiny and inlineable
   - Keep hot `handle()` logic small and branch-light.
   - Move uncommon/slow paths out of line.

5. Data-oriented loop shaping
   - Use type-tagged arrays and batched processing to improve branch predictability and cache locality.
   - Pre-group by strategy type before processing large batches.

6. JIT/GC environment hygiene
   - Use long-running warmup before latency measurement.
   - Pin down JVM flags and GC settings for repeatable comparisons.

## Files

- Benchmark source: `examples/java/src/main/java/com/abc/trading/DispatchOptimizationJmhBenchmark.java`
- Lightweight non-JMH benchmark: `examples/java/src/main/java/com/abc/trading/DispatchOptimizationBenchmark.java`
- Lightweight benchmark report: `examples/java/dispatch_optimization_benchmark_report.md`
