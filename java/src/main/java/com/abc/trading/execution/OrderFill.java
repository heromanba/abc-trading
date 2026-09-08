package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;

import java.math.BigDecimal;

public record OrderFill(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        Quantity quantity,
        Price price,
        BigDecimal position,
        double realizedPnl,
        Commission commission,
        LiquiditySide liquiditySide,
        String venueOrderId
) {
    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, Quantity quantity,
            double price, BigDecimal position, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
                side, quantity, Price.fromDouble(price), position, realizedPnl, Commission.zero("USD"),
                LiquiditySide.TAKER, "");
    }

    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, Quantity quantity,
            double price, int position, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
            side, quantity, Price.fromDouble(price), BigDecimal.valueOf(position), realizedPnl,
            Commission.zero("USD"), LiquiditySide.TAKER, "");
    }

    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity,
            double price, int position, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
            side, Quantity.fromInt(quantity), Price.fromDouble(price), BigDecimal.valueOf(position), realizedPnl,
            Commission.zero("USD"), LiquiditySide.TAKER, "");
    }

    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity,
            double price, int position, double realizedPnl, Commission commission) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
                side, Quantity.fromInt(quantity), Price.fromDouble(price), BigDecimal.valueOf(position), realizedPnl,
                commission, LiquiditySide.TAKER, "");
    }

    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity,
            double price, int position, double realizedPnl, Commission commission,
            LiquiditySide liquiditySide, String venueOrderId) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
                side, Quantity.fromInt(quantity), Price.fromDouble(price), BigDecimal.valueOf(position), realizedPnl,
                commission, liquiditySide, venueOrderId);
    }

    public OrderFill(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, Quantity quantity,
            double price, BigDecimal position, double realizedPnl, Commission commission,
            LiquiditySide liquiditySide, String venueOrderId) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, Price.fromDouble(price), position, realizedPnl, commission, liquiditySide, venueOrderId);
    }

    public OrderFill withState(int nextPosition, double nextRealizedPnl) {
        return withState(BigDecimal.valueOf(nextPosition), nextRealizedPnl);
    }

    public OrderFill withState(BigDecimal nextPosition, double nextRealizedPnl) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, nextPosition, nextRealizedPnl, commission,
                liquiditySide, venueOrderId);
    }

        public BigDecimal priceDecimal() { return price.asDecimal(); }
        public double priceDouble() { return price.asDouble(); }
        public BigDecimal realizedPnlDecimal() { return BigDecimal.valueOf(realizedPnl); }

    public OrderFill withCommission(Commission nextCommission) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, position, realizedPnl, nextCommission,
                liquiditySide, venueOrderId);
    }

    public OrderFill withQuantity(int nextQuantity) {
        return withQuantity(Quantity.fromInt(nextQuantity));
    }

    public OrderFill withQuantity(Quantity nextQuantity) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, nextQuantity, price, position, realizedPnl, commission,
                liquiditySide, venueOrderId);
    }

    public OrderFill withLiquiditySide(LiquiditySide nextLiquiditySide) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, position, realizedPnl, commission,
                nextLiquiditySide, venueOrderId);
    }

    public OrderFill withVenueOrderId(String nextVenueOrderId) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, position, realizedPnl, commission,
                liquiditySide, nextVenueOrderId);
    }
}
