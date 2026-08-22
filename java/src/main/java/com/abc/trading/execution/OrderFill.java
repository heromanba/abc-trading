package com.abc.trading.execution;

public record OrderFill(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        int quantity,
        double price,
        int position,
        double realizedPnl,
        Commission commission,
        LiquiditySide liquiditySide,
        String venueOrderId
) {
    public OrderFill(
            String strategyId,
            String symbol,
            long inputSequence,
            long marketTimestamp,
            String correlationId,
            String orderId,
            SignalDirection side,
            int quantity,
            double price,
            int position,
            double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
                side, quantity, price, position, realizedPnl, Commission.zero("USD"),
                LiquiditySide.TAKER, "");
    }

    public OrderFill(
            String strategyId,
            String symbol,
            long inputSequence,
            long marketTimestamp,
            String correlationId,
            String orderId,
            SignalDirection side,
            int quantity,
            double price,
            int position,
            double realizedPnl,
            Commission commission) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId,
                side, quantity, price, position, realizedPnl, commission, LiquiditySide.TAKER, "");
    }

    public OrderFill withState(int nextPosition, double nextRealizedPnl) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, nextPosition, nextRealizedPnl, commission,
                liquiditySide, venueOrderId);
    }

    public OrderFill withCommission(Commission nextCommission) {
        return new OrderFill(strategyId, symbol, inputSequence, marketTimestamp, correlationId,
                orderId, side, quantity, price, position, realizedPnl, nextCommission,
                liquiditySide, venueOrderId);
    }

    public OrderFill withQuantity(int nextQuantity) {
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
