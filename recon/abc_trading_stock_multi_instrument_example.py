#!/usr/bin/env python3
"""Run Nautilus-shaped Python strategies against the Java library through JPype."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import pandas as pd
import yfinance as yf

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm
from abc_trading.indicators import ExponentialMovingAverage, SimpleMovingAverage
from abc_trading.model.data import Bar
from abc_trading.trading.strategy import Strategy

ROOT = Path(__file__).resolve().parent
DEFAULT_OUTPUT = ROOT / "output"


class MomentumStrategy(Strategy):
    def __init__(self, engine: BacktestEngine, trade_size: int = 10) -> None:
        self.engine = engine
        self.instrument_id = "AAPL"
        self.fast_ema = ExponentialMovingAverage(3)
        self.slow_ema = ExponentialMovingAverage(8)
        self.trade_size = trade_size
        self.trace: list[dict[str, Any]] = []
        self.orders_submitted = 0
        self.signals = 0

    def on_start(self) -> None:
        self.fast_ema = ExponentialMovingAverage(3)
        self.slow_ema = ExponentialMovingAverage(8)

    def on_bar(self, bar: Bar) -> None:
        self.fast_ema.update(bar.close)
        self.slow_ema.update(bar.close)
        signal = "flat"
        position = self.engine.position(self.instrument_id)
        if self.slow_ema.count >= 8 and self.fast_ema.value > self.slow_ema.value and position == 0:
            self._submit_order(bar, "BUY")
            signal = "buy"
        elif self.slow_ema.count >= 8 and self.fast_ema.value < self.slow_ema.value and position > 0:
            self._submit_order(bar, "SELL")
            signal = "sell"
        self.trace.append(
            {
                "strategy": "momentum",
                "ts": bar.ts_init,
                "bar_close": bar.close,
                "signal": signal,
                "position": self.engine.position(self.instrument_id),
                "unrealized_pnl": 0.0,
            },
        )

    def _submit_order(self, bar: Bar, side: str) -> None:
        self.engine.submit_market_order("momentum", self.instrument_id, bar.ts_init, bar.sequence, side, self.trade_size, bar.close)
        self.orders_submitted += 1
        self.signals += 1


class MeanReversionStrategy(Strategy):
    def __init__(self, engine: BacktestEngine, trade_size: int = 10) -> None:
        self.engine = engine
        self.instrument_id = "NVDA"
        self.sma = SimpleMovingAverage(5)
        self.trade_size = trade_size
        self.trace: list[dict[str, Any]] = []
        self.orders_submitted = 0
        self.signals = 0

    def on_start(self) -> None:
        self.sma = SimpleMovingAverage(5)

    def on_bar(self, bar: Bar) -> None:
        signal = "flat"
        self.sma.update(bar.close)
        position = self.engine.position(self.instrument_id)
        if self.sma.initialized and bar.close < self.sma.value * 0.995 and position == 0:
            self._submit_order(bar, "BUY")
            signal = "buy"
        elif self.sma.initialized and bar.close > self.sma.value * 1.005 and position > 0:
            self._submit_order(bar, "SELL")
            signal = "sell"

        self.trace.append(
            {
                "strategy": "mean_reversion",
                "ts": bar.ts_init,
                "bar_close": bar.close,
                "signal": signal,
                "position": self.engine.position(self.instrument_id),
                "unrealized_pnl": 0.0,
            },
        )

    def _submit_order(self, bar: Bar, side: str) -> None:
        self.engine.submit_market_order("mean_reversion", self.instrument_id, bar.ts_init, bar.sequence, side, self.trade_size, bar.close)
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


def build_bars(symbol: str, start: str, end: str) -> list[Bar]:
    data = download_symbol_data(symbol=symbol, start=start, end=end)
    bars: list[Bar] = []
    for sequence, (timestamp, row) in enumerate(data.iterrows(), start=1):
        close = float(row["close"])
        if pd.isna(close):
            continue
        bars.append(Bar(symbol, pd.Timestamp(timestamp).value, close, sequence))
    if not bars:
        raise RuntimeError(f"No usable bars returned for {symbol}")
    return bars


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


def run_backtest(start: str, end: str, output_dir: Path) -> Path:
    symbols = ["AAPL", "NVDA"]
    output_dir.mkdir(parents=True, exist_ok=True)
    bars_by_symbol = {symbol: build_bars(symbol, start, end) for symbol in symbols}
    all_bars = sorted(
        (bar for bars in bars_by_symbol.values() for bar in bars),
        key=lambda bar: (bar.ts_init, bar.symbol),
    )

    java_engine = BacktestEngine(output_dir / "java_events.csv")
    all_bars_summary: dict[str, Any] = {}
    for symbol, bars in bars_by_symbol.items():
        all_bars_summary[symbol] = {
            "bar_count": len(bars),
            "start": str(pd.to_datetime(bars[0].ts_init, unit="ns", utc=True)),
            "end": str(pd.to_datetime(bars[-1].ts_init, unit="ns", utc=True)),
        }
    java_engine.add_venue("XNAS")
    for symbol in symbols:
        java_engine.add_instrument(symbol, "XNAS")
    momentum_strategy = MomentumStrategy(java_engine)
    mean_reversion_strategy = MeanReversionStrategy(java_engine)
    java_engine.add_strategy("AAPL", momentum_strategy)
    java_engine.add_strategy("NVDA", mean_reversion_strategy)
    java_engine.start()
    try:
        java_engine.run(all_bars)
    finally:
        java_engine.close()
        shutdown_jvm()

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

    report = output_dir / "nautilus_stock_data_flow_report.md"
    write_markdown_report(all_bars_summary, strategies, report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--start", default="2023-01-01")
    parser.add_argument("--end", default="2024-01-01")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    report = run_backtest(args.start, args.end, args.output_dir)
    print(f"Backtest complete. Markdown report written to {report}")


if __name__ == "__main__":
    main()
