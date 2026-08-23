package com.abc.trading.execution;

import com.abc.trading.data.BookLevel;
import com.abc.trading.data.BookAction;
import com.abc.trading.data.OrderBookDelta;
import com.abc.trading.data.OrderBookSnapshot;
import com.abc.trading.data.OrderBookL3Snapshot;
import com.abc.trading.data.OrderBookL3Delta;
import com.abc.trading.data.VenueOrder;
import com.abc.trading.data.TradeTick;
import com.abc.trading.data.AggressorSide;
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

    @Test
    void higherPriorityBuyPriceMatchesBeforeEarlierLowerPrice() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBook(book(100, List.of(level(98, 10)), List.of(level(101, 10))));
        exchange.submitLimitOrder(limit("lower", SignalDirection.BUY, 2, 99.0));
        exchange.submitLimitOrder(limit("higher", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBook(book(101, List.of(level(98, 10)), List.of(level(99, 3), level(101, 10))));

        assertEquals(List.of("higher", "lower"), fills.stream().map(OrderFill::orderId).toList());
    }

    @Test
    void deltaUpdatesReplaceAndAddLiquidityBeforeMatching() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBook(book(100, List.of(level(99, 10)), List.of(level(101, 1))));
        exchange.processOrderBookDelta(
            new OrderBookDelta("AAPL", 101, SignalDirection.SELL, BookAction.ADD, 102.0, 4, 2));
        exchange.processOrderBookDelta(
            new OrderBookDelta("AAPL", 102, SignalDirection.SELL, BookAction.UPDATE, 102.0, 5, 3));
        exchange.submitMarketOrder(order("delta-buy", SignalDirection.BUY, 6));

        assertEquals(List.of(101.0, 102.0), fills.stream().map(OrderFill::price).toList());
        assertEquals(List.of(1, 5), fills.stream().map(OrderFill::quantity).toList());
    }

    @Test
    void l3ConsumesIndividualVenueOrdersInPriceTimeOrder() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
                List.of(new VenueOrder("bid-1", SignalDirection.BUY, 99.0, 10, 1)),
                List.of(new VenueOrder("ask-1", SignalDirection.SELL, 101.0, 2, 1),
                        new VenueOrder("ask-2", SignalDirection.SELL, 101.0, 3, 2),
                        new VenueOrder("ask-3", SignalDirection.SELL, 102.0, 10, 3)), 1));

        exchange.submitMarketOrder(order("l3-buy", SignalDirection.BUY, 4));

        assertEquals(List.of("ask-1", "ask-2"), fills.stream().map(OrderFill::venueOrderId).toList());
        assertEquals(List.of(2, 2), fills.stream().map(OrderFill::quantity).toList());
        assertEquals(List.of(101.0, 101.0), fills.stream().map(OrderFill::price).toList());
    }

    @Test
    void l3DeleteDeltaAdvancesToNextVenueOrder() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
                List.of(new VenueOrder("bid-1", SignalDirection.BUY, 99.0, 10, 1)),
                List.of(new VenueOrder("ask-1", SignalDirection.SELL, 101.0, 2, 1),
                        new VenueOrder("ask-2", SignalDirection.SELL, 101.0, 3, 2)), 1));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 101, SignalDirection.SELL,
                BookAction.DELETE, "ask-1", 101.0, 0, 2));
        exchange.submitMarketOrder(order("l3-buy-delete", SignalDirection.BUY, 2));

        assertEquals(List.of("ask-2"), fills.stream().map(OrderFill::venueOrderId).toList());
    }

        @Test
        void l3QueueAheadBlocksCrossingOrderUntilVenueOrderIsDeleted() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
            List.of(new VenueOrder("bid-ahead", SignalDirection.BUY, 100.0, 5, 1)),
            List.of(new VenueOrder("ask-cross", SignalDirection.SELL, 102.0, 5, 1)), 1));
        exchange.submitLimitOrder(limit("queued-buy", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 101, SignalDirection.SELL,
            BookAction.UPDATE, "ask-cross", 100.0, 5, 2));

        assertEquals(List.of(), fills);

        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 102, SignalDirection.BUY,
            BookAction.DELETE, "bid-ahead", 100.0, 0, 3));

        assertEquals(List.of("ask-cross"), fills.stream().map(OrderFill::venueOrderId).toList());
        }

        @Test
        void l3QueueSizeDecreaseAdvancesByTheReleasedQuantity() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
            List.of(new VenueOrder("bid-ahead", SignalDirection.BUY, 100.0, 5, 1)),
            List.of(new VenueOrder("ask-cross", SignalDirection.SELL, 102.0, 5, 1)), 1));
        exchange.submitLimitOrder(limit("queued-buy", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 101, SignalDirection.SELL,
            BookAction.UPDATE, "ask-cross", 100.0, 5, 2));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 102, SignalDirection.BUY,
            BookAction.UPDATE, "bid-ahead", 100.0, 3, 3));

        assertEquals(List.of(), fills);
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 103, SignalDirection.BUY,
            BookAction.UPDATE, "bid-ahead", 100.0, 0, 4));
        assertEquals(List.of("ask-cross"), fills.stream().map(OrderFill::venueOrderId).toList());
        }

        @Test
        void l3QueueSizeIncreaseKeepsVenueOrderAhead() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
            List.of(new VenueOrder("bid-ahead", SignalDirection.BUY, 100.0, 2, 1)),
            List.of(new VenueOrder("ask-cross", SignalDirection.SELL, 102.0, 5, 1)), 1));
        exchange.submitLimitOrder(limit("queued-buy", SignalDirection.BUY, 2, 100.0));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 101, SignalDirection.SELL,
            BookAction.UPDATE, "ask-cross", 100.0, 5, 2));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 102, SignalDirection.BUY,
            BookAction.UPDATE, "bid-ahead", 100.0, 4, 3));
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 103, SignalDirection.BUY,
            BookAction.UPDATE, "bid-ahead", 100.0, 2, 4));

        assertEquals(List.of(), fills);
        exchange.processOrderBookL3Delta(new OrderBookL3Delta("AAPL", 104, SignalDirection.BUY,
            BookAction.DELETE, "bid-ahead", 100.0, 0, 5));
        assertEquals(List.of("ask-cross"), fills.stream().map(OrderFill::venueOrderId).toList());
        }

    @Test
    void l3SellAggressorConsumesBidQueueBeforeFillingRestingBuy() {
        List<OrderFill> fills = new ArrayList<>();
        SimulatedExchange exchange = new SimulatedExchange(new VenueId("XNAS"), fills::add);
        exchange.processOrderBookL3(new OrderBookL3Snapshot("AAPL", 100,
                List.of(new VenueOrder("bid-ahead", SignalDirection.BUY, 100.0, 5, 1)),
                List.of(new VenueOrder("ask-1", SignalDirection.SELL, 102.0, 5, 1)), 1));
        exchange.submitLimitOrder(limit("queued-buy", SignalDirection.BUY, 2, 100.0));

        exchange.processTradeTick(new TradeTick("AAPL", 101, 100.0, 3, AggressorSide.SELLER, 2));
        assertEquals(List.of(), fills);

        exchange.processTradeTick(new TradeTick("AAPL", 102, 100.0, 4, AggressorSide.SELLER, 3));

        assertEquals(List.of(2), fills.stream().map(OrderFill::quantity).toList());
        assertEquals(List.of(100.0), fills.stream().map(OrderFill::price).toList());
        assertEquals(List.of(LiquiditySide.MAKER), fills.stream().map(OrderFill::liquiditySide).toList());
        assertEquals(List.of(""), fills.stream().map(OrderFill::venueOrderId).toList());
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