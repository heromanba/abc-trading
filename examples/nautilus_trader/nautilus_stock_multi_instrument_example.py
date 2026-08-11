#!/usr/bin/env python3
"""Backtest a multi-instrument stock portfolio with two simple Nautilus strategies.

This example downloads free historical daily OHLCV data from Yahoo Finance for
AAPL and NVDA, converts the data into Nautilus bars, runs a backtest in the
Nautilus engine, and writes a markdown report that visualizes the end-to-end flow.
"""

from __future__ import annotations

from decimal import Decimal
from pathlib import Path
from typing import Any

import pandas as pd
import yfinance as yf

from nautilus_trader.backtest.engine import BacktestEngine
from nautilus_trader.config import BacktestEngineConfig, LoggingConfig
from nautilus_trader.indicators import ExponentialMovingAverage, SimpleMovingAverage
from nautilus_trader.model.currencies import USD
from nautilus_trader.model.data import Bar, BarType
from nautilus_trader.model.enums import AccountType, OmsType, OrderSide
from nautilus_trader.model.identifiers import InstrumentId, Venue
from nautilus_trader.model.objects import Money
from nautilus_trader.persistence.wranglers import BarDataWrangler
from nautilus_trader.test_kit.providers import TestInstrumentProvider
from nautilus_trader.trading.config import StrategyConfig
from nautilus_trader.trading.strategy import Strategy

ROOT = Path(__file__).resolve().parent
OUTPUT_REPORT = ROOT / "nautilus_stock_data_flow_report.md"


class MomentumStrategyConfig(StrategyConfig, frozen=True):
    instrument_id: InstrumentId
    bar_type: BarType
    trade_size: Decimal
    fast_ema_period: int = 3
    slow_ema_period: int = 8


class MomentumStrategy(Strategy):
    def __init__(self, config: MomentumStrategyConfig) -> None:
        super().__init__(config=config)
        self.fast_ema = ExponentialMovingAverage(config.fast_ema_period)
        self.slow_ema = ExponentialMovingAverage(config.slow_ema_period)
        self.trace: list[dict[str, Any]] = []
        self.orders_submitted = 0
        self.signals = 0

    def on_start(self) -> None:
        self.register_indicator_for_bars(self.config.bar_type, self.fast_ema)
        self.register_indicator_for_bars(self.config.bar_type, self.slow_ema)
        self.subscribe_bars(self.config.bar_type)

    def on_bar(self, bar: Bar) -> None:
        if not self.indicators_initialized():
            return

        signal = "flat"
        if self.fast_ema.value > self.slow_ema.value and self.portfolio.is_flat(self.config.instrument_id):
            self._submit_order(OrderSide.BUY)
            signal = "buy"
        elif self.fast_ema.value < self.slow_ema.value and self.portfolio.is_net_long(self.config.instrument_id):
            self._submit_order(OrderSide.SELL)
            signal = "sell"

        self.trace.append(
            {
                "strategy": "momentum",
                "ts": str(bar.ts_init),
                "bar_close": float(bar.close),
                "signal": signal,
                "position": int(self.portfolio.net_position(self.config.instrument_id)),
                "unrealized_pnl": float(self.portfolio.unrealized_pnl(self.config.instrument_id)),
            },
        )

    def _submit_order(self, side: OrderSide) -> None:
        instrument = self.cache.instrument(self.config.instrument_id)
        order = self.order_factory.market(
            self.config.instrument_id,
            side,
            instrument.make_qty(self.config.trade_size),
        )
        self.submit_order(order)
        self.orders_submitted += 1
        self.signals += 1


class MeanReversionStrategyConfig(StrategyConfig, frozen=True):
    instrument_id: InstrumentId
    bar_type: BarType
    trade_size: Decimal
    sma_period: int = 5


