# NautilusTrader In-Memory Message-Bus Benchmark Report

**Benchmark date:** 2026-08-06  
**Purpose:** Establish a reproducible Rust baseline for implementing equivalent Java scenarios.

## Executive summary

The benchmark was executed locally against NautilusTrader's Rust message-bus benchmark. It measures the in-process topic routers and handler dispatch paths without Redis, sockets, serialization, persistence, or cross-process communication.

The main steady-state single-topic publish result was approximately **23 ns per published message** for both the Any-based and typed routers. The one-million-message batch measured approximately **22.7 ms** for the Any-based path and **25.2 ms** for the typed path in this run.

These are microbenchmark results for the existing benchmark harness, not an end-to-end trading-system latency measurement.

## Machine and environment

### Hardware

| Property | Value |
|---|---|
| CPU | Intel(R) Core(TM) i5-9400 CPU @ 2.90 GHz |
| CPU family/model | Family 6, Model 158, Stepping 10 |
| Sockets | 1 |
| Physical cores | 6 |
| Logical CPUs available | 6 |
| Threads per core | 1 reported/available |
| Maximum CPU frequency | 4.1 GHz |
| Minimum CPU frequency | 800 MHz |
| L1 data cache | 192 KiB total, 6 instances |
| L1 instruction cache | 192 KiB total, 6 instances |
| L2 cache | 1.5 MiB total, 6 instances |
| L3 cache | 9 MiB |
| NUMA nodes | 1 |
| Architecture | x86_64 |
| SIMD capabilities relevant to this CPU | AVX, AVX2, FMA and SSE4.2 reported |
| Virtualization | VT-x reported |
| Total RAM | 15 GiB |
| Swap | 975 MiB; approximately 975 MiB used when collected |

### Operating system and toolchain

| Property | Value |
|---|---|
| OS kernel | Linux 6.8.0-137-generic |
| Distribution context | Ubuntu kernel/build environment |
| Rust | `rustc 1.97.1 (8bab26f4f 2026-07-14)` |
| Cargo | `cargo 1.97.1 (c980f4866 2026-06-30)` |
| Rust toolchain source | `rust-toolchain.toml` pins `1.97.1` |
| NautilusTrader revision | `370a3a2a8f` |
| CPU governor | `powersave` |
| Benchmark profile | Cargo `bench` profile: optimized, inherits release, full debug symbols, no LTO |
| LTO benchmark profile available | `bench-lto`: fat LTO, one codegen unit |

The benchmark was run with:

```text
cargo bench -p nautilus-common --bench msgbus -- --quick
```

The first run also compiled the required dependencies. Compilation took approximately 3 minutes 55 seconds; compilation time is not part of the reported measurements.

## What was measured

The benchmark source is [crates/common/benches/msgbus.rs](../../../nautilus_trader/crates/common/benches/msgbus.rs).

The test uses a default `QuoteTick` and handlers that perform a small `AtomicU64::fetch_add` operation. That operation prevents the compiler from removing the handler work, but it means the results represent **routing plus minimal handler work**, not routing alone.

The benchmark is isolated to the in-memory routing layer:

- No Redis or other message-bus backing service.
- No network I/O.
- No serialization or deserialization.
- No disk I/O.
- No Java/Python boundary.
- No scheduler or cross-thread queue in the timed publish path.
- Criterion setup and warm-up happen outside the steady-state timing loop where applicable.

## Result interpretation

### Time

A result such as `22.96 ns` means one timed invocation of the benchmarked operation took about 22.96 nanoseconds on average under the measured conditions.

For a batch benchmark, such as `22.74 ms` for 1,000,000 publishes, the approximate average per message is:

$$
\frac{22.74\ \mathrm{ms}}{1{,}000{,}000}
\approx 22.74\ \mathrm{ns/message}
$$

### Throughput

Criterion reports throughput in elements per second. For example, `43.5 Melem/s` means approximately 43.5 million benchmark elements per second.

For one operation per element:

$$
\mathrm{throughput} \approx \frac{1}{\mathrm{latency}}
$$

For multiple subscribers, one published message invokes multiple handlers. The benchmark declares throughput as the number of subscriber-handler elements, so the reported throughput is not always the same as published-message throughput.

### Confidence interval

Criterion displays a three-value range such as:

