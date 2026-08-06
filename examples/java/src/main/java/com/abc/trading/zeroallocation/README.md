# Zero-allocation stress benchmark

`ZeroAllocationStressBenchmark` compares two sustained message-processing paths:

- `zeroAllocationBatch`: reuses one direct `ByteBuffer` record and writes fields in place.
- `objectAllocationBatch`: creates one `AllocatedQuote` object per message and consumes it with JMH's `Blackhole`.

The benchmark uses batches of 100,000 and 1,000,000 messages. JMH performs warm-up and measurement iterations, and forks two JVMs to reduce startup/JIT effects.

## Reusable-buffer optimizations

The reusable path was optimized after the initial long run. It now avoids the
largest avoidable per-message overheads:

1. **No per-message Java object.** One direct `ByteBuffer` record is allocated
  at iteration setup and reused for the complete batch.
2. **No `String.getBytes()` call.** `QuoteFlyweight.set` is intentionally not
  used because `String.getBytes` creates a temporary byte array.
3. **No byte-at-a-time symbol loop.** The fixed-width, zero-padded `EURUSD`
  symbol is encoded as one compile-time `long`; the full 24-byte field is
  written using three `putLong` stores rather than 24 `put` calls.
4. **No per-message text view or `String`.** The timed path does not call
  `symbolView()` or `toString()`, both of which would introduce extra work and
  allocations.
5. **Primitive checksum.** The fields are read back into a primitive checksum
  returned from the JMH method, keeping the write/read work observable without
  allocating result objects.

The direct buffer's absolute-index access still performs native/direct-memory
access and safety checks. Avoiding allocation reduces GC pressure but does not
guarantee higher throughput for every workload.

### Optimized smoke result

After the three-long-store optimization, a focused JMH smoke run at 100,000
messages per batch (two one-second warm-ups and three one-second measurements)
gave:

| Path | Throughput | Allocation per batch | GC collections |
|---|---:|---:|---:|
| Object allocation | 2,588.395 batches/s | 4,000,002.725 B | 24 across 3 s |
| Reused direct buffer | 2,896.501 batches/s | 2.413 B | ~0 |

This is approximately **289.7 million messages/s** for the optimized reusable
path versus **258.8 million messages/s** for the object path in this short run.
It supersedes the initial throughput observation below only for the 100,000
message batch; repeat the long run for stable, publishable measurements and
for the 1,000,000-message batch.

## Build

From `abc-trading/examples/java`:

```text
mvn clean package
```

## Run throughput comparison

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar ZeroAllocationStressBenchmark
```

If the shaded jar has a different name, use the generated `*-shaded.jar` under `target/`.

## Measure allocation and GC activity

```text
java -jar target/data-1.0-SNAPSHOT-shaded.jar ZeroAllocationStressBenchmark -prof gc
```

Important JMH GC-profiler columns:

- `gc.alloc.rate.norm`: allocated bytes per benchmark operation. The zero-allocation path should be near zero; the object path should be substantially larger.
- `gc.count`: number of reported collections during measurement. A zero value does not prove that no GC can occur outside the measured process or during a longer run.
- `gc.time`: time spent in reported collections. This is not a complete pause-latency distribution.

## Long GC stress-run results

A sustained run was completed with one JMH fork, three five-second warm-up
iterations, and ten five-second measurement iterations for each of the four
scenario/batch-size combinations. This is 50 seconds of measured work per row
and approximately 4 minutes 21 seconds for the complete command.

**JDK:** OpenJDK 21.0.11.  
**Heap:** fixed at 2 GiB with `-Xms2g -Xmx2g`.

| Path | Messages per batch | Throughput (batches/s) | Effective messages/s | Allocation per batch | Allocation rate | GC collections across 50 s | JMH GC time across 50 s |
|---|---:|---:|---:|---:|---:|---:|---:|
| Object allocation | 100,000 | 2,628.927 ± 123.712 | ~262.9 million | 4,000,000.538 B | 10,027.868 MB/s | 409 | 673 ms |
| Object allocation | 1,000,000 | 266.695 ± 1.484 | ~266.7 million | 40,000,005.290 B | 10,173.070 MB/s | 415 | 681 ms |
| Reused direct buffer | 100,000 | 595.136 ± 1.437 | ~59.5 million | 2.351 B | 0.001 MB/s | ~0 | 0 ms reported |
| Reused direct buffer | 1,000,000 | 57.646 ± 0.321 | ~57.6 million | 24.216 B | 0.001 MB/s | ~0 | 0 ms reported |

### What the results show

- The object path allocated roughly **40 bytes per message**, producing about
  **10 GB/s** of allocation pressure and about **8 reported collections per
  second** in this run.
- The reusable-buffer path reduced allocation by approximately six orders of
  magnitude and did not trigger a collection during its 50-second measurement
  windows.
- The original reusable **direct** `ByteBuffer` path was about 4.4x slower in
  throughput than the object path. The result demonstrates reduced GC pressure,
  **not** an unconditional throughput win. Direct-buffer access, the original
  per-byte symbol clear/write loop, and bounds checks are part of that cost.
- The benchmark has since been optimized to replace the 24 per-byte symbol
  writes with three eight-byte stores. Re-run the long command before treating
  the table above as the result of the optimized implementation.
- `gc.time` is aggregate collector time reported by JMH; it is **not** a
  percentile pause metric. A claim about maximum, p99, or p99.9 pause latency
  requires the per-event GC/safepoint logs below.

## Observe pauses over a longer stress run

For a pause-oriented run, add a GC log and use a longer measurement duration:

```text
java -Xms2g -Xmx2g -Xlog:gc*,safepoint:file=zeroalloc-gc-%p.log:time,uptime,level,tags \
  -jar target/data-1.0-SNAPSHOT-shaded.jar ZeroAllocationStressBenchmark \
  -wi 3 -i 10 -w 5 -r 5 -f 1 -prof gc
```

`%p` makes the JVM process ID part of the filename, preventing JMH forks from
overwriting each other's logs. Inspect every generated log for individual
`Pause` and `Safepoint` entries. Compare the object-allocation and reusable
buffer paths under the same heap size, collector, JDK, machine load, and run
duration.

## Scope and limitation

The zero-allocation benchmark writes the fixed ASCII symbol directly into the buffer. It intentionally does not call `QuoteFlyweight.set`, because the current implementation of that method calls `String.getBytes`, which allocates a temporary byte array. It also avoids `symbolView().toString()`, which allocates a `String`.

This benchmark measures allocation pressure and throughput, not trading-system latency, message-bus routing, network I/O, or multi-threaded contention. Use a separate benchmark if the production path includes queues, subscribers, serialization, or multiple producer threads.
