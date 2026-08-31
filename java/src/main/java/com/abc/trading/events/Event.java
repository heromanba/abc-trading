package com.abc.trading.events;

import com.abc.trading.data.Quantity;
import com.abc.trading.execution.LiquiditySide;
import com.abc.trading.execution.SignalDirection;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;

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
        Quantity quantity,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal currentPosition,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal realizedPnl,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal commission,
        String commissionCurrency,
        LiquiditySide liquiditySide,
        String venueOrderId,
        String accountCurrency,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal accountTotal,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal accountLocked,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal accountFree,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal marginInitial,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal marginMaintenance,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal unrealizedPnl,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal equity,
        boolean marginCall,
        boolean liquidationRequired
) {
    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, BigDecimal realizedPnl) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity, currentPosition,
                realizedPnl, BigDecimal.ZERO, "USD", null, "", "", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                false, false);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, int currentPosition, double realizedPnl) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity,
                BigDecimal.valueOf(currentPosition), realizedPnl);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, int quantity, int currentPosition, double realizedPnl) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, Quantity.fromInt(quantity),
                BigDecimal.valueOf(currentPosition), realizedPnl);
    }

        public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
                        String sourceEventType, EventType eventType, String strategyId,
                        SignalDirection signalDirection, String correlationId, String orderId,
                        double price, int quantity, int currentPosition, double realizedPnl,
                        double commission, String commissionCurrency, LiquiditySide liquiditySide,
                        String venueOrderId) {
                this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                                strategyId, signalDirection, correlationId, orderId, price, Quantity.fromInt(quantity),
                                BigDecimal.valueOf(currentPosition), BigDecimal.valueOf(realizedPnl),
                                BigDecimal.valueOf(commission), commissionCurrency, liquiditySide, venueOrderId);
        }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, double realizedPnl) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity, currentPosition,
                BigDecimal.valueOf(realizedPnl));
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, double realizedPnl,
            double commission, String commissionCurrency, LiquiditySide liquiditySide,
            String venueOrderId, String accountCurrency, double accountTotal,
            double accountLocked, double accountFree, double marginInitial,
            double marginMaintenance, double unrealizedPnl, double equity,
            boolean marginCall, boolean liquidationRequired) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity,
                currentPosition, BigDecimal.valueOf(realizedPnl), BigDecimal.valueOf(commission),
                commissionCurrency, liquiditySide, venueOrderId, accountCurrency,
                BigDecimal.valueOf(accountTotal), BigDecimal.valueOf(accountLocked),
                BigDecimal.valueOf(accountFree), BigDecimal.valueOf(marginInitial),
                BigDecimal.valueOf(marginMaintenance), BigDecimal.valueOf(unrealizedPnl),
                BigDecimal.valueOf(equity), marginCall, liquidationRequired);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, BigDecimal realizedPnl,
            BigDecimal commission, String commissionCurrency, LiquiditySide liquiditySide,
            String venueOrderId) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity, currentPosition,
                realizedPnl, commission, commissionCurrency, liquiditySide, venueOrderId, "",
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, double realizedPnl,
            double commission, String commissionCurrency, LiquiditySide liquiditySide,
            String venueOrderId) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, quantity, currentPosition,
                BigDecimal.valueOf(realizedPnl), BigDecimal.valueOf(commission), commissionCurrency,
                liquiditySide, venueOrderId);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, int quantity, int currentPosition, double realizedPnl, double commission,
            String commissionCurrency, LiquiditySide liquiditySide, String venueOrderId,
            String accountCurrency, double accountTotal, double accountLocked, double accountFree,
            double marginInitial, double marginMaintenance, double unrealizedPnl, double equity,
            boolean marginCall, boolean liquidationRequired) {
        this(inputSequence, lifecycleSequence, marketTimestamp, symbol, sourceEventType, eventType,
                strategyId, signalDirection, correlationId, orderId, price, Quantity.fromInt(quantity),
                BigDecimal.valueOf(currentPosition), BigDecimal.valueOf(realizedPnl),
                BigDecimal.valueOf(commission), commissionCurrency, liquiditySide, venueOrderId,
                accountCurrency, BigDecimal.valueOf(accountTotal), BigDecimal.valueOf(accountLocked),
                BigDecimal.valueOf(accountFree), BigDecimal.valueOf(marginInitial),
                BigDecimal.valueOf(marginMaintenance), BigDecimal.valueOf(unrealizedPnl),
                BigDecimal.valueOf(equity), marginCall, liquidationRequired);
    }

    public Event(long inputSequence, long lifecycleSequence, long marketTimestamp, String symbol,
            String sourceEventType, EventType eventType, String strategyId,
            SignalDirection signalDirection, String correlationId, String orderId,
            double price, Quantity quantity, BigDecimal currentPosition, BigDecimal realizedPnl,
            BigDecimal commission, String commissionCurrency, LiquiditySide liquiditySide,
            String venueOrderId, String accountCurrency, BigDecimal accountTotal,
            BigDecimal accountLocked, BigDecimal accountFree, BigDecimal marginInitial,
            BigDecimal marginMaintenance, BigDecimal unrealizedPnl, BigDecimal equity,
            boolean marginCall, boolean liquidationRequired) {
        this.inputSequence = inputSequence;
        this.lifecycleSequence = lifecycleSequence;
        this.marketTimestamp = marketTimestamp;
        this.symbol = symbol;
        this.sourceEventType = sourceEventType;
        this.eventType = eventType;
        this.strategyId = strategyId;
        this.signalDirection = signalDirection;
        this.correlationId = correlationId;
        this.orderId = orderId;
        this.price = price;
        this.quantity = quantity;
        this.currentPosition = currentPosition;
        this.realizedPnl = realizedPnl;
        this.commission = commission;
        this.commissionCurrency = commissionCurrency;
        this.liquiditySide = liquiditySide;
        this.venueOrderId = venueOrderId;
        this.accountCurrency = accountCurrency;
        this.accountTotal = accountTotal;
        this.accountLocked = accountLocked;
        this.accountFree = accountFree;
        this.marginInitial = marginInitial;
        this.marginMaintenance = marginMaintenance;
        this.unrealizedPnl = unrealizedPnl;
        this.equity = equity;
        this.marginCall = marginCall;
        this.liquidationRequired = liquidationRequired;
    }

    public double realizedPnlDouble() { return realizedPnl.doubleValue(); }
    public double commissionDouble() { return commission.doubleValue(); }
    public double accountTotalDouble() { return accountTotal.doubleValue(); }
    public double accountLockedDouble() { return accountLocked.doubleValue(); }
    public double accountFreeDouble() { return accountFree.doubleValue(); }
    public double marginInitialDouble() { return marginInitial.doubleValue(); }
    public double marginMaintenanceDouble() { return marginMaintenance.doubleValue(); }
    public double unrealizedPnlDouble() { return unrealizedPnl.doubleValue(); }
    public double equityDouble() { return equity.doubleValue(); }
}
