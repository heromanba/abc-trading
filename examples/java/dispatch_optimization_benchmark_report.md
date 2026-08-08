# Java Dispatch Optimization Benchmark

- Generated: 2026-08-08T22:25:12.435502985
- Warmup rounds: 4
- Measure rounds: 8
- Operations per round: 20000000

| Method | ns/op (lower is better) | ops/sec | Speedup vs megamorphic |
|---|---:|---:|---:|
| direct-final-concrete | 0.279 | 3580103449.77 | 16.88x |
| interface-monomorphic | 0.281 | 3562430412.93 | 16.80x |
| interface-megamorphic | 4.716 | 212034930.37 | 1.00x |
| manual-instanceof | 1.900 | 526244100.83 | 2.48x |

Notes:
- These numbers are from a single machine run and vary by CPU/JDK/flags.
- Monomorphic and direct concrete calls are expected to be close when JIT devirtualizes.
- Megamorphic interface dispatch is typically slower due to reduced inlining opportunities.
