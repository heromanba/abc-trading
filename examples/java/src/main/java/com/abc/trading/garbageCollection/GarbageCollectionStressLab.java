package com.abc.trading.garbageCollection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GC stress playground to visualize generational behavior and compare collectors.
 *
 * <p>Scenarios:
 * <ul>
 *   <li>short-lived: many temporary allocations -> high young-gen pressure</li>
 *   <li>mixed: mostly short-lived + some survivors promoted to old gen</li>
 *   <li>humongous: periodic large allocations to exercise large-object handling</li>
 * </ul>
 */
public final class GarbageCollectionStressLab {

    private static final int KB = 1024;
    private static final int MB = 1024 * KB;

    private GarbageCollectionStressLab() {
    }

    public static void main(String[] args) {
        Config config = Config.fromArgs(args);

        System.out.println("=== GarbageCollectionStressLab ===");
        System.out.printf(Locale.ROOT,
                "scenario=%s duration=%ds burst=%d pauseMs=%d survivors=%d%% maxLiveMB=%d%n",
                config.scenario,
                config.durationSeconds,
                config.burstAllocations,
                config.pauseMs,
                config.survivorRatePercent,
                config.maxLiveSetMb);

        Runtime runtime = Runtime.getRuntime();
        List<byte[]> survivors = new ArrayList<>();
        Instant end = Instant.now().plusSeconds(config.durationSeconds);

        long iterations = 0;
        long bytesAllocated = 0;
        long lastReport = System.nanoTime();

        while (Instant.now().isBefore(end)) {
            for (int i = 0; i < config.burstAllocations; i++) {
                byte[] block = allocateBlock(config.scenario);
                bytesAllocated += block.length;

                if (shouldKeep(block, config.survivorRatePercent)) {
                    survivors.add(block);
                }
            }

            trimLiveSetIfNeeded(survivors, config.maxLiveSetMb);

            iterations++;
            long now = System.nanoTime();
            if (now - lastReport >= 1_000_000_000L) {
                long used = runtime.totalMemory() - runtime.freeMemory();
                System.out.printf(Locale.ROOT,
                        "t=%4ds iterations=%d allocated=%7.1fMB live=%7.1fMB survivors=%d%n",
                        (config.durationSeconds - Duration.between(Instant.now(), end).toSeconds()),
                        iterations,
                        bytesAllocated / (double) MB,
                        used / (double) MB,
                        survivors.size());
                lastReport = now;
            }

            if (config.pauseMs > 0) {
                try {
                    Thread.sleep(config.pauseMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "Done. iterations=%d totalAllocated=%.1fMB finalSurvivors=%d%n",
                iterations,
                bytesAllocated / (double) MB,
                survivors.size());
    }

    private static byte[] allocateBlock(String scenario) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        return switch (scenario) {
            case "short-lived" -> new byte[random.nextInt(8 * KB, 64 * KB)];
            case "mixed" -> {
                if (random.nextInt(100) < 5) {
                    yield new byte[random.nextInt(256 * KB, 1024 * KB)];
                }
                yield new byte[random.nextInt(4 * KB, 48 * KB)];
            }
            case "humongous" -> {
                if (random.nextInt(100) < 12) {
                    yield new byte[random.nextInt(2 * MB, 8 * MB)];
                }
                yield new byte[random.nextInt(16 * KB, 128 * KB)];
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    private static boolean shouldKeep(byte[] block, int survivorRatePercent) {
        int dynamicRate = survivorRatePercent;
        if (block.length >= MB) {
            dynamicRate = Math.max(dynamicRate, 40);
        }
        return ThreadLocalRandom.current().nextInt(100) < dynamicRate;
    }

    private static void trimLiveSetIfNeeded(List<byte[]> survivors, int maxLiveSetMb) {
        long maxBytes = (long) maxLiveSetMb * MB;
        long current = 0;

        for (int i = survivors.size() - 1; i >= 0; i--) {
            current += survivors.get(i).length;
            if (current > maxBytes) {
                survivors.subList(0, i + 1).clear();
                break;
            }
        }
    }

    private record Config(
            String scenario,
            int durationSeconds,
            int burstAllocations,
            int pauseMs,
            int survivorRatePercent,
            int maxLiveSetMb
    ) {
        private static Config fromArgs(String[] args) {
            String scenario = get(args, "--scenario", "mixed");
            int durationSeconds = parseInt(get(args, "--durationSec", "45"));
            int burstAllocations = parseInt(get(args, "--burst", "2500"));
            int pauseMs = parseInt(get(args, "--pauseMs", "5"));
            int survivorRatePercent = parseInt(get(args, "--survivorRate", "8"));
            int maxLiveSetMb = parseInt(get(args, "--maxLiveMb", "768"));

            return new Config(
                    scenario,
                    durationSeconds,
                    burstAllocations,
                    pauseMs,
                    survivorRatePercent,
                    maxLiveSetMb);
        }

        private static String get(String[] args, String key, String defaultValue) {
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].equals(key)) {
                    return args[i + 1];
                }
            }
            return defaultValue;
        }

        private static int parseInt(String value) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + value, e);
            }
        }
    }
}
