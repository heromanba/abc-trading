package com.abc.trading;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchOptimizationBenchmark {

    private static final int WARMUP_ROUNDS = 4;
    private static final int MEASURE_ROUNDS = 8;
    private static final int OPS_PER_ROUND = 20_000_000;
    private static final Path REPORT_PATH = Path.of("dispatch_optimization_benchmark_report.md");

    private static volatile long blackhole;

    @FunctionalInterface
    interface Actor {
        void handle(long payload);
    }

    static final class TrendStrategy implements Actor {
        private long state;

        @Override
        public final void handle(long payload) {
            state += payload + 3;
        }
    }

    static final class ArbitrageStrategy implements Actor {
        private long state;

        @Override
        public final void handle(long payload) {
            state ^= payload + 7;
        }
    }

    static final class MarketMakerStrategy implements Actor {
        private long state;

        @Override
        public final void handle(long payload) {
            state += (payload << 1) - 11;
        }
    }

    public static void main(String[] args) throws IOException {
        DispatchOptimizationBenchmark benchmark = new DispatchOptimizationBenchmark();

        Map<String, Double> nsPerOp = new LinkedHashMap<>();
        nsPerOp.put("direct-final-concrete", benchmark.runBenchmark(benchmark::directConcreteCall));
        nsPerOp.put("interface-monomorphic", benchmark.runBenchmark(benchmark::monomorphicInterfaceCall));
        nsPerOp.put("interface-megamorphic", benchmark.runBenchmark(benchmark::megamorphicInterfaceArrayCall));
        nsPerOp.put("manual-instanceof", benchmark.runBenchmark(benchmark::manualDispatchCall));

        String report = benchmark.buildReport(nsPerOp);
        System.out.print(report);
        Files.writeString(REPORT_PATH, report, StandardCharsets.UTF_8);
        System.out.printf("\nSaved report to %s%n", REPORT_PATH.toAbsolutePath());
    }

    private double runBenchmark(Runnable workload) {
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            workload.run();
        }

        long totalNs = 0;
        for (int i = 0; i < MEASURE_ROUNDS; i++) {
            long start = System.nanoTime();
            workload.run();
            totalNs += (System.nanoTime() - start);
        }

        double totalOps = (double) MEASURE_ROUNDS * OPS_PER_ROUND;
        return totalNs / totalOps;
    }

    private void directConcreteCall() {
        TrendStrategy strategy = new TrendStrategy();

        long payload = 1;
        for (int i = 0; i < OPS_PER_ROUND; i++) {
            strategy.handle(payload);
            payload++;
        }
        blackhole ^= payload ^ strategy.state;
    }

    private void monomorphicInterfaceCall() {
        Actor actor = new TrendStrategy();

        long payload = 1;
        for (int i = 0; i < OPS_PER_ROUND; i++) {
            actor.handle(payload);
            payload++;
        }
        blackhole ^= payload ^ ((TrendStrategy) actor).state;
    }

    private void megamorphicInterfaceArrayCall() {
        Actor[] actors = new Actor[]{
                new TrendStrategy(),
                new ArbitrageStrategy(),
                new MarketMakerStrategy()
        };

        long payload = 1;
        for (int i = 0; i < OPS_PER_ROUND; i++) {
            actors[i % actors.length].handle(payload);
            payload++;
        }
        blackhole ^= payload
                ^ ((TrendStrategy) actors[0]).state
                ^ ((ArbitrageStrategy) actors[1]).state
                ^ ((MarketMakerStrategy) actors[2]).state;
    }

    private void manualDispatchCall() {
        Object[] components = new Object[]{
                new TrendStrategy(),
                new ArbitrageStrategy(),
                new MarketMakerStrategy()
        };

        long payload = 1;
        for (int i = 0; i < OPS_PER_ROUND; i++) {
            Object component = components[i % components.length];
            if (component instanceof TrendStrategy trendStrategy) {
                trendStrategy.handle(payload);
            } else if (component instanceof ArbitrageStrategy arbitrageStrategy) {
                arbitrageStrategy.handle(payload);
            } else if (component instanceof MarketMakerStrategy marketMakerStrategy) {
                marketMakerStrategy.handle(payload);
            }
            payload++;
        }

        blackhole ^= payload
                ^ ((TrendStrategy) components[0]).state
                ^ ((ArbitrageStrategy) components[1]).state
                ^ ((MarketMakerStrategy) components[2]).state;
    }

    private String buildReport(Map<String, Double> nsPerOp) {
        double baseline = nsPerOp.get("interface-megamorphic");
        StringBuilder sb = new StringBuilder();

        sb.append("# Java Dispatch Optimization Benchmark\n\n");
        sb.append("- Generated: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .append("\n");
        sb.append("- Warmup rounds: ").append(WARMUP_ROUNDS).append("\n");
        sb.append("- Measure rounds: ").append(MEASURE_ROUNDS).append("\n");
        sb.append("- Operations per round: ").append(OPS_PER_ROUND).append("\n\n");

        sb.append("| Method | ns/op (lower is better) | ops/sec | Speedup vs megamorphic |\n");
        sb.append("|---|---:|---:|---:|\n");
        for (Map.Entry<String, Double> entry : nsPerOp.entrySet()) {
            double value = entry.getValue();
            double opsPerSec = 1_000_000_000d / value;
            double speedup = baseline / value;
            sb.append("| ").append(entry.getKey())
                    .append(" | ").append(String.format("%.3f", value))
                    .append(" | ").append(String.format("%.2f", opsPerSec))
                    .append(" | ").append(String.format("%.2fx", speedup))
                    .append(" |\n");
        }

        sb.append("\n");
        sb.append("Notes:\n");
        sb.append("- These numbers are from a single machine run and vary by CPU/JDK/flags.\n");
        sb.append("- Monomorphic and direct concrete calls are expected to be close when JIT devirtualizes.\n");
        sb.append("- Megamorphic interface dispatch is typically slower due to reduced inlining opportunities.\n");

        if (blackhole == Long.MIN_VALUE) {
            sb.append("- Blackhole guard triggered.\n");
        }

        return sb.toString();
    }
}