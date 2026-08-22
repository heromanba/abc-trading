#!/usr/bin/env python3
"""Compare ordered Nautilus and Java lifecycle CSV logs."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

COLUMNS = (
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
    "realized_pnl",
    "commission",
    "commission_currency",
    "liquidity_side",
)
NUMERIC_COLUMNS = {"price", "realized_pnl", "commission"}
NUMERIC_TOLERANCE = 1e-5

COMPARABLE_EVENT_TYPES = {"SIGNAL", "ORDER_FILL", "POSITION_UPDATE"}
IGNORED_EVENT_TYPES = {"ORDER_SUBMIT", "ORDER_ACCEPT", "ORDER_LIMIT_ACCEPT"}


def _canonical_rows(reader: csv.DictReader):
    for row in reader:
        event_type = row["event_type"]
        if event_type in IGNORED_EVENT_TYPES:
            continue
        if event_type in {"POSITION_OPENED", "POSITION_CLOSED", "POSITION_CHANGED"}:
            row["event_type"] = "POSITION_UPDATE"
        if row["event_type"] not in COMPARABLE_EVENT_TYPES:
            continue
        row["signal_direction"] = {"1": "BUY", "2": "SELL"}.get(
            row["signal_direction"], row["signal_direction"]
        )
        yield row


def _fields_match(column: str, expected: str, actual: str) -> bool:
    if column not in NUMERIC_COLUMNS:
        return expected == actual
    try:
        expected_value = 0.0 if expected == "" else float(expected)
        actual_value = 0.0 if actual == "" else float(actual)
        return abs(expected_value - actual_value) <= NUMERIC_TOLERANCE
    except ValueError:
        return expected == actual


def compare(expected_path: Path, actual_path: Path) -> int:
    with expected_path.open(newline="", encoding="utf-8") as expected_file, actual_path.open(
        newline="", encoding="utf-8"
    ) as actual_file:
        expected = csv.DictReader(expected_file)
        actual = csv.DictReader(actual_file)
        if tuple(expected.fieldnames or ()) != COLUMNS or tuple(actual.fieldnames or ()) != COLUMNS:
            raise ValueError("unexpected reconciliation CSV schema")

        expected_rows = _canonical_rows(expected)
        actual_rows = _canonical_rows(actual)
        for row_number, (expected_row, actual_row) in enumerate(zip(expected_rows, actual_rows), start=1):
            expected_row["lifecycle_sequence"] = str(row_number)
            actual_row["lifecycle_sequence"] = str(row_number)
            for column in COLUMNS:
                if column in {"source_event_type", "correlation_id", "order_id", "commission_currency"}:
                    continue
                if column == "strategy_id" and expected_row["event_type"] == "POSITION_UPDATE":
                    continue
                if not _fields_match(column, expected_row[column], actual_row[column]):
                    print(
                        f"MISMATCH row={row_number} column={column} "
                        f"expected={expected_row[column]!r} actual={actual_row[column]!r}"
                    )
                    return 1

        expected_extra = next(expected_rows, None)
        actual_extra = next(actual_rows, None)
        if expected_extra is not None or actual_extra is not None:
            print(f"MISMATCH row count differs after row={row_number}")
            return 1

    print(f"MATCH rows={row_number if 'row_number' in locals() else 0}")
    return 0


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    raise SystemExit(compare(args.expected, args.actual))


if __name__ == "__main__":
    main()
