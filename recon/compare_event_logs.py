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
)


def compare(expected_path: Path, actual_path: Path) -> int:
    with expected_path.open(newline="", encoding="utf-8") as expected_file, actual_path.open(
        newline="", encoding="utf-8"
    ) as actual_file:
        expected = csv.DictReader(expected_file)
        actual = csv.DictReader(actual_file)
        if tuple(expected.fieldnames or ()) != COLUMNS or tuple(actual.fieldnames or ()) != COLUMNS:
            raise ValueError("unexpected reconciliation CSV schema")

        for row_number, (expected_row, actual_row) in enumerate(zip(expected, actual), start=1):
            for column in COLUMNS:
                if expected_row[column] != actual_row[column]:
                    print(
                        f"MISMATCH row={row_number} column={column} "
                        f"expected={expected_row[column]!r} actual={actual_row[column]!r}"
                    )
                    return 1

        expected_extra = next(expected, None)
        actual_extra = next(actual, None)
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
