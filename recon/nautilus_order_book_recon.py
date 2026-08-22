#!/usr/bin/env python3
"""Replay shared L2 depth through Nautilus and emit compact fills."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Any

from nautilus_trader.backtest.engine import BacktestEngine
from nautilus_trader.config import BacktestEngineConfig
from nautilus_trader.model import TraderId
from nautilus_trader.model.currencies import USD
from nautilus_trader.model.data import BookOrder, OrderBookDepth10
from nautilus_trader.model.enums import AccountType, AggressorSide, OmsType, OrderSide, TimeInForce
from nautilus_trader.model.identifiers import InstrumentId, TradeId, Venue
from nautilus_trader.model.objects import Money, Price, Quantity
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.trading.strategy import Strategy


def load(path: Path) -> list[dict[str, float | int | str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return [
            {
                "timestamp_ns": int(row["timestamp_ns"]),
                "symbol": row["symbol"],
                "bid_1_price": float(row["bid_1_price"]),
                "bid_1_quantity": int(row["bid_1_quantity"]),
                "bid_2_price": float(row["bid_2_price"]),
                "bid_2_quantity": int(row["bid_2_quantity"]),
                "ask_1_price": float(row["ask_1_price"]),
                "ask_1_quantity": int(row["ask_1_quantity"]),
                "ask_2_price": float(row["ask_2_price"]),
                "ask_2_quantity": int(row["ask_2_quantity"]),
                "sequence": int(row["sequence"]),
            }
            for row in csv.DictReader(source)
        ]


def build_depth(values: list[dict[str, float | int | str]], instrument_id: InstrumentId) -> list[OrderBookDepth10]:
    result: list[OrderBookDepth10] = []
    for value in values:
        timestamp = int(value["timestamp_ns"])
        bids = [
            BookOrder(OrderSide.BUY, Price(value["bid_1_price"], 2), Quantity.from_int(value["bid_1_quantity"]), 1),
            BookOrder(OrderSide.BUY, Price(value["bid_2_price"], 2), Quantity.from_int(value["bid_2_quantity"]), 2),
        ]
        asks = [
            BookOrder(OrderSide.SELL, Price(value["ask_2_price"], 2), Quantity.from_int(value["ask_2_quantity"]), 2),
            BookOrder(OrderSide.SELL, Price(value["ask_1_price"], 2), Quantity.from_int(value["ask_1_quantity"]), 1),
        ]
        result.append(OrderBookDepth10(
            instrument_id=instrument_id,
            bids=bids,
            asks=asks,
            bid_counts=[1, 1],
            ask_counts=[1, 1],
            flags=0,
            sequence=int(value["sequence"]),
            ts_event=timestamp,
            ts_init=timestamp,
        ))
    return result


class BookStrategy(Strategy):
    def __init__(self, instrument: Any) -> None:
        super().__init__()
        self.instrument = instrument
        self.fills: list[dict[str, object]] = []

    def on_start(self) -> None:
        order = self.order_factory.market(
            instrument_id=self.instrument.id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_int(6),
            time_in_force=TimeInForce.GTC,
        )
        self.submit_order(order)

    def on_order_filled(self, event: Any) -> None:
        self.fills.append({
            "order_id": "market-buy-1",
            "price": f"{float(event.last_px):.8f}",
            "quantity": int(event.last_qty),
            "liquidity_side": {"1": "MAKER", "2": "TAKER"}.get(str(event.liquidity_side), str(event.liquidity_side)),
        })

    def on_stop(self) -> None:
        pass


def run(input_path: Path, output_path: Path) -> None:
    values = load(input_path)
    instrument = TestInstrumentProvider.equity("AAPL", "XNAS")
    strategy = BookStrategy(instrument)
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("BOOK-RECON-001")))
    engine.add_venue(
        venue=Venue("XNAS"), oms_type=OmsType.NETTING, account_type=AccountType.CASH,
        starting_balances=[Money(1_000_000, USD)], base_currency=USD, default_leverage=1,
    )
    engine.add_instrument(instrument)
    engine.add_data(build_depth(values, instrument.id))
    engine.add_strategy(strategy)
    try:
        engine.run()
    finally:
        engine.dispose()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=("order_id", "price", "quantity", "liquidity_side"))
        writer.writeheader()
        writer.writerows(strategy.fills)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("order_book_market_data.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_order_book_fills.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
