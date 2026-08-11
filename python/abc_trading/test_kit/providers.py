"""Small deterministic providers for examples and tests."""

from abc_trading.model.identifiers import InstrumentId, Venue


class TestInstrumentProvider:
    @staticmethod
    def equity(symbol: str, venue: str = "SIM") -> InstrumentId:
        return InstrumentId(symbol, Venue(venue))
