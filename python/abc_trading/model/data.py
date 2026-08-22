"""Market data value objects backed by Java primitives."""

from dataclasses import dataclass

from abc_trading._java import java_class


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
    def __init__(self, price: float, quantity: int) -> None:
        self._java = java_class("com.abc.trading.data.BookLevel")(price, quantity)


class OrderBookSnapshot:
    def __init__(
        self,
        symbol: str,
        timestamp: int,
        bids: list[tuple[float, int]],
        asks: list[tuple[float, int]],
        sequence: int = 0,
    ) -> None:
        level_type = java_class("com.abc.trading.data.BookLevel")
        list_type = java_class("java.util.ArrayList")
        java_bids = list_type()
        java_asks = list_type()
        for price, quantity in bids:
            java_bids.add(level_type(price, quantity))
        for price, quantity in asks:
            java_asks.add(level_type(price, quantity))
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
        quantity: int,
        sequence: int = 0,
    ) -> None:
        direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
        book_action = java_class("com.abc.trading.data.BookAction").valueOf(action)
        self._java = java_class("com.abc.trading.data.OrderBookDelta")(
            symbol, timestamp, direction, book_action, price, quantity, sequence
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
