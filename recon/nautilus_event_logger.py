#!/usr/bin/env python3
"""Run the shared cached bars through Nautilus and write a canonical event log."""

from __future__ import annotations

import argparse
import csv
import sys
from collections import deque
from pathlib import Path
from typing import Any

import pandas as pd

NAUTILUS_ROOT = Path(__file__).resolve().parents[2] / "nautilus_trader"
try:
    from nautilus_trader.trading.strategy import Strategy
except ImportError:
    class Strategy:  # type: ignore[no-redef]
        pass

COLUMNS = (
    "input_sequence",
    "lifecycle_sequence",
    "market_timestamp",
    "symbol",
    "source_event_type",
    "event_type",
    "strategy_id",
    "signal_direction",
    "correlation_id",
    "order_id",
    "price",
    "quantity",
    "current_position",
    "realized_pnl",
    "commission",
    "commission_currency",
    "liquidity_side",
)


class EventLogger:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._writer = path.open("w", newline="", encoding="utf-8")
        self._csv = csv.DictWriter(self._writer, fieldnames=COLUMNS)
        self._csv.writeheader()
        self._lifecycle_sequence = 0

    def close(self) -> None:
        self._writer.close()

    def write(self, **values: object) -> None:
        self._lifecycle_sequence += 1
        row = {column: "" for column in COLUMNS}
        row.update({key: str(value) for key, value in values.items() if value is not None})
        row["lifecycle_sequence"] = str(self._lifecycle_sequence)
        row["price"] = _format_float(values.get("price"))
        row["realized_pnl"] = _format_float(values.get("realized_pnl"))
        row["commission"] = _format_float(values.get("commission"))
        row["liquidity_side"] = str(values.get("liquidity_side", ""))
        self._csv.writerow(row)
        self._writer.flush()


def _format_float(value: object) -> str:
    if value is None or (isinstance(value, str) and value == ""):
        return ""
    try:
        return f"{float(value):.8f}"
    except (TypeError, ValueError):
        as_decimal = getattr(value, "as_decimal", None)
        if callable(as_decimal):
            return f"{float(as_decimal()):.8f}"
        return str(value).split(" ", 1)[0]


def _value(event: object, name: str, default: object = None) -> object:
    return getattr(event, name, default)


def _canonical_symbol(instrument_id: object) -> str:
    return str(instrument_id).split(".", 1)[0]


def _load_nautilus(root: Path) -> None:
    try:
        import nautilus_trader  # noqa: F401
    except ImportError as error:
        raise RuntimeError(
            "Nautilus is not importable. Build it from its repository root with "
            "`python build.py`, then rerun this script."
        ) from error


def _load_bars(data_dir: Path, symbol: str, start: str, end: str, instrument: Any) -> tuple[Any, list[Any]]:
    from nautilus_trader.model.data import BarType
    from nautilus_trader.persistence.wranglers import BarDataWrangler

    path = data_dir / f"{symbol}_{start}_{end}.csv"
    if not path.exists():
        raise FileNotFoundError(f"Missing immutable input: {path}")
    frame = pd.read_csv(path, index_col=0, parse_dates=True)
    frame.index = pd.to_datetime(frame.index, utc=True)
    frame = frame[["open", "high", "low", "close", "volume"]]
    bar_type = BarType.from_str(f"{instrument.id}-1-DAY-LAST-EXTERNAL")
    bars = BarDataWrangler(bar_type, instrument).process(frame)
    return bar_type, list(bars)


def _source_price_precision(path: Path) -> int:
    from decimal import Decimal

    precision = 0
    with path.open(newline="", encoding="utf-8") as source:
        for row in csv.DictReader(source):
            precision = max(precision, max(0, -Decimal(row["close"]).as_tuple().exponent))
    return precision


def _raw_equity(symbol: str, venue: str, price_precision: int, currency: Any) -> Any:
    from nautilus_trader.model.identifiers import InstrumentId, Symbol, Venue
    from nautilus_trader.model.instruments import Equity
    from nautilus_trader.model.objects import Price, Quantity

    increment = "1" if price_precision == 0 else f"0.{'0' * (price_precision - 1)}1"
    return Equity(
        instrument_id=InstrumentId(Symbol(symbol), Venue(venue)),
        raw_symbol=Symbol(symbol),
        currency=currency,
        price_precision=price_precision,
        price_increment=Price.from_str(increment),
        lot_size=Quantity.from_int(1),
        ts_event=0,
        ts_init=0,
    )


