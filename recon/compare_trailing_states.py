#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from pathlib import Path

EXPECTED = {
    "trailing_market_sell": ["ACCEPTED", "FILLED"],
    "trailing_limit_sell": ["ACCEPTED", "TRIGGERED", "FILLED"],
}


def load(path: Path) -> dict[str, list[str]]:
    with path.open(newline="", encoding="utf-8") as source:
        result: dict[str, list[str]] = {}
        for row in csv.DictReader(source):
            result.setdefault(row["order_id"], []).append(row["status"])
        return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("expected", type=Path)
    parser.add_argument("actual", type=Path)
    args = parser.parse_args()
    expected = load(args.expected)
    actual = load(args.actual)
    if expected != actual or actual != EXPECTED:
        raise SystemExit(f"MISMATCH expected={expected!r} actual={actual!r} canonical={EXPECTED!r}")
    print(f"MATCH orders={len(actual)}")


if __name__ == "__main__":
    main()
