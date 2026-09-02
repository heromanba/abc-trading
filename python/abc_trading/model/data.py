"""Market data value objects backed by Java value types."""

from dataclasses import dataclass
from decimal import Decimal
from typing import TypeAlias

from abc_trading._java import java_class


QuantityValue: TypeAlias = int | Decimal | str


def _java_quantity(value: QuantityValue) -> object:
    quantity_type = java_class("com.abc.trading.data.Quantity")
    if isinstance(value, bool):
        raise TypeError("quantity must be an int, Decimal, or decimal string")
    if isinstance(value, int):
        return quantity_type.fromInt(value)
    if isinstance(value, Decimal | str):
        decimal_value = value if isinstance(value, Decimal) else Decimal(value)
        precision = max(0, -decimal_value.as_tuple().exponent)
        return quantity_type.fromString(str(decimal_value), precision)
    raise TypeError("quantity must be an int, Decimal, or decimal string")


class Bar:
    @classmethod
    def _from_java(cls, java_bar: object) -> "Bar":
        instance = cls.__new__(cls)
        instance._java = java_bar
        instance._symbol = str(java_bar.symbol())
        instance._ts_init = int(java_bar.tsInit())
        instance._close = float(java_bar.close())
        instance._sequence = int(java_bar.sequence())
        return instance

    def __init__(self, symbol: str, timestamp: int, close: float, sequence: int = 0) -> None:
        self._java = java_class("com.abc.trading.data.Bar")(
            symbol, timestamp, close, sequence
        )
        self._symbol = str(self._java.symbol())
        self._ts_init = int(self._java.tsInit())
        self._close = float(self._java.close())
        self._sequence = int(self._java.sequence())

    @property
    def symbol(self) -> str:
        return self._symbol

    @property
    def ts_init(self) -> int:
        return self._ts_init

    @property
    def close(self) -> float:
        return self._close

    @property
    def sequence(self) -> int:
        return self._sequence


class MarketDataSnapshot:
    def __init__(
        self,
        symbol: str,
        timestamp: int,
        bid: float,
        ask: float,
        last: float,
        mark: float,
        index: float,
        sequence: int = 0,
    ) -> None:
        self._java = java_class("com.abc.trading.data.MarketDataSnapshot")(
            symbol, timestamp, bid, ask, last, mark, index, sequence
        )

    @property
    def java(self) -> object:
        return self._java

    @property
    def symbol(self) -> str:
        return str(self._java.symbol())

    @property
    def ts_init(self) -> int:
        return int(self._java.tsInit())

    @property
    def sequence(self) -> int:
        return int(self._java.sequence())


class BookLevel:
    def __init__(self, price: float, quantity: QuantityValue) -> None:
        self._java = java_class("com.abc.trading.data.BookLevel")(price, _java_quantity(quantity))


class OrderBookSnapshot:
    def __init__(
        self,
        symbol: str,
        timestamp: int,
        bids: list[tuple[float, QuantityValue]],
        asks: list[tuple[float, QuantityValue]],
        sequence: int = 0,
    ) -> None:
        level_type = java_class("com.abc.trading.data.BookLevel")
        list_type = java_class("java.util.ArrayList")
        java_bids = list_type()
        java_asks = list_type()
        for price, quantity in bids:
            java_bids.add(level_type(price, _java_quantity(quantity)))
        for price, quantity in asks:
            java_asks.add(level_type(price, _java_quantity(quantity)))
        self._java = java_class("com.abc.trading.data.OrderBookSnapshot")(
            symbol, timestamp, java_bids, java_asks, sequence
        )

    @property
    def java(self) -> object:
        return self._java


class OrderBookDelta:
    def __init__(
        self,
        symbol: str,
        timestamp: int,
        side: str,
        action: str,
        price: float,
        quantity: QuantityValue,
        sequence: int = 0,
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        book_action = java_class("com.abc.trading.data.BookAction").valueOf(action)
        self._java = java_class("com.abc.trading.data.OrderBookDelta")(
            symbol, timestamp, direction, book_action, price, _java_quantity(quantity), sequence
        )

    @property
    def java(self) -> object:
        return self._java


class VenueOrder:
    def __init__(self, order_id: str, side: str, price: float, quantity: QuantityValue, sequence: int) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        self._java = java_class("com.abc.trading.data.VenueOrder")(order_id, direction, price, _java_quantity(quantity), sequence)

    @property
    def java(self) -> object:
        return self._java


class OrderBookL3Snapshot:
    def __init__(self, symbol: str, timestamp: int,
                 bids: list[VenueOrder | tuple[str, str, float, QuantityValue, int]],
                 asks: list[VenueOrder | tuple[str, str, float, QuantityValue, int]], sequence: int = 0) -> None:
        order_type = java_class("com.abc.trading.data.VenueOrder")
        array_list = java_class("java.util.ArrayList")
        java_bids = array_list()
        java_asks = array_list()
        for order in bids:
            java_bids.add(order.java if isinstance(order, VenueOrder) else order_type(
                order[0], java_class("com.abc.trading.execution.SignalDirection").valueOf(order[1]),
                order[2], _java_quantity(order[3]), order[4]))
        for order in asks:
            java_asks.add(order.java if isinstance(order, VenueOrder) else order_type(
                order[0], java_class("com.abc.trading.execution.SignalDirection").valueOf(order[1]),
                order[2], _java_quantity(order[3]), order[4]))
        self._java = java_class("com.abc.trading.data.OrderBookL3Snapshot")(symbol, timestamp, java_bids, java_asks, sequence)

    @property
    def java(self) -> object:
        return self._java


class OrderBookL3Delta:
    def __init__(self, symbol: str, timestamp: int, side: str, action: str, order_id: str,
                 price: float, quantity: QuantityValue, sequence: int = 0) -> None:
        self._java = java_class("com.abc.trading.data.OrderBookL3Delta")(
            symbol, timestamp,
            java_class("com.abc.trading.execution.SignalDirection").valueOf(side),
            java_class("com.abc.trading.data.BookAction").valueOf(action),
            order_id, price, _java_quantity(quantity), sequence
        )

    @property
    def java(self) -> object:
        return self._java


class TradeTick:
    def __init__(self, symbol: str, timestamp: int, price: float, quantity: QuantityValue,
                 aggressor_side: str, sequence: int = 0) -> None:
        self._java = java_class("com.abc.trading.data.TradeTick")(
            symbol, timestamp, price, _java_quantity(quantity),
            java_class("com.abc.trading.data.AggressorSide").valueOf(aggressor_side),
            sequence
        )

    @property
    def java(self) -> object:
        return self._java


class FxRateUpdate:
    def __init__(self, from_currency: str, to_currency: str, rate: float,
                 timestamp: int, sequence: int = 0) -> None:
        self._java = java_class("com.abc.trading.data.FxRateUpdate")(
            from_currency, to_currency, rate, timestamp, sequence
        )

    @property
    def java(self) -> object:
        return self._java

class FundingRateUpdate:
    def __init__(self, symbol: str, rate: Decimal | str | float,
                 timestamp: int, sequence: int = 0,
                 interval_minutes: int | None = None,
                 next_funding_timestamp: int | None = None) -> None:
        decimal_type = java_class("java.math.BigDecimal")
        self._java = java_class("com.abc.trading.data.FundingRateUpdate")(
            symbol, decimal_type(str(rate)), interval_minutes, next_funding_timestamp,
            timestamp, timestamp, sequence
        )

    @property
    def java(self) -> object:
        return self._java


@dataclass(frozen=True)
class BarType:
    value: str

    @classmethod
    def from_str(cls, value: str) -> "BarType":
        return cls(value)