class ReconStrategy(Strategy):
    def __init__(self, instrument: Any, bar_type: Any, strategy_id: str, logger: EventLogger, sequences: dict[tuple[str, int], int]) -> None:
        super().__init__()
        self.instrument = instrument
        self.instrument_id = instrument.id
        self.bar_type = bar_type
        self.strategy_id = strategy_id
        self.logger = logger
        self.sequences = sequences
        self.use_sma = strategy_id == "mean_reversion"
        self.fast = 0.0
        self.slow = 0.0
        self.count = 0
        self.sma_values: deque[float] = deque(maxlen=5)

    def on_start(self) -> None:
        self.subscribe_bars(self.bar_type)

    def on_bar(self, bar: Any) -> None:
        close = float(bar.close)
        self.count += 1
        if self.use_sma:
            self.sma_values.append(close)
            average = sum(self.sma_values) / len(self.sma_values)
            buy_signal = close < average * 0.995
            sell_signal = close > average * 1.005
        else:
            if self.count == 1:
                self.fast = close
                self.slow = close
            else:
                self.fast += (2.0 / 4.0) * (close - self.fast)
                self.slow += (2.0 / 9.0) * (close - self.slow)
            buy_signal = self.fast > self.slow
            sell_signal = self.fast < self.slow

        position = float(self.portfolio.net_position(self.instrument_id))
        ready = self.count >= 5 if self.use_sma else self.count >= 8
        if ready and buy_signal and position == 0:
            self._submit(bar, "BUY")
        elif ready and sell_signal and position > 0:
            self._submit(bar, "SELL")

    def _submit(self, bar: Any, side: str) -> None:
        from nautilus_trader.model.enums import OrderSide, TimeInForce
        from nautilus_trader.model.objects import Quantity

        sequence = self.sequences[(str(self.instrument_id), int(bar.ts_init))]
        canonical_symbol = _canonical_symbol(self.instrument_id)
        correlation_id = f"{canonical_symbol}-{bar.ts_init}-{sequence}"
        self.logger.write(
            input_sequence=sequence,
            market_timestamp=int(bar.ts_init),
            symbol=_canonical_symbol(self.instrument_id),
            source_event_type="StrategySignal",
            event_type="SIGNAL",
            strategy_id=self.strategy_id,
            signal_direction=side,
            correlation_id=correlation_id,
            price=float(bar.close),
            quantity=0,
            current_position=int(self.portfolio.net_position(self.instrument_id)),
            realized_pnl=0.0,
        )
        order_side = OrderSide.BUY if side == "BUY" else OrderSide.SELL
        order = self.order_factory.market(
            instrument_id=self.instrument_id,
            order_side=order_side,
            quantity=Quantity.from_int(10),
            time_in_force=TimeInForce.GTC,
        )
        self.submit_order(order)

    def on_order_accepted(self, event: Any) -> None:
        self._log_order_event(event, "ORDER_ACCEPT")

    def on_order_denied(self, event: Any) -> None:
        self._log_order_event(event, "ORDER_DENY")

    def on_order_filled(self, event: Any) -> None:
        self._log_order_event(event, "ORDER_FILL")

    def on_position_changed(self, event: Any) -> None:
        self._log_position_event(event)

    def on_position_opened(self, event: Any) -> None:
        self._log_position_event(event)

    def on_position_closed(self, event: Any) -> None:
        self._log_position_event(event)

    def _log_order_event(self, event: Any, event_type: str) -> None:
        instrument_id = str(_value(event, "instrument_id", self.instrument_id))
        timestamp = int(_value(event, "ts_event", _value(event, "ts_init", 0)))
        sequence = self.sequences.get((instrument_id, timestamp), 0)
        side = _value(event, "order_side", _value(event, "side", ""))
        commission = _value(event, "commission", 0.0)
        commission_currency = _value(event, "commission_currency", None)
        has_commission = commission is not None and not (isinstance(commission, str) and commission == "")
        if commission_currency is None and has_commission:
            commission_currency = _value(commission, "currency", "USD")
        self.logger.write(
            input_sequence=sequence,
            market_timestamp=timestamp,
            symbol=_canonical_symbol(instrument_id),
            source_event_type=type(event).__name__,
            event_type=event_type,
            strategy_id=self.strategy_id,
            signal_direction=str(side),
            correlation_id=str(_value(event, "client_order_id", "")),
            order_id=str(_value(event, "client_order_id", "")),
            price=_value(event, "last_px", _value(event, "price", "")),
            quantity=_value(event, "last_qty", _value(event, "quantity", 0)),
            current_position=int(self.portfolio.net_position(self.instrument_id)),
            realized_pnl=_value(event, "realized_pnl", 0.0),
            commission=commission,
            commission_currency=commission_currency or "USD",
            liquidity_side={"1": "MAKER", "2": "TAKER"}.get(
                str(_value(event, "liquidity_side", "")),
                str(_value(event, "liquidity_side", "")),
            ),
        )

    def _log_position_event(self, event: Any) -> None:
        timestamp = int(_value(event, "ts_event", _value(event, "ts_init", 0)))
        sequence = self.sequences.get((str(self.instrument_id), timestamp), 0)
        self.logger.write(
            input_sequence=sequence,
            market_timestamp=timestamp,
            symbol=_canonical_symbol(self.instrument_id),
            source_event_type=type(event).__name__,
            event_type="POSITION_UPDATE",
            strategy_id=self.strategy_id,
            signal_direction="HOLD",
            order_id=str(_value(event, "client_order_id", "")),
            quantity=_value(event, "quantity", 0),
            current_position=int(self.portfolio.net_position(self.instrument_id)),
            realized_pnl=_value(event, "realized_pnl", 0.0),
            liquidity_side="",
        )

    def on_stop(self) -> None:
        pass


