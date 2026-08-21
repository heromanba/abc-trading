package com.abc.trading.execution;

import com.abc.trading.data.MarketDataSnapshot;
import com.abc.trading.execution.commands.SubmitOrder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Local order emulator modeled after Nautilus OrderEmulator ownership. */
public final class OrderEmulator {
    private final Map<String, SubmitOrder> pending = new LinkedHashMap<>();
    private final Consumer<SubmitOrder> releaseHandler;

    public OrderEmulator(Consumer<SubmitOrder> releaseHandler) {
        this.releaseHandler = releaseHandler;
    }

    public void cacheSubmitOrder(SubmitOrder command) {
        if (command.emulationTrigger() == TriggerType.NO_TRIGGER) {
            throw new IllegalArgumentException("emulationTrigger is required");
        }
        if (pending.putIfAbsent(command.clientOrderId(), command) != null) {
            throw new IllegalStateException("Order already emulated: " + command.clientOrderId());
        }
    }

    public void processMarketData(MarketDataSnapshot snapshot) {
        for (SubmitOrder command : pending.values().toArray(SubmitOrder[]::new)) {
            if (matches(command, snapshot)) {
                pending.remove(command.clientOrderId());
                releaseHandler.accept(command);
            }
        }
    }

    public boolean cancel(String clientOrderId) {
        return pending.remove(clientOrderId) != null;
    }

    public boolean contains(String clientOrderId) {
        return pending.containsKey(clientOrderId);
    }

    public Map<String, SubmitOrder> pending() {
        return Map.copyOf(pending);
    }

    private static boolean matches(SubmitOrder command, MarketDataSnapshot snapshot) {
        double marketPrice = switch (command.emulationTrigger()) {
            case DEFAULT, BID_ASK -> command.side() == SignalDirection.BUY ? snapshot.ask() : snapshot.bid();
            case LAST_PRICE -> snapshot.last();
            default -> throw new IllegalArgumentException("Unsupported emulation trigger");
        };
        double triggerPrice = command.triggerPrice() > 0.0 ? command.triggerPrice() : command.price();
        return command.side() == SignalDirection.BUY
                ? marketPrice >= triggerPrice : marketPrice <= triggerPrice;
    }
}