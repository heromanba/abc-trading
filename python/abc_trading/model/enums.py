"""Enum values exposed by the Python facade."""

from enum import Enum


class AccountType(str, Enum):
    CASH = "CASH"
    MARGIN = "MARGIN"


class OmsType(str, Enum):
    HEDGING = "HEDGING"
    NETTING = "NETTING"


class OrderSide(str, Enum):
    BUY = "BUY"
    SELL = "SELL"
