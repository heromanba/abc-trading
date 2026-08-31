#!/usr/bin/env python3
"""Emit the final Java account state for the shared linear perpetual fixture."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm
from abc_trading.model.data import MarketDataSnapshot


FIELDS = (
    "currency", "balance_total", "balance_locked", "balance_free",
    "margin_initial", "margin_maintenance", "unrealized_pnl", "equity",
)


def run(input_path: Path, output_path: Path) -> None:
    with input_path.open(encoding="utf-8") as source:
        fixture = json.load(source)
    starting_balance = Decimal(str(fixture["starting_balance"]))
    event_path = output_path.with_name(output_path.stem + "_events.csv")
    engine = BacktestEngine(event_path)
    symbol = fixture["symbol"]
    venue = fixture["venue"]
    engine.add_venue(venue)
    engine.configure_account(venue, starting_balance, fixture["currency"], Decimal(str(fixture["leverage"])))
    engine.add_instrument(symbol, venue, 0.1, "BTC", fixture["currency"], 0.05, 0.025,
                          size_precision=fixture["size_precision"],
                          size_increment=fixture["size_increment"],
                          price_precision=fixture["price_precision"],
                          price_tick_size=fixture["price_tick_size"])
    engine.start()
    try:
        engine.run_market_data([MarketDataSnapshot(
            symbol, 100, fixture["entry_price"], fixture["entry_price"],
            fixture["entry_price"], fixture["entry_price"], fixture["entry_price"], 1,
        )])
        engine.submit_market_order("ACCOUNT_PARITY", symbol, "open-long", 100, 1, "BUY", fixture["quantity"], fixture["entry_price"])
        engine.run_market_data([MarketDataSnapshot(
            symbol, 101, fixture["mark_price"], fixture["mark_price"],
            fixture["mark_price"], fixture["mark_price"], fixture["mark_price"], 2,
        )])
        state = engine.account_state(venue, 101)
        row = {"currency": state["currency"]}
        row.update({field: f'{state[field]:.8f}' for field in FIELDS[1:]})
    finally:
        engine.close()
        shutdown_jvm()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerow(row)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("account_parity_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "java_account_state.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
