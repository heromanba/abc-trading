#!/usr/bin/env python3
"""Replay shared quote/trade/reference snapshots through Nautilus."""

from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Any

from nautilus_trader.backtest.engine import BacktestEngine
from nautilus_trader.config import BacktestEngineConfig
from nautilus_trader.model import TraderId
from nautilus_trader.model.currencies import USD
from nautilus_trader.model.data import IndexPriceUpdate, MarkPriceUpdate, QuoteTick, TradeTick
from nautilus_trader.model.enums import AccountType, AggressorSide, OmsType, OrderSide, TimeInForce, TriggerType
from nautilus_trader.model.identifiers import InstrumentId, TradeId, Venue
from nautilus_trader.model.objects import Money, Price, Quantity
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.trading.strategy import Strategy

ORDER_NAMES = {
    "stop_market_bid_ask": (OrderSide.BUY, TriggerType.BID_ASK, "market"),
    "stop_market_last": (OrderSide.BUY, TriggerType.LAST_PRICE, "market"),
    "stop_limit_last": (OrderSide.BUY, TriggerType.LAST_PRICE, "limit"),
}


def load_rows(path: Path) -> list[dict[str, float | int | str]]:
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


def build_data(rows: list[dict[str, float | int | str]], instrument_id: InstrumentId) -> list[Any]:
    data: list[Any] = []
    for row in rows:
        timestamp = int(row["timestamp_ns"])
        data.extend([
            QuoteTick(
                instrument_id=instrument_id,
                bid_price=Price(float(row["bid"]), 2),
                ask_price=Price(float(row["ask"]), 2),
                bid_size=Quantity.from_int(1),
                ask_size=Quantity.from_int(1),
                ts_event=timestamp,
                ts_init=timestamp,
            ),
            TradeTick(
                instrument_id=instrument_id,
                price=Price(float(row["last"]), 2),
                size=Quantity.from_int(1),
                aggressor_side=AggressorSide.BUYER,
                trade_id=TradeId(f"T{int(row['sequence'])}"),
                ts_event=timestamp,
                ts_init=timestamp,
            ),
            MarkPriceUpdate(
                instrument_id=instrument_id,
                value=Price(float(row["mark"]), 2),
                ts_event=timestamp,
                ts_init=timestamp,
            ),
            IndexPriceUpdate(
                instrument_id=instrument_id,
                value=Price(float(row["index"]), 2),
                ts_event=timestamp,
                ts_init=timestamp,
            ),
        ])
    return data


class TriggerStrategy(Strategy):
    def __init__(self, instrument: Any) -> None:
        super().__init__()
        self.instrument = instrument
        self.order_names: dict[str, str] = {}
        self.transitions: list[dict[str, object]] = []

    def on_start(self) -> None:
        instrument_id = self.instrument.id
        market_bid_ask = self.order_factory.stop_market(
            instrument_id=instrument_id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_int(1),
            trigger_price=Price(105.0, 2),
            trigger_type=TriggerType.BID_ASK,
            time_in_force=TimeInForce.GTC,
        )
        market_last = self.order_factory.stop_market(
            instrument_id=instrument_id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_int(1),
            trigger_price=Price(105.0, 2),
            trigger_type=TriggerType.LAST_PRICE,
            time_in_force=TimeInForce.GTC,
        )
        limit_last = self.order_factory.stop_limit(
            instrument_id=instrument_id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_int(1),
            price=Price(104.0, 2),
            trigger_price=Price(105.0, 2),
            trigger_type=TriggerType.LAST_PRICE,
            time_in_force=TimeInForce.GTC,
        )
        for name, order in (
            ("stop_market_bid_ask", market_bid_ask),
            ("stop_market_last", market_last),
            ("stop_limit_last", limit_last),
        ):
            self.order_names[str(order.client_order_id)] = name
            self.submit_order(order)

    def on_order_accepted(self, event: Any) -> None:
        self._record(event, "ACCEPTED")

    def on_order_triggered(self, event: Any) -> None:
        self._record(event, "TRIGGERED")

    def on_order_filled(self, event: Any) -> None:
        self._record(event, "FILLED")

    def _record(self, event: Any, status: str) -> None:
        order_id = str(event.client_order_id)
        self.transitions.append({
            "order_id": self.order_names.get(order_id, order_id),
            "status": status,
            "sequence": int(event.ts_event),
        })

    def on_stop(self) -> None:
        pass


def run(input_path: Path, output_path: Path) -> None:
    rows = load_rows(input_path)
    instrument = TestInstrumentProvider.equity("AAPL", "XNAS")
    data = build_data(rows, instrument.id)
    strategy = TriggerStrategy(instrument)
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("TRIGGER-RECON-001")))
    engine.add_venue(
        venue=Venue("XNAS"),
        oms_type=OmsType.NETTING,
        account_type=AccountType.CASH,
        starting_balances=[Money(1_000_000, USD)],
        base_currency=USD,
        default_leverage=1,
    )
    engine.add_instrument(instrument)
    engine.add_data(data)
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
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("trigger_market_data.csv"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_trigger_states.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
