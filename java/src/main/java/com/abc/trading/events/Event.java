package com.abc.trading.events;

import com.abc.trading.execution.SignalDirection;
import com.abc.trading.execution.LiquiditySide;

public record Event(
        long inputSequence,
        long lifecycleSequence,
        long marketTimestamp,
        String symbol,
        String sourceEventType,
        EventType eventType,
        String strategyId,
        SignalDirection signalDirection,
        String correlationId,
        String orderId,
        double price,
        int quantity,
        int currentPosition,
        double realizedPnl,
        double commission,
        String commissionCurrency,
        LiquiditySide liquiditySide,
        String venueOrderId,
        String accountCurrency,
        double accountTotal,
        double accountLocked,
        double accountFree,
        double marginInitial,
        double marginMaintenance
) {
    public Event(
            long inputSequence,
            long lifecycleSequence,
            long marketTimestamp,
            String symbol,
            String sourceEventType,
            EventType eventType,
            String strategyId,
            SignalDirection signalDirection,
            String correlationId,
            String orderId,
            double price,
            int quantity,
            int currentPosition,
            double realizedPnl) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity,
                currentPosition, realizedPnl, 0.0, "USD", null, "", "", 0.0, 0.0, 0.0, 0.0, 0.0);
    }

        public Event(
                        long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
                        String sourceEventType, EventType eventType, String strategyId,
                        SignalDirection signalDirection, String correlationId, String orderId,
                        double price, int quantity, int currentPosition, double realizedPnl,
                        double commission, String commissionCurrency) {
                this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                                strategyId, signalDirection, correlationId, orderId, price, quantity,
                                currentPosition, realizedPnl, commission, commissionCurrency, null, "", "", 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        public Event(
                        long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
                        String sourceEventType, EventType eventType, String strategyId,
                        SignalDirection signalDirection, String correlationId, String orderId,
                        double price, int quantity, int currentPosition, double realizedPnl,
                        double commission, String commissionCurrency, LiquiditySide liquiditySide) {
                this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                                strategyId, signalDirection, correlationId, orderId, price, quantity,
                                currentPosition, realizedPnl, commission, commissionCurrency, liquiditySide, "", "", 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        public Event(
                long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
                String sourceEventType, EventType eventType, String strategyId,
                SignalDirection signalDirection, String correlationId, String orderId,
                double price, int quantity, int currentPosition, double realizedPnl,
                double commission, String commissionCurrency, LiquiditySide liquiditySide,
                String venueOrderId) {
                this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                                strategyId, signalDirection, correlationId, orderId, price, quantity,
                                currentPosition, realizedPnl, commission, commissionCurrency, liquiditySide,
                                venueOrderId, "", 0.0, 0.0, 0.0, 0.0, 0.0);
        }
}
