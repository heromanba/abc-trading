# Java Messaging Benchmark

Date: 2026-09-02

## Scope

`DisruptorMessagingJmhBenchmark` compares the existing synchronous typed `MessageBus` with the bounded `DisruptorMessageBus` feed-ingress handoff. The direct path invokes its handler inline; the Disruptor path publishes to a multi-producer, single-consumer ring and routes on the consumer thread. Both paths increment an `AtomicLong` so delivery remains observable.

This is a throughput comparison, not a claim that the two paths have identical latency or scheduling semantics.

## Environment

- Linux
- OpenJDK 21.0.12
- JMH 1.37
- LMAX Disruptor 3.4.4
- One fork, one warmup iteration, two one-second measurement iterations

## Baseline

Command:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar 'DisruptorMessagingJmhBenchmark.*' -wi 1 -i 2 -f 1 -t 1 -w 1s -r 1s -foe true
```

| Benchmark | Threads | Throughput |
|---|---:|---:|
| direct typed dispatch | 1 | 65.1M ops/s |
| Disruptor handoff | 1 | 14.4M ops/s |

The Disruptor result includes publication, cross-thread handoff, event materialization, routing, and handler execution. Direct dispatch remains the correctness and lowest-overhead local path.

## Four-producer baseline

The multi-producer methods were run separately with `-t 4`:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar 'DisruptorMessagingJmhBenchmark.*MultiProducer' -wi 1 -i 2 -f 1 -t 4 -w 1s -r 1s -foe true
```

| Benchmark | Threads | Throughput |
|---|---:|---:|
| direct typed dispatch | 4 | 27.3M ops/s |
| Disruptor handoff | 4 | 21.6M ops/s |

## Ring capacity sweep

The Disruptor methods were run with `disruptorBufferSize=1024,4096,65536`, one fork, one warmup, and two one-second measurements.

| Ring capacity | Single producer | Four producers |
|---:|---:|---:|
| 1,024 | 8.5M ops/s | 10.7M ops/s |
| 4,096 | 8.8M ops/s | 15.1M ops/s |
| 65,536 | 9.0M ops/s | 19.4M ops/s |

The four-producer run is the useful tuning signal: 4K improves substantially over 1K, while 64K provides additional burst capacity at a higher preallocation cost. The default was changed from 1K to 4K as the balanced production setting. The explicit constructor remains available for deployments that need the larger burst buffer.

These short runs are directional evidence, not a production capacity guarantee. Longer runs should be repeated on the deployment hardware with representative payload sizes, subscriber counts, feed rates, and allocation/CPU profilers before selecting a latency-sensitive wait strategy.

## Rust comparison caveat

The Rust `crates/common/benches/msgbus.rs` benchmark measures typed versus `Any`-based local routing and handler overhead. It is not numerically comparable to this Java result because this benchmark includes an asynchronous Disruptor handoff and uses different runtimes, payloads, subscriber setup, and hardware conditions.
