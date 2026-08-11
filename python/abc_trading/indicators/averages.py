"""Moving averages used by Python strategies."""

from __future__ import annotations

from abc_trading._java import java_class


class SimpleMovingAverage:
    def __init__(self, period: int) -> None:
        if period <= 0:
            raise ValueError("period must be positive")
        self.period = period
        self._java = java_class("com.abc.trading.indicators.SimpleMovingAverage")(period)
        self.value = 0.0
        self.count = 0

    def update_raw(self, value: float) -> None:
        self.value = float(self._java.update(value))
        self.count = int(self._java.count())

    def update(self, value: float) -> None:
        self.update_raw(value)

    @property
    def initialized(self) -> bool:
        return bool(self._java.initialized())


class ExponentialMovingAverage:
    def __init__(self, period: int) -> None:
        if period <= 0:
            raise ValueError("period must be positive")
        self.period = period
        self._java = java_class("com.abc.trading.indicators.ExponentialMovingAverage")(period)
        self.value = 0.0
        self.count = 0

    def update_raw(self, value: float) -> None:
        self.value = float(self._java.update(value))
        self.count = int(self._java.count())

    def update(self, value: float) -> None:
        self.update_raw(value)