```text
[22.940 ns 22.964 ns 22.970 ns]
```

This is the measured lower estimate, point estimate, and upper estimate for the benchmark's confidence range. It is not a guarantee for every machine or every future run.

## Detailed results

The values below are the Criterion output from the executed `--quick` run. The middle value is used as the convenient point estimate.

### Handler dispatch overhead

These tests call a handler directly rather than performing topic lookup.

| Scenario | Criterion time | Approximate meaning |
|---|---:|---|
| Any-based handler | 4.3737–4.3976 ns | Dynamic `Any` path with downcast-compatible handler interface |
| Typed handler | 0.51747–0.52056 ns | Direct statically typed handler call |
| Typed via `TypedHandler` | 1.5494 ns | Typed dispatch through the framework wrapper |

The typed direct-call case is a lower-bound-style comparison. A Java equivalent should distinguish a direct interface/lambda call from the complete topic-router path rather than comparing only this row.

### Single-topic steady-state publish

One exact topic, one subscriber, warmed topic cache, one publish per iteration.

| Scenario | Criterion time | Throughput |
|---|---:|---:|
| Any-based router | 22.606–23.413 ns | 42.711–44.236 Melem/s |
| Typed router | 22.940–22.970 ns | 43.535–43.592 Melem/s |

The two router results are effectively close on this machine. The small difference should not be treated as a meaningful winner without repeated normal and LTO-profile runs.

### Cold-path publish

A new topic is published and the router must perform topic-pattern matching and populate its cache. Router creation and subscription setup are included by the benchmark setup, but the reported operation is the first publish.

| Scenario | Criterion time | Throughput |
|---|---:|---:|
| Any-based | 222.88–228.97 ns | 4.3675–4.4867 Melem/s |
| Typed | 269.15–269.44 ns | 3.7114–3.7154 Melem/s |

This is not a steady-state latency number. It represents the cost of a cache miss plus pattern scan.

### Multiple subscribers

One exact topic with 1, 5, or 10 subscribers. Throughput is reported in subscriber-handler elements.

| Subscribers | Any-based time | Typed time | Any-based throughput | Typed throughput |
|---:|---:|---:|---:|---:|
| 1 | 22.543–23.273 ns | 22.081–22.086 ns | 42.969–44.360 Melem/s | 45.277–45.288 Melem/s |
| 5 | 47.318 ns | 46.816–46.842 ns | 105.66–105.67 Melem/s | 106.74–106.80 Melem/s |
| 10 | 79.529–79.671 ns | 78.968–79.157 ns | 125.52–125.74 Melem/s | 126.33–126.63 Melem/s |

The total time increases as handlers are added. The per-handler cost decreases in the throughput column because each benchmark invocation performs more useful handler elements.

### Wildcard topic patterns

One wildcard subscription, with the topic cache warmed before measurement.

| Scenario | Criterion time | Throughput |
|---|---:|---:|
| Any-based wildcard | 22.530–22.535 ns | 44.376–44.386 Melem/s |
| Typed wildcard | 22.089–22.117 ns | 45.214–45.271 Melem/s |

Because the topic was warmed, these values primarily measure cached dispatch, not repeated cold wildcard matching.

### High-volume batch publishing

Each benchmark iteration publishes either 100,000 or 1,000,000 messages to one warmed exact topic and one subscriber.

| Messages per iteration | Any-based time | Typed time | Any-based throughput | Typed throughput |
|---:|---:|---:|---:|---:|
| 100,000 | 2.1800–2.1818 ms | 2.2312–2.2315 ms | 45.833–45.872 Melem/s | 44.813–44.819 Melem/s |
| 1,000,000 | 22.598–23.295 ms | 25.232–25.240 ms | 42.928–44.251 Melem/s | 39.620–39.632 Melem/s |

The typed batch path was slower in this particular run. That observation is valid for this binary and environment, but it should not be generalized as a property of typed routing. The benchmark implementation, optimizer decisions, atomic counter, cache state, and machine frequency behavior all affect the result.

### Mixed-topic workload

Four warmed topics—BTCUSDT, ETHUSDT, SOLUSDT, and XRPUSDT—with one wildcard subscription matching all four. One benchmark iteration publishes each topic once.

