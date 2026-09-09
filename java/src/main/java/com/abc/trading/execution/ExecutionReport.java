package com.abc.trading.execution;

import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;

import java.math.BigDecimal;

/** Venue-normalized execution report preserving both client and exchange identities. */
public record ExecutionReport(
        String clientOrderId,
        String exchangeOrderId,
        String symbol,
        OrderEventType eventType,
        OrderStatus status,
        SignalDirection side,
        Quantity lastQuantity,
        Price lastPrice,
        BigDecimal commission,
        String commissionCurrency,
        long eventTimeNs,
        String deduplicationKey) {
    public ExecutionReport {
        if (clientOrderId == null || clientOrderId.isBlank()) throw new IllegalArgumentException("clientOrderId is required");
        if (exchangeOrderId == null || exchangeOrderId.isBlank()) throw new IllegalArgumentException("exchangeOrderId is required");
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        if (eventType == null || status == null) throw new IllegalArgumentException("eventType and status are required");
        if (side == null || side == SignalDirection.HOLD) throw new IllegalArgumentException("side is required");
        if (lastQuantity == null || lastPrice == null) throw new IllegalArgumentException("fill values are required");
        if (commission == null || commissionCurrency == null || commissionCurrency.isBlank()) {
            throw new IllegalArgumentException("commission is required");
        }
        if (eventTimeNs < 0 || deduplicationKey == null || deduplicationKey.isBlank()) {
            throw new IllegalArgumentException("execution identity is invalid");
        }
    }

    public OrderEvent toOrderEvent() {
        return new OrderEvent(clientOrderId, exchangeOrderId, eventType, null, status,
                lastQuantity, lastQuantity, com.abc.trading.data.Quantity.fromInt(0), lastPrice);
    }
}
