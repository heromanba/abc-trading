#!/usr/bin/env python3
"""Emit the final Nautilus account state for the shared linear perpetual fixture."""

from __future__ import annotations

import argparse
import csv
import json
from decimal import Decimal
from pathlib import Path
from typing import Any

from nautilus_trader.core.nautilus_pyo3.backtest import BacktestEngine, BacktestEngineConfig
from nautilus_trader.core.nautilus_pyo3.model import (
    AccountType, Currency, CryptoPerpetual, Money, OmsType, OrderSide, Price,
    Quantity, QuoteTick, TimeInForce, TraderId, Venue,
)
from nautilus_trader.core.nautilus_pyo3.trading import Strategy
from nautilus_trader.test_kit.providers import TestInstrumentProvider


FIELDS = (
    "currency", "balance_total", "balance_locked", "balance_free",
    "margin_initial", "margin_maintenance", "unrealized_pnl", "equity",
)


class AccountParityStrategy(Strategy):
    def __new__(cls, instrument: Any, fixture: dict[str, Any]) -> "AccountParityStrategy":
        return super().__new__(cls)

    def __init__(self, instrument: Any, fixture: dict[str, Any]) -> None:
        super().__init__()
        self.instrument = instrument
        self.fixture = fixture

    def on_start(self) -> None:
        quantity = Decimal(str(self.fixture["quantity"]))
        order = self.order_factory.market(
            instrument_id=self.instrument.id,
            order_side=OrderSide.BUY,
            quantity=Quantity.from_str(f"{quantity:.3f}"),
            time_in_force=TimeInForce.GTC,
        )
        self.submit_order(order)


def zero_fee_instrument() -> Any:
    source = TestInstrumentProvider.btcusdt_perp_binance()
    values = source.to_dict(source)
    values["maker_fee"] = "0.000000"
    values["taker_fee"] = "0.000000"
    return CryptoPerpetual.from_dict(values)


def quote(instrument: Any, timestamp: int, price: float, quantity: Decimal) -> Any:
    return QuoteTick(
        instrument_id=instrument.id,
        bid_price=Price.from_str(f"{price:.1f}"),
        ask_price=Price.from_str(f"{price:.1f}"),
        bid_size=Quantity.from_str(f"{quantity:.3f}"),
        ask_size=Quantity.from_str(f"{quantity:.3f}"),
        ts_event=timestamp,
        ts_init=timestamp,
    )


def run(input_path: Path, output_path: Path) -> None:
    with input_path.open(encoding="utf-8") as source:
        fixture = json.load(source)
    quantity = Decimal(str(fixture["quantity"]))
    instrument = zero_fee_instrument()
    strategy = AccountParityStrategy(instrument, fixture)
    engine = BacktestEngine(BacktestEngineConfig(trader_id=TraderId("ACCT-PARITY-001")))
    venue = Venue(str(fixture["venue"]))
    currency = Currency.from_str(fixture["currency"])
    engine.add_venue(
        venue=venue,
        oms_type=OmsType.NETTING,
        account_type=AccountType.MARGIN,
        starting_balances=[Money(fixture["starting_balance"], currency)],
        base_currency=currency,
        default_leverage=fixture["leverage"],
        trade_execution=True,
    )
    engine.add_instrument(instrument)
    engine.add_data([
        quote(instrument, 100, fixture["entry_price"], quantity),
        quote(instrument, 101, fixture["mark_price"], quantity),
    ])
    engine.add_strategy(strategy)
    try:
        engine.run()
        account = engine.portfolio.account(venue=venue)
        total = account.balance_total(currency).as_double()
        locked = account.balance_locked(currency).as_double()
        free = account.balance_free(currency).as_double()
        initial = sum(value.as_double() for value in account.initial_margins().values())
        maintenance = sum(value.as_double() for value in account.maintenance_margins().values())
        unrealized = engine.portfolio.unrealized_pnl(instrument.id).as_double()
        row = {
            "currency": str(currency),
            "balance_total": f"{total:.8f}",
            "balance_locked": f"{locked:.8f}",
            "balance_free": f"{free:.8f}",
            "margin_initial": f"{initial:.8f}",
            "margin_maintenance": f"{maintenance:.8f}",
            "unrealized_pnl": f"{unrealized:.8f}",
            "equity": f"{total + unrealized:.8f}",
        }
    finally:
        engine.dispose()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerow(row)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path(__file__).with_name("account_parity_market_data.json"))
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_account_state.csv")
    args = parser.parse_args()
    run(args.input, args.output)


if __name__ == "__main__":
    main()
