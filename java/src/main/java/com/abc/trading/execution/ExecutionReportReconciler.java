package com.abc.trading.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Orders venue reports by event time, suppresses duplicates, and rejects stale terminal races. */
public final class ExecutionReportReconciler {
    private final Map<String, Long> lastEventTimes = new HashMap<>();
    private final Map<String, OrderStatus> statuses = new HashMap<>();
    private final Set<String> seenReports = new HashSet<>();

    public synchronized Optional<OrderEvent> accept(ExecutionReport report) {
        if (!seenReports.add(report.deduplicationKey())) return Optional.empty();
        String orderId = report.clientOrderId();
        long previousTime = lastEventTimes.getOrDefault(orderId, -1L);
        OrderStatus previousStatus = statuses.get(orderId);
        if (report.eventTimeNs() < previousTime) return Optional.empty();
        if (previousStatus != null && isTerminal(previousStatus)
                && report.status() != OrderStatus.FILLED) return Optional.empty();
        lastEventTimes.put(orderId, report.eventTimeNs());
        statuses.put(orderId, report.status());
        return Optional.of(new OrderEvent(orderId, report.exchangeOrderId(), report.eventType(),
                previousStatus, report.status(), report.lastQuantity(), report.lastQuantity(),
                com.abc.trading.data.Quantity.fromInt(0), report.lastPrice()));
    }

    public synchronized OrderStatus status(String clientOrderId) {
        return statuses.get(clientOrderId);
    }

    public synchronized int seenReportCount() {
        return seenReports.size();
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.FILLED || status == OrderStatus.CANCELED
                || status == OrderStatus.REJECTED || status == OrderStatus.EXPIRED;
    }
}
