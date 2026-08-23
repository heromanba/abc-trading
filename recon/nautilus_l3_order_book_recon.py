#!/usr/bin/env python3
"""Replay the shared L3 MBO fixture through Nautilus Trader."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal
from pathlib import Path
from typing import Any

from nautilus_trader.backtest.engine import BacktestEngine
from nautilus_trader.config import BacktestEngineConfig
from nautilus_trader.model import TraderId
from nautilus_trader.model.currencies import USD
from nautilus_trader.model.data import BookOrder, OrderBookDelta, OrderBookDeltas, TradeTick
from nautilus_trader.model.enums import AccountType, AggressorSide, BookAction, BookType, OmsType, OrderSide, TimeInForce
from nautilus_trader.model.identifiers import InstrumentId, TradeId, Venue
from nautilus_trader.model.objects import Money, Price, Quantity
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.trading.strategy import Strategy


def load_fixture(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


def normalize_liquidity_side(value: Any) -> str:
    return {"1": "MAKER", "2": "TAKER"}.get(str(value), str(value))


def build_data(fixture: dict[str, Any], instrument_id: InstrumentId) -> list[Any]:
    snapshot = fixture["snapshot"]
    timestamp = int(snapshot["timestamp_ns"])
    deltas = []
    for order in snapshot["bids"] + snapshot["asks"]:
        deltas.append(OrderBookDelta(
            instrument_id=instrument_id,
            action=BookAction.ADD,
            order=BookOrder(
                OrderSide[order["side"]],
                Price.from_str(f'{order["price"]:.2f}'),
                Quantity.from_int(order["quantity"]),
                int(order["sequence"]),
            ),
            flags=0,
            sequence=int(order["sequence"]),
            ts_event=timestamp,
            ts_init=timestamp,
        ))

    data: list[Any] = [OrderBookDeltas(instrument_id, deltas)]
    for index, event in enumerate(fixture["events"]):
        if event["type"] != "TRADE":
            continue
        data.append(TradeTick(
            instrument_id=instrument_id,
            price=Price.from_str(f'{event["price"]:.2f}'),
            size=Quantity.from_int(event["quantity"]),
            aggressor_side={
                "BUYER": AggressorSide.BUYER,
                "SELLER": AggressorSide.SELLER,
                "NO_AGGRESSOR": AggressorSide.NO_AGGRESSOR,
            }[event["aggressor_side"]],
            trade_id=TradeId(f'L3-{index}'),
            ts_event=int(event["timestamp_ns"]),
            ts_init=int(event["timestamp_ns"]),
        ))
    return data


class L3Strategy(Strategy):
    def __init__(self, instrument: Any, orders: list[dict[str, Any]]) -> None:
        super().__init__()
        self.instrument = instrument
        self.orders = orders
        self.submitted = False
        self.order_names: dict[str, str] = {}
        self.fills: list[dict[str, str]] = []

    def on_start(self) -> None:
        self.subscribe_order_book_deltas(self.instrument.id)

    def on_order_book_deltas(self, deltas: OrderBookDeltas) -> None:
        if self.submitted:
            return
        self.submitted = True
        for order in self.orders:
            if order["type"] == "MARKET":
                created = self.order_factory.market(
                    instrument_id=self.instrument.id,
                    order_side=OrderSide[order["side"]],
                    quantity=Quantity.from_int(order["quantity"]),
                    time_in_force=TimeInForce.GTC,
                )
            else:
                created = self.order_factory.limit(
                    instrument_id=self.instrument.id,
                    order_side=OrderSide[order["side"]],
                    quantity=Quantity.from_int(order["quantity"]),
                    price=Price.from_str(f'{order["price"]:.2f}'),
                    time_in_force=TimeInForce.GTC,
                )
            self.order_names[str(created.client_order_id)] = order["order_id"]
            self.submit_order(created)

    def on_order_filled(self, event: Any) -> None:
        venue_order_id = getattr(event, "venue_order_id", None)
        self.fills.append({
            "order_id": self.order_names[str(event.client_order_id)],
            "price": f"{float(event.last_px):.8f}",
            "quantity": str(int(event.last_qty)),
            "liquidity_side": normalize_liquidity_side(event.liquidity_side),
            "venue_order_id": "" if venue_order_id is None else str(venue_order_id),
        })


def run(input_path: Path, output_path: Path) -> None:
    fixture = load_fixture(input_path)
    instrument = TestInstrumentProvider.equity(str(fixture["symbol"]), str(fixture["venue"]))
    strategy = L3Strategy(instrument, fixture["orders"])
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("L3-RECON-001")))
    engine.add_venue(
        venue=Venue(str(fixture["venue"])),
        oms_type=OmsType.NETTING,
        account_type=AccountType.MARGIN,
        starting_balances=[Money(1_000_000, USD)],
        base_currency=USD,
        default_leverage=Decimal(1),
        book_type=BookType.L3_MBO,
        trade_execution=True,
        queue_position=True,
    )
    engine.add_instrument(instrument)
    engine.add_data(build_data(fixture, instrument.id))
    engine.add_strategy(strategy)
    try:
        engine.run()
    finally:
        engine.dispose()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(
            target,
            fieldnames=("order_id", "price", "quantity", "liquidity_side", "venue_order_id"),
        )
        writer.writeheader()
        writer.writerows(strategy.fills)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("l3_mbo_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_l3_fills.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
