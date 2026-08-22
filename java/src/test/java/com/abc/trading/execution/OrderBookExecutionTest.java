package com.abc.trading.execution;

import com.abc.trading.data.BookLevel;
import com.abc.trading.data.OrderBookSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderBookExecutionTest {
    @Test
    void marketBuyConsumesAskLevelsInPriceOrder() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBook(book(100, List.of(level(99, 5), level(98, 10)),
                List.of(level(101, 3), level(102, 4))));

        exchange.submitMarketOrder(order("buy-1", SignalDirection.BUY, 6));

        assertEquals(List.of(101.0, 102.0), fills.stream().map(OrderFill::price).toList());
        assertEquals(List.of(3, 3), fills.stream().map(OrderFill::quantity).toList());
        assertEquals(List.of(LiquiditySide.TAKER, LiquiditySide.TAKER),
                fills.stream().map(OrderFill::liquiditySide).toList());
    }

    @Test
    void marketSellConsumesBidLevelsAndCanRemainPartiallyFilled() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBook(book(100, List.of(level(99, 5), level(98, 4)),
                List.of(level(101, 10))));

        exchange.submitMarketOrder(order("sell-1", SignalDirection.SELL, 12));

        assertEquals(List.of(99.0, 98.0), fills.stream().map(OrderFill::price).toList());
        assertEquals(List.of(5, 4), fills.stream().map(OrderFill::quantity).toList());
    }

    @Test
    void crossedLimitIsTakerAndRestingLimitIsMaker() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBook(book(100, List.of(level(99, 10)), List.of(level(101, 10))));

        exchange.submitLimitOrder(limit("crossed", SignalDirection.BUY, 2, 101.0));
        exchange.submitLimitOrder(limit("resting", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBook(book(101, List.of(level(99, 10)), List.of(level(100, 2), level(101, 10))));

        assertEquals(List.of(LiquiditySide.TAKER, LiquiditySide.MAKER),
                fills.stream().map(OrderFill::liquiditySide).toList());
        assertEquals(List.of(101.0, 100.0), fills.stream().map(OrderFill::price).toList());
    }

    @Test
    void workingOrdersAtSamePriceAreProcessedFifo() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.submitLimitOrder(limit("first", SignalDirection.BUY, 2, 100.0));
        exchange.submitLimitOrder(limit("second", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBook(book(100, List.of(level(99, 10)), List.of(level(101, 10))));
        exchange.processOrderBook(book(101, List.of(level(99, 10)), List.of(level(100, 3), level(101, 10))));

        assertEquals(List.of("first", "second"), fills.stream().map(OrderFill::orderId).toList());
        assertEquals(List.of(2, 1), fills.stream().map(OrderFill::quantity).toList());
    }

    private static OrderBookSnapshot book(long timestamp, List<BookLevel> bids, List<BookLevel> asks) {
        return new OrderBookSnapshot("AAPL", timestamp, bids, asks, timestamp);
    }

    private static BookLevel level(double price, int quantity) {
        return new BookLevel(price, quantity);
    }

    private static OrderIntent order(String id, SignalDirection side, int quantity) {
        return new OrderIntent("strategy", "AAPL", 1, 100, "corr", id, side,
                quantity, 100.0, 0, 0.0);
    }

    private static LimitOrderIntent limit(String id, SignalDirection side, int quantity, double price) {
        return new LimitOrderIntent("strategy", "AAPL", 1, 100, "corr", id, side,
                quantity, price, 0, 0.0);
    }
}