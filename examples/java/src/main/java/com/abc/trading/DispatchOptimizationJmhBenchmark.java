package com.abc.trading;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class DispatchOptimizationJmhBenchmark {

    private static final int BATCH_SIZE = 16_384;

    private final long[] payloads = new long[BATCH_SIZE];
    private final int[] tags = new int[BATCH_SIZE];

    private final TrendStrategy trendStrategy = new TrendStrategy();
    private final ArbitrageStrategy arbitrageStrategy = new ArbitrageStrategy();
    private final MarketMakerStrategy marketMakerStrategy = new MarketMakerStrategy();

    private Actor monomorphicActor;
    private Actor[] megamorphicActors;
    private SealedActor[] sealedActors;
    private Object[] manualComponents;

    @Setup
    public void setup() {
        for (int i = 0; i < BATCH_SIZE; i++) {
            payloads[i] = i + 1L;
            tags[i] = i % 3;
        }

        monomorphicActor = trendStrategy;
        megamorphicActors = new Actor[]{trendStrategy, arbitrageStrategy, marketMakerStrategy};
        sealedActors = new SealedActor[]{trendStrategy, arbitrageStrategy, marketMakerStrategy};
        manualComponents = new Object[]{trendStrategy, arbitrageStrategy, marketMakerStrategy};
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void directConcrete(Blackhole blackhole) {
        long acc = 0L;
        for (int i = 0; i < BATCH_SIZE; i++) {
            acc += trendStrategy.handle(payloads[i]);
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void monomorphicInterface(Blackhole blackhole) {
        long acc = 0L;
        Actor actor = monomorphicActor;
        for (int i = 0; i < BATCH_SIZE; i++) {
            acc += actor.handle(payloads[i]);
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void megamorphicInterface(Blackhole blackhole) {
        long acc = 0L;
        Actor[] actors = megamorphicActors;
        for (int i = 0; i < BATCH_SIZE; i++) {
            acc += actors[tags[i]].handle(payloads[i]);
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void manualInstanceof(Blackhole blackhole) {
        long acc = 0L;
        Object[] components = manualComponents;
        for (int i = 0; i < BATCH_SIZE; i++) {
            Object component = components[tags[i]];
            long payload = payloads[i];

            if (component instanceof TrendStrategy trend) {
                acc += trend.handle(payload);
            } else if (component instanceof ArbitrageStrategy arbitrage) {
                acc += arbitrage.handle(payload);
            } else if (component instanceof MarketMakerStrategy maker) {
                acc += maker.handle(payload);
            }
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void sealedSwitch(Blackhole blackhole) {
        long acc = 0L;
        SealedActor[] actors = sealedActors;
        for (int i = 0; i < BATCH_SIZE; i++) {
            long payload = payloads[i];
            SealedActor actor = actors[tags[i]];
            acc += switch (actor) {
                case TrendStrategy trend -> trend.handle(payload);
                case ArbitrageStrategy arbitrage -> arbitrage.handle(payload);
                case MarketMakerStrategy maker -> maker.handle(payload);
            };
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void taggedSwitch(Blackhole blackhole) {
        long acc = 0L;
        for (int i = 0; i < BATCH_SIZE; i++) {
            long payload = payloads[i];
            acc += switch (tags[i]) {
                case 0 -> trendStrategy.handle(payload);
                case 1 -> arbitrageStrategy.handle(payload);
                default -> marketMakerStrategy.handle(payload);
            };
        }
        blackhole.consume(acc);
    }

    @Benchmark
    @OperationsPerInvocation(BATCH_SIZE)
    public void splitByTypeNoDispatch(Blackhole blackhole) {
        long acc = 0L;
        for (int i = 0; i < BATCH_SIZE; i += 3) {
            acc += trendStrategy.handle(payloads[i]);
            if (i + 1 < BATCH_SIZE) {
                acc += arbitrageStrategy.handle(payloads[i + 1]);
            }
            if (i + 2 < BATCH_SIZE) {
                acc += marketMakerStrategy.handle(payloads[i + 2]);
            }
        }
        blackhole.consume(acc);
    }

    interface Actor {
        long handle(long payload);
    }

    sealed interface SealedActor permits TrendStrategy, ArbitrageStrategy, MarketMakerStrategy {
    }

    static final class TrendStrategy implements Actor, SealedActor {
        private long state;

        @Override
        public long handle(long payload) {
            state += payload + 3;
            return state;
        }
    }

    static final class ArbitrageStrategy implements Actor, SealedActor {
        private long state;

        @Override
        public long handle(long payload) {
            state ^= payload + 7;
            return state;
        }
    }

    static final class MarketMakerStrategy implements Actor, SealedActor {
        private long state;

        @Override
        public long handle(long payload) {
            state += (payload << 1) - 11;
            return state;
        }
    }
}