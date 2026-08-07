# Cache Miss Example

This folder contains a small Java program that illustrates cache misses caused by poor spatial locality.

## What it shows

- `sequentialScan()` walks an `int[]` in order.
- `randomPointerChase()` follows a randomized next-index table.

The sequential path is cache-friendly because adjacent values live on the same cache lines and the CPU prefetcher can predict access patterns.
The random path is cache-unfriendly because each access jumps to a new location, causing more cache misses and stalled cycles.

## Run

From `abc-trading/examples/java`:

```bash
mvn -q -DskipTests compile
java -cp target/classes com.abc.trading.cachemiss.CacheMissExample
```

## How to interpret the output

You should see the random pointer-chase path take noticeably longer than the sequential scan path.

That gap is a simple illustration of:

- cache line reuse
- hardware prefetching
- pointer chasing
- memory latency hiding versus cache miss stalls

## Related example

If you want a JMH benchmark version, see the existing [CacheLocalityBenchmark.java](../CacheLocalityBenchmark.java).