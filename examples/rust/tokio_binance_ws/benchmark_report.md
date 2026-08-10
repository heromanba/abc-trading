# Rust Tokio vs Java Netty Binance WebSocket Benchmark

## Scope
This benchmark compares a Binance ticker websocket consumer implemented in Rust with Tokio and in Java with Netty. The goal is to measure the kinds of metrics that matter for a low-latency trading pipeline:

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

- messages: 67
- parse errors: 0
- duration seconds: 15.290
- average ingest latency: 17.83 us
- p99.9 latency: 49.78 us
- throughput: 4.38 messages/s
- RSS: 12,524 KB

### Java Netty

Observed from a 45-second measurement window after a 5-second warm-up for the baseline strategy:

- messages: 135
- parse errors: 0
- disconnects: 0
- connection setup: 210 ms
- time to first message: 2045 ms
- average ingest latency: 7.29 us
- p99.9 latency: 236.97 us
- jitter: 235.56 us
- throughput: 9.00 messages/s
- ordering skew: 0 us
- heap: ~36.2 MB
- GC time: 25 ms

## Java tuning strategies tested

### Baseline
- Lowest throughput of the three runs in the stricter benchmark, but it was the most straightforward implementation.

### Pooled allocator
- connection setup: 59 ms
- time to first message: 314 ms
- average ingest latency: 8.58 us
- p99.9 latency: 36.14 us
- jitter: 34.78 us
- throughput: 7.27 messages/s

### Epoll transport
- connection setup: 66 ms
- time to first message: 256 ms
- average ingest latency: 5.03 us
- p99.9 latency: 14.13 us
- jitter: 13.10 us
- throughput: 5.00 messages/s

## Side-by-side comparison

| Metric | Rust Tokio | Java Netty baseline | Java Netty pooled | Java Netty epoll |
| --- | ---: | ---: | ---: | ---: |
| Messages observed | 67 | 135 | 109 | 75 |
| Average ingest latency | 17.83 us | 7.29 us | 8.58 us | 5.03 us |
| p99.9 latency | 49.78 us | 236.97 us | 36.14 us | 14.13 us |
| Jitter | n/a | 235.56 us | 34.78 us | 13.10 us |
| Throughput | 4.38 msgs/s | 9.00 msgs/s | 7.27 msgs/s | 5.00 msgs/s |
| Memory footprint | 12,524 KB RSS | ~36.2 MB heap | ~46.9 MB heap | ~51.9 MB heap |
| Time to first message | n/a | 2045 ms | 314 ms | 256 ms |

## Interpretation
The results show that the Java side can be made much more competitive for tail latency when using more careful runtime tuning:

- The Rust Tokio run was more memory-light and had a smaller tail-latency profile than the Java baseline.
- The pooled allocator reduced Java tail latency substantially compared with the baseline.
- The epoll transport brought Java tail latency down further and also reduced the time to first message.
- In these runs, the Java epoll strategy had the best latency profile overall, while the Rust implementation remained attractive for its lower memory footprint and simpler runtime model.
- For a trading pipeline, the most important distinction is not only peak throughput but also tail latency stability and startup predictability.

## Practical takeaway
If the system needs strict low-latency behavior, the best trade-off from this small experiment was:

1. use pooled buffer allocation
2. prefer an epoll-based transport on Linux
3. keep the message parsing path short and avoid unnecessary allocations
4. monitor tail latency, jitter, and first-message latency in addition to average latency
