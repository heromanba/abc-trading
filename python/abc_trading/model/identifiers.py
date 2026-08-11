"""Instrument and venue identifiers."""

from dataclasses import dataclass


@dataclass(frozen=True)
class Venue:
    value: str

    def __str__(self) -> str:
        return self.value


@dataclass(frozen=True)
class InstrumentId:
    symbol: str
    venue: Venue

    def __str__(self) -> str:
        return f"{self.symbol}.{self.venue}"
