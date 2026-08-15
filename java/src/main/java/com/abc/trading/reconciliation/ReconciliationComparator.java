package com.abc.trading.reconciliation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Compares ordered lifecycle CSV rows and reports the first structural mismatch. */
public final class ReconciliationComparator {
    private static final List<String> REQUIRED_COLUMNS = List.of(
            "input_sequence",
            "lifecycle_sequence",
            "market_timestamp",
            "symbol",
            "source_event_type",
            "event_type",
            "strategy_id",
            "signal_direction",
            "correlation_id",
            "order_id",
            "price",
            "quantity",
            "current_position",
            "realized_pnl");

    public ReconciliationResult compare(Path expected, Path actual) {
        try (BufferedReader left = Files.newBufferedReader(expected);
             BufferedReader right = Files.newBufferedReader(actual)) {
            String leftHeader = left.readLine();
            String rightHeader = right.readLine();
            validateHeader(leftHeader, expected);
            validateHeader(rightHeader, actual);

            long row = 0;
            while (true) {
                String leftLine = left.readLine();
                String rightLine = right.readLine();
                if (leftLine == null && rightLine == null) {
                    return ReconciliationResult.matched(row);
                }
                row++;
                if (leftLine == null || rightLine == null) {
                    return ReconciliationResult.mismatch(row, "row count differs");
                }
                List<String> leftFields = split(leftLine);
                List<String> rightFields = split(rightLine);
                if (leftFields.size() != REQUIRED_COLUMNS.size()
                        || rightFields.size() != REQUIRED_COLUMNS.size()) {
                    return ReconciliationResult.mismatch(row, "column count differs");
                }
                for (int index = 0; index < REQUIRED_COLUMNS.size(); index++) {
                    if (!leftFields.get(index).equals(rightFields.get(index))) {
                        return ReconciliationResult.mismatch(
                                row,
                                REQUIRED_COLUMNS.get(index) + " differs: expected '"
                                        + leftFields.get(index) + "', actual '"
                                        + rightFields.get(index) + "'");
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to compare reconciliation logs", exception);
        }
    }

    private static List<String> split(String line) {
        return new ArrayList<>(Arrays.asList(line.split(",", -1)));
    }

    private static void validateHeader(String header, Path path) {
        if (header == null || !split(header).equals(REQUIRED_COLUMNS)) {
            throw new IllegalArgumentException("Unexpected reconciliation header: " + path);
        }
    }
}