def run(input_dir: Path, output_path: Path, start: str, end: str, nautilus_root: Path) -> None:
    _load_nautilus(nautilus_root)
    from decimal import Decimal
    from nautilus_trader.backtest.engine import BacktestEngine
    from nautilus_trader.config import BacktestEngineConfig
    from nautilus_trader.model import TraderId
    from nautilus_trader.model.enums import AccountType, CurrencyType, OmsType
    from nautilus_trader.model.objects import Currency, Money
    from nautilus_trader.model.identifiers import Venue

    symbols = ("AAPL", "NVDA")
    recon_currency = Currency(
        code="RUSD",
        precision=15,
        iso4217=0,
        name="Recon decimal USD",
        currency_type=CurrencyType.FIAT,
    )
    instruments = {
        symbol: _raw_equity(
            symbol,
            "XNAS",
            _source_price_precision(input_dir / f"{symbol}_{start}_{end}.csv"),
            recon_currency,
        )
        for symbol in symbols
    }
    prepared = {
        symbol: _load_bars(input_dir, symbol, start, end, instruments[symbol]) for symbol in symbols
    }
    all_bars = sorted(
        (bar for _, (_, bars) in prepared.items() for bar in bars),
        key=lambda bar: (int(bar.ts_init), str(bar.bar_type.instrument_id)),
    )
    sequences = {
        (str(bar.bar_type.instrument_id), int(bar.ts_init)): sequence
        for sequence, bar in enumerate(all_bars, start=1)
    }
    logger = EventLogger(output_path)
    engine = BacktestEngine(config=BacktestEngineConfig(trader_id=TraderId("NAUTILUS-RECON-001")))
    engine.add_venue(
        venue=Venue("XNAS"),
        oms_type=OmsType.NETTING,
        account_type=AccountType.CASH,
        starting_balances=[Money(1_000_000, recon_currency)],
        base_currency=recon_currency,
        default_leverage=Decimal(1),
    )
    for symbol in symbols:
        engine.add_instrument(instruments[symbol])
        engine.add_data(prepared[symbol][1])
        strategy_id = "momentum" if symbol == "AAPL" else "mean_reversion"
        engine.add_strategy(ReconStrategy(instruments[symbol], prepared[symbol][0], strategy_id, logger, sequences))
    try:
        engine.run()
    finally:
        engine.dispose()
        logger.close()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", type=Path, default=Path(__file__).parent / "output" / "immutable_data")
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "output" / "nautilus_events.csv")
    parser.add_argument("--start", default="2023-01-01")
    parser.add_argument("--end", default="2024-01-01")
    parser.add_argument("--nautilus-root", type=Path, default=Path(__file__).resolve().parents[2] / "nautilus_trader")
    args = parser.parse_args()
    run(args.input_dir, args.output, args.start, args.end, args.nautilus_root)
    print(f"Nautilus event log written to {args.output}")


if __name__ == "__main__":
    main()