class MeanReversionStrategy(Strategy):
    def __init__(self, config: MeanReversionStrategyConfig) -> None:
        super().__init__(config=config)
        self.sma = SimpleMovingAverage(config.sma_period)
        self.trace: list[dict[str, Any]] = []
        self.orders_submitted = 0
        self.signals = 0

    def on_start(self) -> None:
        self.register_indicator_for_bars(self.config.bar_type, self.sma)
        self.subscribe_bars(self.config.bar_type)

    def on_bar(self, bar: Bar) -> None:
        if not self.indicators_initialized():
            return

        signal = "flat"
        close_price = float(bar.close)
        sma_value = float(self.sma.value)
        if close_price < sma_value * 0.995 and self.portfolio.is_flat(self.config.instrument_id):
            self._submit_order(OrderSide.BUY)
            signal = "buy"
        elif close_price > sma_value * 1.005 and self.portfolio.is_net_long(self.config.instrument_id):
            self._submit_order(OrderSide.SELL)
            signal = "sell"

        self.trace.append(
            {
                "strategy": "mean_reversion",
                "ts": str(bar.ts_init),
                "bar_close": close_price,
                "signal": signal,
                "position": int(self.portfolio.net_position(self.config.instrument_id)),
                "unrealized_pnl": float(self.portfolio.unrealized_pnl(self.config.instrument_id)),
            },
        )

    def _submit_order(self, side: OrderSide) -> None:
        instrument = self.cache.instrument(self.config.instrument_id)
        order = self.order_factory.market(
            self.config.instrument_id,
            side,
            instrument.make_qty(self.config.trade_size),
        )
        self.submit_order(order)
        self.orders_submitted += 1
        self.signals += 1


def download_symbol_data(symbol: str, start: str, end: str) -> pd.DataFrame:
    df = yf.download(symbol, start=start, end=end, progress=False, auto_adjust=False)
    if df.empty:
        raise RuntimeError(f"No data returned for {symbol}")

    if isinstance(df.columns, pd.MultiIndex):
        ticker_level = df.columns.get_level_values(1)
        if symbol in ticker_level:
            df = df.xs(symbol, axis=1, level=1)
        else:
            df = df.droplevel(0, axis=1)

    cols = {"Open": "open", "High": "high", "Low": "low", "Close": "close", "Volume": "volume"}
    available = [c for c in df.columns if c in cols]
    rename_map = {c: cols[c] for c in available}
    df = df.rename(columns=rename_map)

    needed = ["open", "high", "low", "close", "volume"]
    missing = [c for c in needed if c not in df.columns]
    if missing:
        raise RuntimeError(f"Missing expected OHLCV columns for {symbol}: {missing}")

    df = df[needed].copy()
    df.index = pd.to_datetime(df.index, utc=True)
    return df


def build_bars(symbol: str) -> tuple[BarType, list[Bar], Any]:
    instrument = TestInstrumentProvider.equity(symbol=symbol, venue="XNAS")
    bar_type = BarType.from_str(f"{symbol}.XNAS-1-DAY-LAST-EXTERNAL")
    data_df = download_symbol_data(symbol=symbol, start="2023-01-01", end="2024-01-01")
    bars = BarDataWrangler(bar_type, instrument).process(data_df)
    return bar_type, bars, instrument


