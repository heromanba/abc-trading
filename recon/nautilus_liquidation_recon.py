#!/usr/bin/env python3
"""Replay the shared liquidation fixture through Nautilus Trader."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal
from pathlib import Path
from typing import Any

from nautilus_trader.core.nautilus_pyo3.backtest import BacktestEngine
from nautilus_trader.core.nautilus_pyo3.backtest import BacktestEngineConfig
from nautilus_trader.core.nautilus_pyo3.model import AccountType, Currency, Money, OmsType, Price, Quantity, QuoteTick
from nautilus_trader.core.nautilus_pyo3.model import CryptoPerpetual, TraderId, Venue
from nautilus_trader.core.nautilus_pyo3.model import OrderSide, TimeInForce
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.core.nautilus_pyo3.trading import Strategy


def load_fixture(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as source:
        return json.load(source)


class LiquidationStrategy(Strategy):
    def __new__(cls, instrument: Any, fixture: dict[str, Any]) -> "LiquidationStrategy":
        return super().__new__(cls)

    def __init__(self, instrument: Any, fixture: dict[str, Any]) -> None:
        super().__init__()
        self.instrument = instrument
        self.fixture = fixture
        self.order_names: dict[str, str] = {}
        self.canceled: list[dict[str, str]] = []
        self.liquidation_fill: dict[str, str] | None = None

    def on_start(self) -> None:
        market = self.order_factory.market(
            instrument_id=self.instrument.id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_int(self.fixture["quantity"]),
            time_in_force=TimeInForce.GTC,
        )
        resting = self.order_factory.limit(
            instrument_id=self.instrument.id,
            order_side=OrderSide.SELL,
            quantity=Quantity.from_int(1),
            price=Price.from_str(f'{self.fixture["open_order_price"]:.1f}'),
            time_in_force=TimeInForce.GTC,
        )
        self.order_names[str(market.client_order_id)] = "open-long"
        self.order_names[str(resting.client_order_id)] = "resting-sell"
        self.submit_order(market)
        self.submit_order(resting)

    def on_order_canceled(self, event: Any) -> None:
        if str(event.client_order_id) in self.order_names:
            self.canceled.append({
                "event_type": "ORDER_CANCEL",
                "side": "HOLD",
                "price": "0.00000000",
                "quantity": "0",
            })

    def on_order_filled(self, event: Any) -> None:
        if str(event.client_order_id) in self.order_names:
            return
        self.liquidation_fill = {
            "event_type": "LIQUIDATION_FILL",
            "side": str(event.order_side),
            "price": f"{float(event.last_px):.8f}",
            "quantity": str(int(event.last_qty)),
        }


def make_quotes(fixture: dict[str, Any], instrument: Any) -> list[QuoteTick]:
    return [
        QuoteTick(
            instrument_id=instrument.id,
            bid_price=Price.from_str(f'{price:.1f}'),
            ask_price=Price.from_str(f'{price:.1f}'),
            bid_size=Quantity.from_int(fixture["quantity"]),
            ask_size=Quantity.from_int(fixture["quantity"]),
            ts_event=timestamp,
            ts_init=timestamp,
        )
        for timestamp, price in ((100, fixture["entry_price"]), (101, fixture["crash_price"]))
    ]


def run(input_path: Path, output_path: Path) -> None:
    fixture = load_fixture(input_path)
    instrument = CryptoPerpetual.from_dict(TestInstrumentProvider.xbtusd_bitmex().to_dict(TestInstrumentProvider.xbtusd_bitmex()))
    strategy = LiquidationStrategy(instrument, fixture)
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("LIQ-RECON-001")))
    engine.add_venue(
        venue=Venue(str(fixture["venue"])),
        oms_type=OmsType.NETTING,
        account_type=AccountType.MARGIN,
        starting_balances=[Money(1.0, Currency.from_str("BTC"))],
        base_currency=Currency.from_str("BTC"),
        default_leverage=Decimal(1),
        liquidation_enabled=True,
        liquidation_cancel_open_orders=True,
    )
    engine.add_instrument(instrument)
    engine.add_data(make_quotes(fixture, instrument))
    engine.add_strategy(strategy)
    try:
        engine.run()
    finally:
        engine.dispose()

    rows: list[dict[str, str]] = []
    if strategy.liquidation_fill is not None:
        rows.append({
            "event_type": "LIQUIDATION_STARTED",
            "side": "HOLD",
            "price": "0.00000000",
            "quantity": str(fixture["quantity"]),
        })
        rows.extend(strategy.canceled)
        rows.append(strategy.liquidation_fill)
        rows.append({
            "event_type": "LIQUIDATION_COMPLETED",
            "side": "HOLD",
            "price": "0.00000000",
            "quantity": "0",
        })

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=("event_type", "side", "price", "quantity"))
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("liquidation_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_liquidation_events.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
