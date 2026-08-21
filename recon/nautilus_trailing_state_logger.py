#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from decimal import Decimal
from pathlib import Path
from typing import Any

from nautilus_trader.backtest.engine import BacktestEngine
from nautilus_trader.config import BacktestEngineConfig
from nautilus_trader.model import TraderId
from nautilus_trader.model.currencies import USD
from nautilus_trader.model.data import IndexPriceUpdate, MarkPriceUpdate, QuoteTick, TradeTick
from nautilus_trader.model.enums import AccountType, AggressorSide, OmsType, OrderSide, TimeInForce, TriggerType, TrailingOffsetType
from nautilus_trader.model.identifiers import InstrumentId, TradeId, Venue
from nautilus_trader.model.objects import Money, Price, Quantity
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.trading.strategy import Strategy


def rows(path: Path) -> list[dict[str, float | int | str]]:
    with path.open(newline="", encoding="utf-8") as source:
        return [{"timestamp_ns": int(r["timestamp_ns"]), "bid": float(r["bid"]), "ask": float(r["ask"]),
                 "last": float(r["last"]), "mark": float(r["mark"]), "index": float(r["index"]),
                 "sequence": int(r["sequence"])} for r in csv.DictReader(source)]


def data(values: list[dict[str, float | int | str]], instrument_id: InstrumentId) -> list[Any]:
    result: list[Any] = []
    for value in values:
        timestamp = int(value["timestamp_ns"])
        result.extend([
            QuoteTick(instrument_id=instrument_id, bid_price=Price(value["bid"], 2), ask_price=Price(value["ask"], 2),
                      bid_size=Quantity.from_int(1), ask_size=Quantity.from_int(1), ts_event=timestamp, ts_init=timestamp),
            TradeTick(instrument_id=instrument_id, price=Price(value["last"], 2), size=Quantity.from_int(1),
                      aggressor_side=AggressorSide.BUYER, trade_id=TradeId(f"T{value['sequence']}"),
                      ts_event=timestamp, ts_init=timestamp),
            MarkPriceUpdate(instrument_id=instrument_id, value=Price(value["mark"], 2), ts_event=timestamp, ts_init=timestamp),
            IndexPriceUpdate(instrument_id=instrument_id, value=Price(value["index"], 2), ts_event=timestamp, ts_init=timestamp),
        ])
    return result


class Strategy(Strategy):
    def __init__(self, instrument: Any) -> None:
        super().__init__()
        self.instrument = instrument
        self.names: dict[str, str] = {}
        self.transitions: list[dict[str, object]] = []

    def on_start(self) -> None:
        market = self.order_factory.trailing_stop_market(
            instrument_id=self.instrument.id, order_side=OrderSide.SELL, quantity=Quantity.from_int(1),
            activation_price=Price(105.0, 2), trailing_offset=Decimal("5"),
            trailing_offset_type=TrailingOffsetType.PRICE, trigger_type=TriggerType.LAST_PRICE,
            time_in_force=TimeInForce.GTC,
        )
        limit = self.order_factory.trailing_stop_limit(
            instrument_id=self.instrument.id, order_side=OrderSide.SELL, quantity=Quantity.from_int(1),
            price=None, activation_price=Price(105.0, 2), limit_offset=Decimal("1"),
            trailing_offset=Decimal("5"), trailing_offset_type=TrailingOffsetType.PRICE,
            trigger_type=TriggerType.LAST_PRICE, time_in_force=TimeInForce.GTC,
        )
        for name, order in (("trailing_market_sell", market), ("trailing_limit_sell", limit)):
            self.names[str(order.client_order_id)] = name
            self.submit_order(order)

    def on_order_accepted(self, event: Any) -> None:
        self.record(event, "ACCEPTED")

    def on_order_triggered(self, event: Any) -> None:
        self.record(event, "TRIGGERED")

    def on_order_filled(self, event: Any) -> None:
        self.record(event, "FILLED")

    def record(self, event: Any, status: str) -> None:
        self.transitions.append({"order_id": self.names[str(event.client_order_id)], "status": status, "sequence": int(event.ts_event)})

    def on_stop(self) -> None:
        pass


def run(input_path: Path, output_path: Path) -> None:
    values = rows(input_path)
    instrument = TestInstrumentProvider.equity("AAPL", "XNAS")
    strategy = Strategy(instrument)
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("TRAILING-RECON-001")))
    engine.add_venue(venue=Venue("XNAS"), oms_type=OmsType.NETTING, account_type=AccountType.MARGIN,
                     starting_balances=[Money(1_000_000, USD)], base_currency=USD, default_leverage=Decimal(1))
    engine.add_instrument(instrument)
    engine.add_data(data(values, instrument.id))
    engine.add_strategy(strategy)
    try:
        engine.run()
    finally:
        engine.dispose()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=("order_id", "status", "sequence"))
        writer.writeheader()
        writer.writerows(strategy.transitions)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("trailing_market_data.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_trailing_states.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
