# Aeron Messaging Benchmark

Date: 2026-09-05

## Scope

`AeronMessagingJmhBenchmark` measures publication into an Aeron IPC stream with a live subscription polling and consuming every message. The payload uses the same versioned binary envelope as `AeronMessageBusBacking`.

## Environment

- Linux
- OpenJDK 21.0.12
- Aeron 1.46.2
- JMH 1.37
- Embedded MediaDriver, `aeron:ipc`, one fork
- One warmup and two one-second measurement iterations

## Result

Command:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar 'AeronMessagingJmhBenchmark.*' -wi 1 -i 2 -f 1 -w 1s -r 1s -foe true
```

| Benchmark | Throughput |
|---|---:|
| Aeron IPC handoff with live consumer | 8.12M ops/s |

This is directional evidence from a short run. It includes binary envelope creation, Aeron publication, IPC handoff, subscription polling, and consumer-side delivery. Longer runs should use representative payload sizes and measure CPU, allocation, backpressure, and latency percentiles.

For the earlier same-host short baselines, direct typed dispatch reached about 65.1M ops/s, Disruptor handoff about 14.4M ops/s, and Redis-backed delivery was not used as a direct numeric comparison. These figures are not interchangeable: they use different transport semantics and payload paths.
