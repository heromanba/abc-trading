#!/usr/bin/env python3
"""Compare normalized Rust and Java liquidation lifecycle rows."""

from __future__ import annotations

import argparse
import csv
from decimal import Decimal, InvalidOperation
from pathlib import Path


FIELDS = ("event_type", "side", "price", "quantity")
NUMERIC_FIELDS = {"price", "quantity"}


def load(path: Path) -> list[tuple[str, ...]]:
    with path.open(newline="", encoding="utf-8") as source:
        return [tuple(row[field] for field in FIELDS) for row in csv.DictReader(source)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    expected = load(args.expected)
    actual = load(args.actual)
    if len(expected) != len(actual):
        raise SystemExit(f"MISMATCH row_count expected={len(expected)} actual={len(actual)}")
    for index, (expected_row, actual_row) in enumerate(zip(expected, actual), start=1):
        for field_index, field in enumerate(FIELDS):
            expected_value = expected_row[field_index]
            actual_value = actual_row[field_index]
            if field in NUMERIC_FIELDS:
                try:
                    difference = abs(Decimal(expected_value) - Decimal(actual_value))
                except InvalidOperation as error:
                    raise SystemExit(f"MISMATCH row={index} field={field} contains non-decimal data") from error
                if difference > Decimal("0.00001"):
                    raise SystemExit(
                        f"MISMATCH row={index} field={field} expected={expected_value!r} actual={actual_value!r}"
                    )
            elif expected_value != actual_value:
                raise SystemExit(
                    f"MISMATCH row={index} field={field} expected={expected_value!r} actual={actual_value!r}"
                )
    print(f"MATCH liquidation rows={len(actual)}")


if __name__ == "__main__":
    main()
