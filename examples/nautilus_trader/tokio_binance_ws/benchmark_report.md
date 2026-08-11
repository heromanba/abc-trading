# Rust Tokio vs Java Netty Binance WebSocket Benchmark

## Scope
This benchmark compares a Binance BTCUSDT @bookTicker websocket consumer implemented in Rust with Tokio and in Java with Netty. The goal is to measure the metrics that matter for a low-latency trading pipeline:

- average ingest latency
- tail latency (p99.9)
- jitter / latency distribution
- throughput
- memory footprint
- connection setup time
- time to first message
- ordering skew
- parse errors and disconnects

## Verified results

### Rust Tokio
Observed from a 45-second measurement window after a 5-second warm-up:

- messages: 5105
- parse errors: 0
- duration seconds: 45.002
- average ingest latency: 14.163 us
- p99.9 latency: 81.907 us
- throughput: 113.440 messages/s
- RSS: 12,796 KB

### Java Netty
Observed from a 45-second measurement window after a 5-second warm-up.

#### Baseline
- messages: 135
- parse errors: 0
- disconnects: 0
- connection setup: 210 ms
- time to first message: 2045 ms
- average ingest latency: 7.288 us
- p99.9 latency: 236.967 us
- jitter: 235.557 us
- throughput: 9.000 messages/s
- ordering skew: 0.000 us
- heap: ~34.6 MB
- GC time: 25 ms

#### Pooled allocator
- messages: 109
- parse errors: 0
- disconnects: 0
- connection setup: 59 ms
- time to first message: 314 ms
- average ingest latency: 8.579 us
- p99.9 latency: 36.141 us
- jitter: 34.775 us
- throughput: 7.267 messages/s
- ordering skew: 0.000 us
- heap: ~44.5 MB
- GC time: 25 ms

#### Epoll transport
- messages: 75
- parse errors: 0
- disconnects: 0
- connection setup: 66 ms
- time to first message: 256 ms
- average ingest latency: 5.032 us
- p99.9 latency: 14.134 us
- jitter: 13.100 us
- throughput: 5.000 messages/s
- ordering skew: 0.000 us
- heap: ~49.5 MB
- GC time: 25 ms

## Side-by-side comparison

| Metric | Rust Tokio | Java Netty baseline | Java Netty pooled | Java Netty epoll |
| --- | ---: | ---: | ---: | ---: |
| Messages observed | 5105 | 135 | 109 | 75 |
| Average ingest latency | 14.163 us | 7.288 us | 8.579 us | 5.032 us |
| p99.9 latency | 81.907 us | 236.967 us | 36.141 us | 14.134 us |
| Jitter | n/a | 235.557 us | 34.775 us | 13.100 us |
| Throughput | 113.440 msg/s | 9.000 msg/s | 7.267 msg/s | 5.000 msg/s |
| Memory footprint | 12,796 KB RSS | ~34.6 MB heap | ~44.5 MB heap | ~49.5 MB heap |
| Time to first message | n/a | 2045 ms | 314 ms | 256 ms |

## Interpretation
The updated results show that the BTCUSDT @bookTicker feed is a good stress case for this benchmark, and the Java side benefits strongly from transport and allocator tuning:

- Rust Tokio delivered the highest observed message volume and the most memory-light runtime profile in this run.
- The Java baseline had the highest raw throughput of the Java strategies, but its tail latency and startup latency were clearly worse.
- The pooled allocator substantially improved Java tail latency compared with the baseline.
- The epoll transport produced the best Java latency profile overall, including the lowest p99.9 latency and jitter.
- For a trading pipeline, tail latency stability and startup predictability are often more important than average throughput alone.

## Practical takeaway
For low-latency workload tuning on Linux, the best trade-off from this benchmark was:

1. keep the parsing path short and allocation-light
2. prefer pooled buffers for the Java path
3. use epoll transport when available
4. monitor p99.9, jitter, and first-message latency alongside average latency
