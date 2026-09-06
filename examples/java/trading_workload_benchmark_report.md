# End-to-End Trading Workload Benchmark

Date: 2026-09-06

## Workload

`TradingWorkloadJmhBenchmark` drives the Java runtime through a simulated venue and AAPL instrument. Setup seeds an L3 book. Each market-data operation then processes:

1. a market-data snapshot and account mark update
2. a trade tick against the seeded L3 book
3. one or four market `OrderIntent` messages
4. simulated execution fills
5. portfolio position, PnL, and account-state updates

The benchmark also includes a bar-driven order round trip for comparison.

## Short-run results

Environment: Linux, OpenJDK 21.0.12, JMH 1.37, one fork, one warmup and one one-second measurement iteration.

| Workload | Child orders/update | Throughput | Allocation |
|---|---:|---:|---:|
| Bar plus order round trip | 1 | 320.6K ops/s | 3.87 KB/op |
| Bar plus order round trip | 4 | 318.8K ops/s | 3.87 KB/op |
| Market snapshot + trade tick + order/fill round trip | 1 | 1,885 ops/s | 77.2 KB/op |
| Market snapshot + trade tick + order/fill round trip | 4 | 928 ops/s | 157.1 KB/op |

These are directional short-run results. The market-data workload is intentionally much heavier because it exercises L3 matching, event publication, fills, and account transitions. Longer runs should use representative instruments, order sizes, fee models, latency, and strategy behavior.
