#!/usr/bin/env python3
"""Replay shared L2 depth through Java and emit compact fills."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm
from abc_trading.model.data import OrderBookSnapshot


def load(path: Path) -> list[OrderBookSnapshot]:
    with path.open(newline="", encoding="utf-8") as source:
        return [
            OrderBookSnapshot(
                row["symbol"], int(row["timestamp_ns"]),
                [(float(row["bid_1_price"]), int(row["bid_1_quantity"])),
                 (float(row["bid_2_price"]), int(row["bid_2_quantity"]))],
                [(float(row["ask_1_price"]), int(row["ask_1_quantity"])),
                 (float(row["ask_2_price"]), int(row["ask_2_quantity"]))],
                int(row["sequence"]),
            )
            for row in csv.DictReader(source)
        ]


def run(input_path: Path, output_path: Path) -> None:
    event_path = output_path.with_name(output_path.stem + "_events.csv")
    engine = BacktestEngine(event_path)
    engine.add_venue("XNAS")
    engine.add_instrument("AAPL", "XNAS")
    engine.start()
    snapshots = load(input_path)
    try:
        engine.run_order_books(snapshots[:1])
        engine.submit_market_order("book", "AAPL", "market-buy-1", 100, 1, "BUY", 6, 100.0)
        engine.submit_market_order("book", "AAPL", "market-sell-1", 100, 1, "SELL", 7, 100.0)
        engine.submit_limit_order("book", "AAPL", "limit-buy-1", 100, 1, "BUY", 2, 100.0)
        engine.run_order_books(snapshots[1:])
    finally:
        engine.close()
        shutdown_jvm()
    fills: list[dict[str, object]] = []
    with event_path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row["event_type"] == "ORDER_FILL":
                fills.append({
                    "order_id": row["order_id"],
                    "price": row["price"],
                    "quantity": row["quantity"],
                    "liquidity_side": row["liquidity_side"],
                })
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=("order_id", "price", "quantity", "liquidity_side"))
        writer.writeheader()
        writer.writerows(fills)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("order_book_market_data.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "java_order_book_fills.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
