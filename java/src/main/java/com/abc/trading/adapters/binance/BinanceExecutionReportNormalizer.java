package com.abc.trading.adapters.binance;

import com.abc.trading.data.Price;
import com.abc.trading.data.Quantity;
import com.abc.trading.execution.ExecutionReport;
import com.abc.trading.execution.OrderEventType;
import com.abc.trading.execution.OrderStatus;
import com.abc.trading.execution.SignalDirection;

/** Converts Binance user-stream order updates into deterministic execution reports. */
public final class BinanceExecutionReportNormalizer {
    private BinanceExecutionReportNormalizer() { }

    public static ExecutionReport normalize(BinanceOrderUpdate update) {
        OrderStatus status = status(update.orderStatus());
        OrderEventType eventType = update.isTrade()
                ? (status == OrderStatus.FILLED ? OrderEventType.FILLED : OrderEventType.PARTIALLY_FILLED)
                : eventType(status);
        SignalDirection side = "BUY".equals(update.side()) ? SignalDirection.BUY : SignalDirection.SELL;
        Quantity quantity = Quantity.fromDecimal(update.lastQuantity(), update.lastQuantity().scale());
        Price price = Price.fromDecimal(update.lastPrice(), update.lastPrice().scale());
        long eventTimeNs = Math.multiplyExact(update.eventTimeMs(), 1_000_000L);
        String key = update.orderId() + "|" + update.executionType() + "|" + update.eventTimeMs()
                + "|" + update.lastQuantity().toPlainString() + "|" + update.lastPrice().toPlainString();
        return new ExecutionReport(update.clientOrderId(), Long.toString(update.orderId()), update.symbol(),
                eventType, status, side, quantity, price, update.commission(), update.commissionAsset(),
                eventTimeNs, key);
    }

    private static OrderStatus status(String status) {
        return switch (status) {
            case "NEW" -> OrderStatus.ACCEPTED;
            case "PARTIALLY_FILLED" -> OrderStatus.PARTIALLY_FILLED;
            case "FILLED" -> OrderStatus.FILLED;
            case "CANCELED" -> OrderStatus.CANCELED;
            case "REJECTED" -> OrderStatus.REJECTED;
            case "EXPIRED" -> OrderStatus.EXPIRED;
            default -> throw new IllegalArgumentException("Unsupported Binance order status: " + status);
        };
    }

    private static OrderEventType eventType(OrderStatus status) {
        return switch (status) {
            case ACCEPTED -> OrderEventType.ACCEPTED;
            case CANCELED -> OrderEventType.CANCELED;
            case REJECTED -> OrderEventType.REJECTED;
            case EXPIRED -> OrderEventType.EXPIRED;
            default -> throw new IllegalArgumentException("Unsupported terminal report status: " + status);
        };
    }
}
