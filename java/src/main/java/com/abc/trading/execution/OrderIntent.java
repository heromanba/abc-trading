package com.abc.trading.execution;

public record OrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        int quantity,
        double price,
        int currentPosition,
        double realizedPnl,
        TimeInForce timeInForce,
        long expireTimeNs,
        double triggerPrice,
        TriggerType triggerType
) {
    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                quantity, price, currentPosition, realizedPnl, TimeInForce.GTC, 0L, 0.0, TriggerType.NO_TRIGGER);
    }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                                0.0, TriggerType.NO_TRIGGER);
        }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                                triggerPrice, TriggerType.NO_TRIGGER);
        }
}