def write_markdown_report(all_bars_summary: dict[str, Any], strategies: list[dict[str, Any]], output_path: Path) -> None:
    lines = [
        "# Nautilus multi-instrument stock backtest report",
        "",
        "## Overview",
        "",
        "This report captures a small Nautilus backtest that uses historical daily OHLCV data from Yahoo Finance for AAPL and NVDA.",
        "It demonstrates a single portfolio with multiple instruments and two simple example strategies:",
        "- a momentum strategy for AAPL",
        "- a mean reversion strategy for NVDA",
        "",
        "## Data flow",
        "",
        "```mermaid",
        "flowchart LR",
        "A[Yahoo Finance API] --> B[Historical OHLCV DataFrame]",
        "B --> C[BarDataWrangler]",
        "C --> D[Nautilus BacktestEngine]",
        "D --> E[Momentum Strategy]",
        "D --> F[Mean Reversion Strategy]",
        "E --> G[Shared Portfolio]",
        "F --> G",
        "G --> H[Orders / Fills]",
        "```",
        "",
        "## Instruments and data",
        "",
    ]

    for symbol, info in all_bars_summary.items():
        lines.append(f"- {symbol}: {info['bar_count']} bars from {info['start']} to {info['end']}")

    lines.extend([
        "",
        "## Strategy outcomes",
        "",
    ])

    for strategy in strategies:
        lines.append(f"- {strategy['name']}: {strategy['signals']} signals, {strategy['orders']} orders, latest position {strategy['latest_position']}")

    lines.extend([
        "",
        "## Sample trace entries",
        "",
        "| strategy | timestamp | signal | close | position |",
        "| --- | --- | --- | ---: | ---: |",
    ])

    for strategy in strategies:
        for entry in strategy["trace"][:3]:
            lines.append(
                f"| {entry['strategy']} | {entry['ts']} | {entry['signal']} | {entry['bar_close']:.2f} | {entry['position']} |",
            )

    output_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    symbols = ["AAPL", "NVDA"]
    venue = Venue("XNAS")
    engine = BacktestEngine(
        config=BacktestEngineConfig(
            trader_id="BACKTEST_TRADER-STOCKS",
            logging=LoggingConfig(log_level="ERROR"),
        ),
    )

    engine.add_venue(
        venue=venue,
        oms_type=OmsType.NETTING,
        account_type=AccountType.MARGIN,
        starting_balances=[Money(1_000_000, USD)],
        base_currency=USD,
        default_leverage=Decimal(1),
    )

    all_bars_summary: dict[str, Any] = {}
    all_bars: list[Bar] = []

    instruments_by_symbol: dict[str, Any] = {}
    for symbol in symbols:
        bar_type, bars, instrument = build_bars(symbol)
        instruments_by_symbol[symbol] = instrument
        engine.add_instrument(instrument)
        all_bars.extend(bars)
        all_bars_summary[symbol] = {
            "bar_type": bar_type,
            "bar_count": len(bars),
            "start": str(bars[0].ts_init),
            "end": str(bars[-1].ts_init),
        }

    engine.add_data(all_bars)

    momentum_config = MomentumStrategyConfig(
        instrument_id=instruments_by_symbol["AAPL"].id,
        bar_type=BarType.from_str("AAPL.XNAS-1-DAY-LAST-EXTERNAL"),
        trade_size=Decimal(10),
    )
    mean_reversion_config = MeanReversionStrategyConfig(
        instrument_id=instruments_by_symbol["NVDA"].id,
        bar_type=BarType.from_str("NVDA.XNAS-1-DAY-LAST-EXTERNAL"),
        trade_size=Decimal(10),
    )

    momentum_strategy = MomentumStrategy(momentum_config)
    mean_reversion_strategy = MeanReversionStrategy(mean_reversion_config)
    engine.add_strategy(momentum_strategy)
    engine.add_strategy(mean_reversion_strategy)

    engine.run()
    engine.dispose()

    strategies = [
        {
            "name": "Momentum (AAPL)",
            "signals": momentum_strategy.signals,
            "orders": momentum_strategy.orders_submitted,
            "latest_position": int(momentum_strategy.trace[-1]["position"] if momentum_strategy.trace else 0),
            "trace": momentum_strategy.trace[:6],
        },
        {
            "name": "Mean Reversion (NVDA)",
            "signals": mean_reversion_strategy.signals,
            "orders": mean_reversion_strategy.orders_submitted,
            "latest_position": int(mean_reversion_strategy.trace[-1]["position"] if mean_reversion_strategy.trace else 0),
            "trace": mean_reversion_strategy.trace[:6],
        },
    ]

    write_markdown_report(all_bars_summary, strategies, OUTPUT_REPORT)
    print(f"Backtest complete. Markdown report written to {OUTPUT_REPORT}")


if __name__ == "__main__":
    main()
