"""Nautilus-shaped Python facade over the Java backtest library."""

from __future__ import annotations

import jpype
from pathlib import Path

from abc_trading._java import ensure_jvm, java_class
from abc_trading.model.data import Bar


class BacktestEngine:
    """Python-importable engine backed by ``com.abc.trading`` Java classes."""

    def __init__(self, output_path: str | Path) -> None:
        ensure_jvm()
        java_type = java_class("com.abc.trading.backtest.BacktestEngine")
        self._java = java_type(str(Path(output_path).resolve()))
        self._started = False
        self._strategy_proxies: list[object] = []

    def add_venue(self, venue: str) -> None:
        self._java.addVenue(venue)

    def add_instrument(self, symbol: str, venue: str) -> None:
        self._java.addInstrument(symbol, venue)

    def start(self) -> None:
        self._java.start()
        self._started = True

    def add_strategy(self, symbol: str, strategy: object) -> None:
        """Register a Python strategy for Java-driven bar callbacks."""
        interface = java_class("com.abc.trading.trading.StrategyHandler")

        def on_start() -> None:
            strategy.on_start()

        def on_bar(java_bar: object) -> None:
            strategy.on_bar(Bar._from_java(java_bar))

        def on_stop() -> None:
            strategy.on_stop()

        proxy = jpype.JProxy(
            interface,
            dict(onStart=on_start, onBar=on_bar, onStop=on_stop),
        )
        self._strategy_proxies.append(proxy)
        self._java.addStrategy(symbol, proxy)

    def run(self, bars: list[Bar]) -> None:
        """Run bars through the Java event loop and Python strategy callbacks."""
        java_bar_type = java_class("com.abc.trading.data.Bar")
        java_bars = jpype.JArray(java_bar_type)([bar._java for bar in bars])
        self._java.runBars(java_bars)

    @property
    def started(self) -> bool:
        return self._started

    def position(self, symbol: str) -> int:
        return int(self._java.position(symbol))

    def submit_market_order(
        self,
        strategy_id: str,
        symbol: str,
        market_timestamp: int,
        sequence: int,
        side: str,
        quantity: int,
        price: float,
    ) -> None:
        self._java.submitMarketOrder(
            strategy_id,
            symbol,
            market_timestamp,
            sequence,
            side,
            quantity,
            price,
        )

    def submit_limit_order(
        self,
        strategy_id: str,
        symbol: str,
        market_timestamp: int,
        sequence: int,
        side: str,
        quantity: int,
        limit_price: float,
    ) -> None:
        self._java.submitLimitOrder(
            strategy_id,
            symbol,
            market_timestamp,
            sequence,
            side,
            quantity,
            limit_price,
        )

    def close(self) -> None:
        self._java.close()


def shutdown_jvm() -> None:
    """Shutdown JPype after all Python-side report values are materialized."""
    if jpype.isJVMStarted():
        jpype.shutdownJVM()
