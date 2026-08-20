"""Nautilus-shaped Python facade over the Java backtest library."""

from __future__ import annotations

import jpype
from pathlib import Path

from abc_trading._java import ensure_jvm, java_class
from abc_trading.model.data import Bar


class StrategyContext:
    """Python view of the Java strategy context and order API."""

    def __init__(self, java_context: object) -> None:
        self._java = java_context

    def position(self, symbol: str) -> int:
        return int(self._java.position(symbol))

    def market(
        self,
        symbol: str,
        side: str,
        quantity: int,
        price: float,
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        signal_direction = java_class("com.abc.trading.execution.SignalDirection")
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.market(
            symbol, signal_direction.valueOf(side), quantity, price, tif, expire_time_ns
        ))

    def limit(
        self,
        symbol: str,
        side: str,
        quantity: int,
        limit_price: float,
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        signal_direction = java_class("com.abc.trading.execution.SignalDirection")
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.limit(
            symbol, signal_direction.valueOf(side), quantity, limit_price, tif, expire_time_ns
        ))

    def cancel(self, client_order_id: str) -> None:
        self._java.cancel(client_order_id)

    def modify(self, client_order_id: str, quantity: int | None = None, price: float | None = None) -> None:
        java_quantity = jpype.JObject(quantity, jpype.JClass("java.lang.Integer")) if quantity is not None else None
        java_price = jpype.JObject(price, jpype.JClass("java.lang.Double")) if price is not None else None
        self._java.modify(client_order_id, java_quantity, java_price)


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

    def set_max_fill_quantity(self, venue: str, quantity: int) -> None:
        self._java.setMaxFillQuantity(venue, quantity)

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

        def on_bar_with_context(java_bar: object, java_context: object) -> None:
            java_context.onBar(java_bar)
            strategy.context = StrategyContext(java_context)
            strategy.on_bar(Bar._from_java(java_bar))

        def on_stop() -> None:
            strategy.on_stop()

        proxy = jpype.JProxy(
            interface,
            dict(onStart=on_start, onBar=on_bar, onBarWithContext=on_bar_with_context, onStop=on_stop),
        )
        self._strategy_proxies.append(proxy)
        strategy_id = str(getattr(strategy, "strategy_id", symbol))
        self._java.addStrategy(symbol, strategy_id, proxy)

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
