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
                Commission commission
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
                        side, quantity, price, position, realizedPnl, Commission.zero("USD"));
        }

        public OrderFill withState(int nextPosition, double nextRealizedPnl) {
                return new OrderFill(
                                strategyId,
                                symbol,
                                inputSequence,
                                marketTimestamp,
                                correlationId,
                                orderId,
                                side,
                                quantity,
                                price,
                                nextPosition,
                                nextRealizedPnl,
                                commission);
        }

        public OrderFill withCommission(Commission nextCommission) {
                return new OrderFill(
                                strategyId,
                                symbol,
                                inputSequence,
                                marketTimestamp,
                                correlationId,
                                orderId,
                                side,
                                quantity,
                                price,
                                position,
                                realizedPnl,
                                nextCommission);
        }
}