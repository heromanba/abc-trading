#!/usr/bin/env python3
"""Compare Java and Nautilus stop-order trigger states from shared snapshots."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

EXPECTED = {
    "stop_market_bid_ask": ["ACCEPTED", "FILLED"],
    "stop_market_last": ["ACCEPTED", "FILLED"],
    "stop_limit_last": ["ACCEPTED", "TRIGGERED", "FILLED"],
}


def load_snapshots(path: Path) -> list[dict[str, float | int | str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return [
            {
                "timestamp_ns": int(row["timestamp_ns"]),
                "symbol": row["symbol"],
                "bid": float(row["bid"]),
                "ask": float(row["ask"]),
                "last": float(row["last"]),
                "mark": float(row["mark"]),
                "index": float(row["index"]),
                "sequence": int(row["sequence"]),
            }
            for row in csv.DictReader(source)
        ]


def load_states(path: Path) -> dict[str, list[str]]:
    with path.open(newline="", encoding="utf-8") as source:
        actual: dict[str, list[str]] = {}
        for row in csv.DictReader(source):
            actual.setdefault(row["order_id"], []).append(row["status"])
    return actual


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("states", type=Path, help="CSV with order_id,status,sequence rows")
    parser.add_argument("actual", type=Path, nargs="?", help="Second backend CSV")
    args = parser.parse_args()
    expected = load_states(args.states)
    actual = load_states(args.actual) if args.actual is not None else expected
    target = EXPECTED if args.actual is None else expected
    if actual != target:
        raise SystemExit(f"MISMATCH expected={target!r} actual={actual!r}")
    print(f"MATCH orders={len(actual)}")


if __name__ == "__main__":
    main()
