# Comparable Transport Benchmark

Date: 2026-09-06

## Harness

`TransportMessagingJmhBenchmark` uses the same 64-byte binary payload for every transport. The first eight bytes contain a sequence number consumed by the live subscriber. The benchmark provides:

- single-producer throughput
- four-producer throughput
- end-to-end sample time for direct, Disruptor, Aeron same-JVM, and Redis
- Aeron child-JVM throughput using a shared Aeron IPC directory
- JMH GC allocation profiling
- JMH sampled stack profiling

Redis requires a local server at `127.0.0.1:6379`. The Aeron child-process benchmark launches `AeronBenchmarkProcess` from the same shaded jar.

## Commands

Build:

```text
mvn -pl java install -DskipTests
mvn -f examples/java/pom.xml package -DskipTests
```

Single-producer throughput:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar \
  'TransportMessagingJmhBenchmark.publishThroughput' \
  -p transport=direct,disruptor,aeron,aeronProcess,redis \
  -wi 1 -i 1 -f 1 -w 1s -r 1s -foe true
```

Four-producer throughput:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar \
  'TransportMessagingJmhBenchmark.publishThroughputMultiProducer' \
  -p transport=direct,disruptor,aeron,aeronProcess,redis \
  -t 4 -wi 1 -i 1 -f 1 -w 1s -r 1s -foe true
```

End-to-end sample time:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar \
  'TransportMessagingJmhBenchmark.publishEndToEnd' \
  -p transport=direct,disruptor,aeron,redis \
  -wi 1 -i 1 -f 1 -w 1s -r 1s -foe true
```

Allocation and CPU samples:

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar \
  'TransportMessagingJmhBenchmark.publishThroughput' \
  -p transport=direct,disruptor,aeron,redis \
  -wi 1 -i 1 -f 1 -w 1s -r 1s -foe true -prof gc

java -jar target/data-1.0-SNAPSHOT-shaded.jar \
  'TransportMessagingJmhBenchmark.publishThroughput' \
  -p transport=direct,disruptor,aeron,redis \
  -wi 1 -i 1 -f 1 -w 1s -r 1s -foe true -prof stack
```

## Short-run results

Environment: Linux, OpenJDK 21.0.12, JMH 1.37, 64-byte payload, one fork, one warmup and one one-second measurement iteration.

### Throughput

| Transport | One producer | Four producers |
|---|---:|---:|
| Direct typed dispatch | 22.01M ops/s | 13.73M ops/s |
| Disruptor same-JVM handoff | 7.66M ops/s | 7.85M ops/s |
| Aeron same-JVM IPC | 6.64M ops/s | 6.37M ops/s |
| Aeron child-JVM IPC | 6.77M ops/s | 6.99M ops/s |
| Redis Streams | 29.7K ops/s | 61.2K ops/s |

### End-to-end sample time

The method waits until the live consumer observes the sequence number. Results are sample-time means and p99 values from the short run.

| Transport | Mean | p50 | p99 |
|---|---:|---:|---:|
| Direct typed dispatch | 105 ns | 93 ns | 112 ns |
| Disruptor same-JVM | 12.48 us | 3.87 us | 24.65 us |
| Aeron same-JVM IPC | 1.11 us | 906 ns | 2.46 us |
| Redis Streams | 85.39 us | 75.65 us | 247.52 us |

Aeron child-JVM latency is intentionally excluded from this synchronous method. Its throughput path proves cross-process publication and consumption, but the harness does not add a return acknowledgement channel that would distort the transport comparison.

### Allocation profile

Single-producer `-prof gc` results for the 64-byte payload:

| Transport | Allocation |
|---|---:|
| Direct typed dispatch | 136.7 B/op |
| Disruptor same-JVM | 139.7 B/op |
| Aeron same-JVM IPC | 933.1 B/op |
| Redis Streams | 4,057.1 B/op |

The allocation figures include this harness's payload and envelope construction. They are useful for relative direction, not an isolated transport-object accounting.

### CPU profile observations

The JMH `stack` profiler showed:

- direct: samples concentrated in payload construction and the benchmark method
- Disruptor: batch processor, publication, sequencer, and parking/unparking
- Aeron: publication, string/envelope work, and Aeron log-buffer reads
- Redis: socket poll/write/read paths dominated the runnable samples

Use `-prof async` or `perfasm` on deployment hardware for deeper CPU attribution; the short stack run is qualitative.

## Recommendation

- Use direct typed dispatch for synchronous local runtime handlers.
- Use Disruptor for same-JVM feed-to-trading-thread handoff when bounded buffering and ordered consumption matter.
- Use Aeron for cross-process Java IPC or when sub-millisecond end-to-end handoff matters; its same-JVM result is lower-throughput than this Disruptor configuration but substantially lower latency in this short run.
- Use Redis Streams for durable external coordination, recovery, and process-to-process integration where tens of thousands of messages per second and tens-to-hundreds of microseconds latency are acceptable.

These are directional short-run measurements. Repeat with production payload sizes, subscriber counts, CPU pinning, wait strategies, Redis topology, and longer measurement windows before making a latency-sensitive deployment decision.
