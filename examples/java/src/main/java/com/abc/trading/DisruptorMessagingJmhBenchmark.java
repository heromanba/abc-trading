package com.abc.trading;

import com.abc.trading.msgbus.DisruptorMessageBus;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.msgbus.MessageBusRouter;
import com.abc.trading.msgbus.MessageHandler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
public class DisruptorMessagingJmhBenchmark {
    private static final String TOPIC = "data.trade.BINANCE.BTCUSDT";
    private static final String PAYLOAD = "trade";

    @Param({"1024", "4096", "65536"})
    private int disruptorBufferSize;

    private MessageBus directBus;
    private DisruptorMessageBus disruptorBus;
    private final AtomicLong directConsumed = new AtomicLong();
    private final AtomicLong disruptorConsumed = new AtomicLong();

    @Setup(Level.Iteration)
    public void setup() {
        directBus = new MessageBus(null);
        directBus.subscribe(String.class, message -> directConsumed.incrementAndGet());

        MessageHandler handler = event -> disruptorConsumed.incrementAndGet();
        MessageBusRouter router = new ExactTopicRouter(TOPIC, handler);
        disruptorBus = new DisruptorMessageBus(router, disruptorBufferSize);
    }

    @TearDown(Level.Iteration)
    public void tearDown() {
        disruptorBus.close();
    }

    @Benchmark
    public void directTypedDispatch() {
        directBus.publish(PAYLOAD);
    }

    @Benchmark
    public void disruptorHandoff() {
        disruptorBus.publish(TOPIC, PAYLOAD);
    }

    @Benchmark
    @Threads(4)
    public void directTypedDispatchMultiProducer() {
        directBus.publish(PAYLOAD);
    }

    @Benchmark
    @Threads(4)
    public void disruptorHandoffMultiProducer() {
        disruptorBus.publish(TOPIC, PAYLOAD);
    }

    private static final class ExactTopicRouter implements MessageBusRouter {
        private final String topic;
        private final MessageHandler handler;

        private ExactTopicRouter(String topic, MessageHandler handler) {
            this.topic = topic;
            this.handler = handler;
        }

        @Override
        public void subscribe(String topicPattern, MessageHandler handler) {
            throw new UnsupportedOperationException("benchmark router is fixed");
        }

        @Override
        public void unsubscribe(String topicPattern, MessageHandler handler) {
            throw new UnsupportedOperationException("benchmark router is fixed");
        }

        @Override
        public List<MessageHandler> route(String topic) {
            return this.topic.equals(topic) ? List.of(handler) : List.of();
        }
    }
}
