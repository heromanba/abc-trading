#!/usr/bin/env python3
"""Compare ordered Java and Nautilus order-book fills."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path


def load(path: Path) -> list[tuple[str, str, str, str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return [
            (row["order_id"], row["price"], row["quantity"], row["liquidity_side"])
            for row in csv.DictReader(source)
        ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    expected = load(args.expected)
    actual = load(args.actual)
    if expected != actual:
        raise SystemExit(f"MISMATCH expected={expected!r} actual={actual!r}")
    print(f"MATCH fills={len(actual)}")


if __name__ == "__main__":
    main()