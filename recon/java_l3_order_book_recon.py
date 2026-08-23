#!/usr/bin/env python3
"""Replay the shared L3 MBO fixture through the Java backend."""

from __future__ import annotations

import argparse
import csv
import json
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm
from abc_trading.model.data import OrderBookL3Snapshot, TradeTick, VenueOrder


def load_fixture(path: Path) -> dict[str, object]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def run(input_path: Path, output_path: Path) -> None:
    fixture = load_fixture(input_path)
    symbol = str(fixture["symbol"])
    venue = str(fixture["venue"])
    snapshot_data = fixture["snapshot"]
    assert isinstance(snapshot_data, dict)

    snapshot = OrderBookL3Snapshot(
        symbol,
        int(snapshot_data["timestamp_ns"]),
        [VenueOrder(order["order_id"], order["side"], float(order["price"]),
                    int(order["quantity"]), int(order["sequence"]))
         for order in snapshot_data["bids"]],
        [VenueOrder(order["order_id"], order["side"], float(order["price"]),
                    int(order["quantity"]), int(order["sequence"]))
         for order in snapshot_data["asks"]],
        int(snapshot_data["sequence"]),
    )

    event_path = output_path.with_name(output_path.stem + "_events.csv")
    engine = BacktestEngine(event_path)
    engine.add_venue(venue)
    engine.configure_account(venue, 1_000_000.0, "USD", 1.0)
    engine.add_instrument(symbol, venue)
    engine.start()
    try:
        engine.run_order_books_l3([snapshot])
        for order in fixture["orders"]:
            if order["type"] == "MARKET":
                engine.submit_market_order(
                    "l3-recon", symbol, order["order_id"], int(order["timestamp_ns"]),
                    int(order["sequence"]), order["side"], int(order["quantity"]),
                    float(order["price"]),
                )
            else:
                engine.submit_limit_order(
                    "l3-recon", symbol, order["order_id"], int(order["timestamp_ns"]),
                    int(order["sequence"]), order["side"], int(order["quantity"]),
                    float(order["price"]),
                )
        trades = [
            TradeTick(
                symbol,
                int(event["timestamp_ns"]),
                float(event["price"]),
                int(event["quantity"]),
                event["aggressor_side"],
                int(event["sequence"]),
            )
            for event in fixture["events"]
            if event["type"] == "TRADE"
        ]
        engine.run_trade_ticks(trades)
    finally:
        engine.close()
        shutdown_jvm()

    fills: list[dict[str, str]] = []
    with event_path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            if row["event_type"] == "ORDER_FILL":
                fills.append({
                    "order_id": row["order_id"],
                    "price": row["price"],
                    "quantity": row["quantity"],
                    "liquidity_side": row["liquidity_side"],
                    "venue_order_id": row["venue_order_id"],
                })

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(
            target,
            fieldnames=("order_id", "price", "quantity", "liquidity_side", "venue_order_id"),
        )
        writer.writeheader()
        writer.writerows(fills)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("l3_mbo_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "java_l3_fills.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
