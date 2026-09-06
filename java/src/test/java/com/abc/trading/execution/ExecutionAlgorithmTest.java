package com.abc.trading.execution;

import com.abc.trading.data.AggressorSide;
import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.data.Quantity;
import com.abc.trading.data.TradeTick;
import com.abc.trading.msgbus.MessageBus;
import com.abc.trading.execution.commands.CancelOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAlgorithmTest {
    @Test
    void twapDistributesTargetAcrossTimeAndRestoresProgress() {
        MessageBus bus = new MessageBus(null);
        List<OrderIntent> orders = new ArrayList<>();
        bus.subscribe(OrderIntent.class, orders::add);
        TwapExecutionConfig config = new TwapExecutionConfig("twap-1", "BTCUSDT", SignalDirection.BUY,
                Quantity.fromInt(10), 100, 500, 100, TimeInForce.IOC);
        TwapExecutionAlgorithm algorithm = new TwapExecutionAlgorithm(bus, config);
        algorithm.start();

        bus.publish(new MarketDataSnapshot("BTCUSDT", 99, 100, 100, 100, 100, 100, 1));
        bus.publish(new MarketDataSnapshot("BTCUSDT", 100, 100, 100, 101, 101, 101, 2));
        bus.publish(new MarketDataSnapshot("BTCUSDT", 200, 100, 100, 102, 102, 102, 3));
        bus.publish(new MarketDataSnapshot("BTCUSDT", 300, 100, 100, 103, 103, 103, 4));
        bus.publish(new MarketDataSnapshot("BTCUSDT", 500, 100, 100, 104, 104, 104, 5));

        assertEquals(List.of(2, 2, 3, 3), orders.stream()
                .map(order -> order.quantity().toIntExact()).toList());
        assertEquals(10, algorithm.submittedQuantity().toIntExact());
        ExecutionAlgorithmState state = algorithm.state();
        algorithm.close();

        TwapExecutionAlgorithm resumed = new TwapExecutionAlgorithm(bus, config);
        resumed.restore(state);
        assertTrue(resumed.isActive());
        assertEquals(10, resumed.submittedQuantity().toIntExact());
        resumed.close();
    }

    @Test
    void vwapUsesTradeVolumeParticipationAndMaximumSlice() {
        MessageBus bus = new MessageBus(null);
        List<OrderIntent> orders = new ArrayList<>();
        bus.subscribe(OrderIntent.class, orders::add);
        VwapExecutionConfig config = new VwapExecutionConfig("vwap-1", "BTCUSDT", SignalDirection.SELL,
                Quantity.fromInt(10), 100, 1_000, new BigDecimal("0.5"),
                Quantity.fromInt(1), Quantity.fromInt(3), TimeInForce.IOC);
        VwapExecutionAlgorithm algorithm = new VwapExecutionAlgorithm(bus, config);
        algorithm.start();

        bus.publish(new TradeTick("BTCUSDT", 100, 100, 4, AggressorSide.BUYER, 1));
        bus.publish(new TradeTick("BTCUSDT", 200, 101, 4, AggressorSide.BUYER, 2));
        bus.publish(new TradeTick("BTCUSDT", 300, 102, 10, AggressorSide.SELLER, 3));
        bus.publish(new TradeTick("BTCUSDT", 400, 103, 20, AggressorSide.BUYER, 4));

        assertEquals(List.of(2, 2, 3, 3), orders.stream()
                .map(order -> order.quantity().toIntExact()).toList());
        assertEquals(10, algorithm.submittedQuantity().toIntExact());
        assertEquals(new BigDecimal("38"), algorithm.state().observedVolume());
        algorithm.close();
    }

    @Test
    void tracksFillsAndPublishesCancellationForUnfilledChildren() {
        MessageBus bus = new MessageBus(null);
        List<OrderIntent> orders = new ArrayList<>();
        List<CancelOrder> cancellations = new ArrayList<>();
        bus.subscribe(OrderIntent.class, orders::add);
        bus.subscribe(CancelOrder.class, cancellations::add);
        TwapExecutionAlgorithm algorithm = new TwapExecutionAlgorithm(bus,
                new TwapExecutionConfig("twap-2", "BTCUSDT", SignalDirection.BUY,
                        Quantity.fromInt(4), 100, 300, 100, TimeInForce.IOC));
        algorithm.start();
        bus.publish(new MarketDataSnapshot("BTCUSDT", 100, 100, 100, 100, 100, 100, 1));
        OrderIntent child = orders.get(0);
        bus.publish(new OrderFill("twap-2", "BTCUSDT", 1, 100, child.correlationId(),
                child.orderId(), SignalDirection.BUY, Quantity.fromInt(1), 100, 1, 0.0));

        assertEquals(1, algorithm.filledQuantity().toIntExact());
        algorithm.cancel();

        assertFalse(algorithm.isActive());
        assertTrue(cancellations.stream().anyMatch(cancel -> cancel.clientOrderId().equals(child.orderId())));
        algorithm.close();
    }
}
