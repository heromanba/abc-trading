"""Adapters from tabular market data to Python model objects."""

from collections.abc import Iterable

from abc_trading.model.data import Bar


class BarDataWrangler:
    def __init__(self, bar_type: object | None = None) -> None:
        self.bar_type = bar_type

    def process(self, rows: Iterable[object]) -> list[Bar]:
        return list(rows)
