#!/usr/bin/env python3
"""Compare observable Rust and Java fills for the shared L3 MBO fixture."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


FIELDS = ("order_id", "price", "quantity", "liquidity_side")


def load(path: Path) -> list[tuple[str, ...]]:
    with path.open(newline="", encoding="utf-8") as source:
        rows = []
        for row in csv.DictReader(source):
            liquidity_side = {"1": "MAKER", "2": "TAKER"}.get(
                row["liquidity_side"], row["liquidity_side"]
            )
            rows.append((row["order_id"], row["price"], row["quantity"], liquidity_side))
        return rows


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    expected = load(args.expected)
    actual = load(args.actual)
    if expected != actual:
        raise SystemExit(f"MISMATCH expected={expected!r} actual={actual!r}")
    print(f"MATCH L3 fills={len(actual)}")
    print("venue_order_id comparison: diagnostic only; Nautilus IDs identify client orders")


if __name__ == "__main__":
    main()
