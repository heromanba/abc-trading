package com.abc.trading.execution;

import com.abc.trading.data.Quantity;
import com.abc.trading.data.Price;
import java.math.BigDecimal;

public record OrderIntent(
        String strategyId,
        String symbol,
        long inputSequence,
        long marketTimestamp,
        String correlationId,
        String orderId,
        SignalDirection side,
        Quantity quantity,
        Price price,
        BigDecimal currentPosition,
        BigDecimal realizedPnl,
        TimeInForce timeInForce,
        long expireTimeNs,
        Price triggerPrice,
        TriggerType triggerType,
        Price activationPrice,
        double trailingOffset,
        TrailingOffsetType trailingOffsetType
) {
        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                String correlationId, String orderId, SignalDirection side, Quantity quantity, double price,
                BigDecimal currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                double triggerPrice, TriggerType triggerType, double activationPrice,
                double trailingOffset, TrailingOffsetType trailingOffsetType) {
            this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side, quantity,
                    Price.fromDouble(price), currentPosition, BigDecimal.valueOf(realizedPnl), timeInForce,
                    expireTimeNs, Price.fromDouble(triggerPrice), triggerType, Price.fromDouble(activationPrice),
                    trailingOffset, trailingOffsetType);
        }

        public double priceDouble() { return price.asDouble(); }
        public Price triggerPriceValue(int precision) { return triggerPrice; }
        public Price activationPriceValue(int precision) { return activationPrice; }
        public BigDecimal priceDecimal() { return price.asDecimal(); }
        public double realizedPnlDouble() { return realizedPnl.doubleValue(); }
        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double price,
                        BigDecimal currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, currentPosition, realizedPnl, timeInForce, expireTimeNs,
                                triggerPrice, triggerType, 0.0, 0.0, null);
        }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        BigDecimal currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), price, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
        }
        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double price,
                        BigDecimal currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, currentPosition, realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
        }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType, double activationPrice,
                        double trailingOffset, TrailingOffsetType trailingOffsetType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), price, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce,
                                expireTimeNs, triggerPrice, triggerType, activationPrice, trailingOffset, trailingOffsetType);
        }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, Quantity quantity, double price,
                        int currentPosition, double realizedPnl) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                quantity, price, BigDecimal.valueOf(currentPosition), realizedPnl, TimeInForce.GTC, 0L,
                                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
        }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, BigDecimal.valueOf(currentPosition), realizedPnl, TimeInForce.GTC, 0L,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce, expireTimeNs,
                0.0, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

    public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
            String correlationId, String orderId, SignalDirection side, int quantity, double price,
            int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
            double triggerPrice) {
        this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                Quantity.fromInt(quantity), price, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce, expireTimeNs,
                triggerPrice, TriggerType.NO_TRIGGER, 0.0, 0.0, null);
    }

        public OrderIntent(String strategyId, String symbol, long inputSequence, long marketTimestamp,
                        String correlationId, String orderId, SignalDirection side, int quantity, double price,
                        int currentPosition, double realizedPnl, TimeInForce timeInForce, long expireTimeNs,
                        double triggerPrice, TriggerType triggerType) {
                this(strategyId, symbol, inputSequence, marketTimestamp, correlationId, orderId, side,
                                Quantity.fromInt(quantity), price, BigDecimal.valueOf(currentPosition), realizedPnl, timeInForce, expireTimeNs,
                                triggerPrice, triggerType, 0.0, 0.0, null);
        }
}
