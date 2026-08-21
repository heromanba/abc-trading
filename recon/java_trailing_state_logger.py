#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm
from abc_trading.model.data import MarketDataSnapshot


def load(path: Path) -> list[MarketDataSnapshot]:
    with path.open(newline="", encoding="utf-8") as source:
        return [
            MarketDataSnapshot(row["symbol"], int(row["timestamp_ns"]), float(row["bid"]), float(row["ask"]),
                               float(row["last"]), float(row["mark"]), float(row["index"]), int(row["sequence"]))
            for row in csv.DictReader(source)
        ]


def run(input_path: Path, output_path: Path) -> None:
    engine = BacktestEngine(output_path.with_name(output_path.stem + "_events.csv"))
    engine.add_venue("XNAS")
    engine.add_instrument("AAPL", "XNAS")
    engine.start()
    snapshots = load(input_path)
    try:
        engine.run_market_data(snapshots[:1])
        engine.submit_trailing_stop_market_order("trailing", "AAPL", "trailing_market_sell", "SELL", 1, 100, 105.0, 5.0)
        engine.submit_trailing_stop_limit_order("trailing", "AAPL", "trailing_limit_sell", "SELL", 1, 100, 0.0, 105.0, 1.0, 5.0)
        order_ids = ("trailing_market_sell", "trailing_limit_sell")
        previous = {order_id: engine.order_status(order_id) for order_id in order_ids}
        rows = [{"order_id": order_id, "status": previous[order_id], "sequence": 1} for order_id in order_ids]
        for snapshot in snapshots[1:]:
            engine.run_market_data([snapshot])
            for order_id in order_ids:
                status = engine.order_status(order_id)
                if status != previous[order_id]:
                    rows.append({"order_id": order_id, "status": status, "sequence": snapshot.sequence})
                    previous[order_id] = status
        with output_path.open("w", newline="", encoding="utf-8") as target:
            writer = csv.DictWriter(target, fieldnames=("order_id", "status", "sequence"))
            writer.writeheader()
            writer.writerows(rows)
    finally:
        engine.close()
        shutdown_jvm()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("trailing_market_data.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "java_trailing_states.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
