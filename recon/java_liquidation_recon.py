#!/usr/bin/env python3
"""Replay the shared liquidation fixture through the Java backtest runtime."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm


def run(input_path: Path, output_path: Path) -> None:
    with input_path.open(encoding="utf-8") as source:
        fixture = json.load(source)
    event_path = output_path.with_name(output_path.stem + "_events.csv")
    engine = BacktestEngine(event_path)
    symbol = fixture["symbol"]
    venue = fixture["venue"]
    engine.add_venue(venue)
    engine.configure_account(venue, fixture["starting_balance"], fixture["account_currency"], fixture["leverage"])
    engine.add_instrument(
        symbol,
        venue,
        fixture["tick_size"],
        symbol,
        "USD",
        0.0,
        0.0,
        "FIXED_PER_UNIT",
        0.0,
        0.00000001,
    )
    engine.start()
    try:
        engine.run_market_data([
            __import__("abc_trading.model.data", fromlist=["MarketDataSnapshot"]).MarketDataSnapshot(
                symbol, 100, fixture["entry_price"], fixture["entry_price"],
                fixture["entry_price"], fixture["entry_price"], fixture["entry_price"], 1,
            )
        ])
        engine.submit_market_order("LIQUIDATION_FIXTURE", symbol, "open-long", 100, 1, "BUY", fixture["quantity"], fixture["entry_price"])
        engine.submit_limit_order("LIQUIDATION_FIXTURE", symbol, "resting-sell", 100, 2, "SELL", 1, fixture["open_order_price"])
        engine.run_market_data([
            __import__("abc_trading.model.data", fromlist=["MarketDataSnapshot"]).MarketDataSnapshot(
                symbol, 101, fixture["crash_price"], fixture["crash_price"],
                fixture["crash_price"], fixture["crash_price"], fixture["crash_price"], 2,
            )
        ])
    finally:
        engine.close()
        shutdown_jvm()

    rows: list[dict[str, str]] = []
    with event_path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row["event_type"] in {
                "LIQUIDATION_STARTED", "ORDER_CANCEL", "LIQUIDATION_FILL", "LIQUIDATION_COMPLETED"
            }:
                rows.append({
                    "event_type": row["event_type"],
                    "side": row["signal_direction"],
                    "price": row["price"],
                    "quantity": row["quantity"],
                })
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=("event_type", "side", "price", "quantity"))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("liquidation_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "java_liquidation_events.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
