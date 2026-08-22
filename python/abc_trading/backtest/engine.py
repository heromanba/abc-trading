"""Nautilus-shaped Python facade over the Java backtest library."""

from __future__ import annotations

import jpype
from pathlib import Path

from abc_trading._java import ensure_jvm, java_class
from abc_trading.model.data import Bar, MarketDataSnapshot, OrderBookSnapshot, OrderBookDelta


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

    def emulated_market(self, symbol: str, side: str, quantity: int, price: float,
                        emulation_trigger: str = "LAST_PRICE") -> str:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(emulation_trigger)
        return str(self._java.emulatedMarket(symbol, direction, quantity, price, trigger))

    def emulated_limit(self, symbol: str, side: str, quantity: int, limit_price: float,
                       emulation_trigger: str = "LAST_PRICE") -> str:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(emulation_trigger)
        return str(self._java.emulatedLimit(symbol, direction, quantity, limit_price, trigger))

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

    def stop_market(
        self,
        symbol: str,
        side: str,
        quantity: int,
        trigger_price: float,
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        signal_direction = java_class("com.abc.trading.execution.SignalDirection")
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.stopMarket(
            symbol, signal_direction.valueOf(side), quantity, trigger_price, tif, expire_time_ns
        ))

    def stop_limit(
        self,
        symbol: str,
        side: str,
        quantity: int,
        limit_price: float,
        trigger_price: float,
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        signal_direction = java_class("com.abc.trading.execution.SignalDirection")
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.stopLimit(
            symbol, signal_direction.valueOf(side), quantity, limit_price, trigger_price,
            tif, expire_time_ns
        ))

    def trailing_stop_market(
        self,
        symbol: str,
        side: str,
        quantity: int,
        activation_price: float,
        trailing_offset: float,
        offset_type: str = "PRICE",
        trigger_type: str = "LAST_PRICE",
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        offset = java_class("com.abc.trading.execution.TrailingOffsetType").valueOf(offset_type)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.trailingStopMarket(
            symbol, direction, quantity, activation_price, trailing_offset,
            offset, trigger, tif, expire_time_ns
        ))

    def trailing_stop_limit(
        self,
        symbol: str,
        side: str,
        quantity: int,
        limit_price: float,
        activation_price: float,
        limit_offset: float,
        trailing_offset: float,
        offset_type: str = "PRICE",
        trigger_type: str = "LAST_PRICE",
        time_in_force: str = "GTC",
        expire_time_ns: int = 0,
    ) -> str:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        offset = java_class("com.abc.trading.execution.TrailingOffsetType").valueOf(offset_type)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        tif = java_class("com.abc.trading.execution.TimeInForce").valueOf(time_in_force)
        return str(self._java.trailingStopLimit(
            symbol, direction, quantity, limit_price, activation_price, limit_offset,
            trailing_offset, offset, trigger, tif, expire_time_ns
        ))

    def cancel(self, client_order_id: str) -> None:
        self._java.cancel(client_order_id)

    def modify(
        self,
        client_order_id: str,
        quantity: int | None = None,
        price: float | None = None,
        trigger_price: float | None = None,
    ) -> None:
        java_quantity = jpype.JObject(quantity, jpype.JClass("java.lang.Integer")) if quantity is not None else None
        java_price = jpype.JObject(price, jpype.JClass("java.lang.Double")) if price is not None else None
        java_trigger_price = jpype.JObject(trigger_price, jpype.JClass("java.lang.Double")) if trigger_price is not None else None
        self._java.modify(client_order_id, java_quantity, java_price, java_trigger_price)


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

    def add_instrument(self, symbol: str, venue: str, tick_size: float = 0.01) -> None:
        self._java.addInstrument(symbol, venue, tick_size)

    def set_max_fill_quantity(self, venue: str, quantity: int) -> None:
        self._java.setMaxFillQuantity(venue, quantity)

    def submit_stop_market_order(
        self,
        strategy_id: str,
        symbol: str,
        order_id: str,
        side: str,
        quantity: int,
        timestamp_ns: int,
        trigger_price: float,
        trigger_type: str = "LAST_PRICE",
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        self._java.submitStopMarketOrder(
            strategy_id, symbol, order_id, direction, quantity, timestamp_ns, trigger_price, trigger
        )

    def submit_stop_limit_order(
        self,
        strategy_id: str,
        symbol: str,
        order_id: str,
        side: str,
        quantity: int,
        timestamp_ns: int,
        limit_price: float,
        trigger_price: float,
        trigger_type: str = "LAST_PRICE",
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        self._java.submitStopLimitOrder(
            strategy_id, symbol, order_id, direction, quantity, timestamp_ns,
            limit_price, trigger_price, trigger
        )

    def submit_trailing_stop_market_order(
        self, strategy_id: str, symbol: str, order_id: str, side: str, quantity: int,
        timestamp_ns: int, activation_price: float, trailing_offset: float,
        offset_type: str = "PRICE", trigger_type: str = "LAST_PRICE",
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        offset = java_class("com.abc.trading.execution.TrailingOffsetType").valueOf(offset_type)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        self._java.submitTrailingStopMarketOrder(
            strategy_id, symbol, order_id, direction, quantity, timestamp_ns,
            activation_price, trailing_offset, offset, trigger
        )

    def submit_trailing_stop_limit_order(
        self, strategy_id: str, symbol: str, order_id: str, side: str, quantity: int,
        timestamp_ns: int, limit_price: float, activation_price: float, limit_offset: float,
        trailing_offset: float, offset_type: str = "PRICE", trigger_type: str = "LAST_PRICE",
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        offset = java_class("com.abc.trading.execution.TrailingOffsetType").valueOf(offset_type)
        trigger = java_class("com.abc.trading.execution.TriggerType").valueOf(trigger_type)
        self._java.submitTrailingStopLimitOrder(
            strategy_id, symbol, order_id, direction, quantity, timestamp_ns,
            limit_price, activation_price, limit_offset, trailing_offset, offset, trigger
        )

    def order_status(self, order_id: str) -> str:
        return str(self._java.orderStatus(order_id))

    def emulate_order(self, client_order_id: str) -> None:
        self._java.emulateOrder(client_order_id)

    def release_order(self, client_order_id: str) -> None:
        self._java.releaseOrder(client_order_id)

    def submit_released_order(self, client_order_id: str) -> None:
        self._java.submitReleasedOrder(client_order_id)

    def trigger_order(self, client_order_id: str) -> None:
        self._java.triggerOrder(client_order_id)

    def void_order(self, client_order_id: str) -> None:
        self._java.voidOrder(client_order_id)

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

    def run_market_data(self, snapshots: list[MarketDataSnapshot]) -> None:
        java_type = java_class("com.abc.trading.data.MarketDataSnapshot")
        java_snapshots = jpype.JArray(java_type)([snapshot.java for snapshot in snapshots])
        self._java.runMarketData(java_snapshots)

    def run_order_books(self, snapshots: list[OrderBookSnapshot]) -> None:
        java_type = java_class("com.abc.trading.data.OrderBookSnapshot")
        java_snapshots = jpype.JArray(java_type)([snapshot.java for snapshot in snapshots])
        self._java.runOrderBooks(java_snapshots)

    def run_order_book_deltas(self, deltas: list[OrderBookDelta]) -> None:
        java_type = java_class("com.abc.trading.data.OrderBookDelta")
        java_deltas = jpype.JArray(java_type)([delta.java for delta in deltas])
        self._java.runOrderBookDeltas(java_deltas)

    @property
    def started(self) -> bool:
        return self._started

    def position(self, symbol: str) -> int:
        return int(self._java.position(symbol))

    def submit_market_order(
        self,
        strategy_id: str,
        symbol: str,
        order_id: str,
        market_timestamp: int,
        sequence: int,
        side: str,
        quantity: int,
        price: float,
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        self._java.submitMarketOrder(
            strategy_id,
            symbol,
            order_id,
            direction,
            quantity,
            market_timestamp,
            price,
        )

    def submit_limit_order(
        self,
        strategy_id: str,
        symbol: str,
        order_id: str,
        market_timestamp: int,
        sequence: int,
        side: str,
        quantity: int,
        limit_price: float,
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        self._java.submitLimitOrder(
            strategy_id,
            symbol,
            order_id,
            direction,
            quantity,
            market_timestamp,
            limit_price,
        )

    def close(self) -> None:
        self._java.close()


def shutdown_jvm() -> None:
    """Shutdown JPype after all Python-side report values are materialized."""
    if jpype.isJVMStarted():
        jpype.shutdownJVM()
