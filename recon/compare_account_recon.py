#!/usr/bin/env python3
"""Compare final Rust and Java account-state snapshots."""

from __future__ import annotations

import argparse
import csv
from decimal import Decimal, InvalidOperation
from pathlib import Path


FIELDS = (
    "currency", "balance_total", "balance_locked", "balance_free",
    "margin_initial", "margin_maintenance", "unrealized_pnl", "equity",
)
NUMERIC_FIELDS = set(FIELDS[1:])
TOLERANCE = Decimal("0.00001")


def load(path: Path) -> dict[str, str]:
    with path.open(newline="", encoding="utf-8") as source:
        rows = list(csv.DictReader(source))
    if len(rows) != 1:
        raise ValueError(f"expected one account row in {path}, got {len(rows)}")
    return rows[0]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    expected = load(args.expected)
    actual = load(args.actual)
    for field in FIELDS:
        if field in NUMERIC_FIELDS:
            try:
                difference = abs(Decimal(expected[field]) - Decimal(actual[field]))
            except InvalidOperation as error:
                raise SystemExit(f"MISMATCH field={field} contains non-decimal data") from error
            if difference > TOLERANCE:
                raise SystemExit(
                    f"MISMATCH field={field} expected={expected[field]!r} actual={actual[field]!r}"
                )
        elif expected[field] != actual[field]:
            raise SystemExit(
                f"MISMATCH field={field} expected={expected[field]!r} actual={actual[field]!r}"
            )
    print("MATCH account state fields=8")


if __name__ == "__main__":
    main()