| Scenario | Time for four publishes | Throughput |
|---|---:|---:|
| Any-based | 103.04–103.11 ns | 38.793–38.819 Melem/s |
| Typed | 95.551–95.711 ns | 41.793–41.862 Melem/s |

The approximate per-publish times are 25.78 ns for Any-based and 23.90 ns for typed routing, obtained by dividing the four-operation batch time by four.

### `RefCell` and thread-local access overhead

These tests compare a direct `TopicRouter`, a `RefCell<TopicRouter>`, and a thread-local `RefCell<TopicRouter>`.

| Access path | Criterion time | Throughput |
|---|---:|---:|
| Direct `TopicRouter` | 22.137–22.201 ns | 45.044–45.173 Melem/s |
| Via `RefCell` | 22.468–22.490 ns | 44.465–44.508 Melem/s |
| Via thread-local + `RefCell` | 23.028–23.067 ns | 43.353–43.425 Melem/s |

In this run, the approximate additional cost was 0.3 ns for `RefCell` and 0.9 ns for thread-local plus `RefCell`, relative to direct access. These differences are small and should be validated with longer runs before being used as design conclusions.

### Subscription backfill

This measures adding a wildcard subscription after 0, 16, 64, or 256 concrete topics are already cached. It is a subscription-time operation, not a publish-time operation.

| Cached topics | Criterion time | Throughput |
|---:|---:|---:|
| 0 | 105.66–108.23 ns | 9.2396–9.4639 Melem/s |
| 16 | 1.2561–1.2609 µs | 793.07–796.12 Kelem/s |
| 64 | 4.6504–4.6593 µs | 214.62–215.03 Kelem/s |
| 256 | 18.033–18.083 µs | 55.302–55.453 Kelem/s |

The increasing cost illustrates that adding a subscription may scan all cached topics to backfill matching handlers.

## Important measurement limitations

1. **Quick mode was used.** The command used Criterion's `--quick` option for a practical local run. For publishable numbers, use the normal benchmark configuration and repeat the run.
2. **CPU governor was `powersave`.** The CPU was not locked to the performance governor. Dynamic frequency changes can add noise.
3. **Swap was almost fully used.** Approximately 975 MiB of swap was reported as used when the machine facts were collected. This is a warning against treating the result as a tightly controlled laboratory measurement.
4. **The system was not isolated.** No CPU pinning, ASLR control, process isolation, or background-work audit was performed.
5. **The benchmark uses minimal handler work.** Each counting handler performs an atomic increment. This is useful to prevent dead-code elimination but is not a no-op and is not a realistic trading handler.
6. **The result is not end-to-end latency.** It excludes object creation, serialization, queueing, thread handoff, logging, scheduling, and network/backing-store costs.
7. **Rust and Java must measure equivalent work.** Java results should use the same message payload shape, topic pattern, subscriber count, warm-up state, batching, and handler side effect.

## Guidance for the Java comparison

Use JMH rather than `System.nanoTime()` loops. Recommended Java benchmark dimensions:

- Direct handler dispatch: typed interface versus dynamic/object-based dispatch.
- One exact topic and one subscriber, warmed cache.
- One wildcard topic and one subscriber, warmed cache.
- 1, 5, and 10 subscribers.
- Cold topic publish with cache population.
- Four warmed instrument topics.
- 100,000 and 1,000,000 publish batches.
- Direct router versus `ThreadLocal`/wrapper access if those are part of the design.
- A separate allocation benchmark if comparing the zero-allocation example.

For fair comparison, keep the handler side effect alive using a JMH `Blackhole` or a safely consumed counter, and report both average time and throughput. Do not include console output in the timed method.

A stronger follow-up Rust run would be:

```text
cargo bench -p nautilus-common --bench msgbus
cargo bench --profile bench-lto -p nautilus-common --bench msgbus
```

Run the Java benchmark and the Rust benchmark back-to-back on the same machine, ideally after stopping background workloads and using the same CPU-governor policy.

## Reproduction

From the NautilusTrader repository root:

```text
source "$HOME/.cargo/env"
cargo bench -p nautilus-common --bench msgbus -- --quick
```

Criterion HTML reports are written below [target/criterion](../../../nautilus_trader/target/criterion). The existing benchmark registration is in [crates/common/Cargo.toml](../../../nautilus_trader/crates/common/Cargo.toml).
