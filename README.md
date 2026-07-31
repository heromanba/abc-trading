# abc-trading
Playground for ideas for quant dev

## JMH Low-Latency Benchmark Suite

This repository includes a set of benchmark classes under `data/src/main/java/com/abc/trading/` for exploring CPU and memory behavior, including:

- `CacheLocalityBenchmark`
- `FalseSharingBenchmark`
- `SoAvsAoSBenchmark`
- `BranchPredictionBenchmark`
- `PrefetchStrideBenchmark`
- `AllocationChurnBenchmark`

### Build and run

Use Maven from the project root to compile and run a benchmark.

```bash
mvn -f data/pom.xml clean package
java -jar data/target/data-1.0-SNAPSHOT-shaded.jar <benchmark-class> -f 1
```

Example:

```bash
java -jar data/target/data-1.0-SNAPSHOT-shaded.jar com.abc.trading.CacheLocalityBenchmark -f 1
```

### If you want to run with `exec:java`

```bash
mvn -f data/pom.xml exec:java \
  -Dexec.mainClass="org.openjdk.jmh.Main" \
  -Dexec.args="com.abc.trading.CacheLocalityBenchmark -f 1" \
  -Dexec.classpathScope=runtime
```

### Notes

- The shaded JAR includes JMH and dependency classes so the forked JVM can load `org.openjdk.jmh.runner.ForkedMain`.
- The Maven compiler plugin is configured to use `jmh-generator-annprocess` for JMH annotation processing.
