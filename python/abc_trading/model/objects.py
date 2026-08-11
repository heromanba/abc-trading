"""Numeric domain objects backed by Java primitives."""

from abc_trading._java import java_class


class Money:
    def __init__(self, amount: float, currency: object) -> None:
        self._java = java_class("com.abc.trading.model.Money")(
            amount, str(currency)
        )
        self._amount = float(self._java.amount())
        self._currency = str(self._java.currency())

    @property
    def amount(self) -> float:
        return self._amount

    @property
    def currency(self) -> str:
        return self._currency

    def __float__(self) -> float:
        return self.amount
