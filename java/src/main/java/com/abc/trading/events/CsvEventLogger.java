package com.abc.trading.events;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class CsvEventLogger implements EventLogger {
    private final BufferedWriter writer;

    public CsvEventLogger(Path path) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            writer.write("input_sequence,lifecycle_sequence,market_timestamp,symbol,source_event_type,event_type,strategy_id,signal_direction,correlation_id,order_id,price,quantity,current_position,realized_pnl");
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create event log at " + path, e);
        }
    }

    @Override
    public synchronized void log(Event event) {
        try {
            writer.write(toCsvRow(event));
            writer.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write event", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to close event log", e);
        }
    }

    private static String toCsvRow(Event event) {
        return event.inputSequence() + ","
            + event.lifecycleSequence() + ","
            + event.marketTimestamp() + ","
            + nullSafe(event.symbol()) + ","
            + nullSafe(event.sourceEventType()) + ","
                + event.eventType().name() + ","
                + event.strategyId() + ","
                + event.signalDirection().name() + ","
                + event.correlationId() + ","
                + nullSafe(event.orderId()) + ","
                + formatDouble(event.price()) + ","
                + event.quantity() + ","
                + event.currentPosition() + ","
                + formatDouble(event.realizedPnl());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }
}
