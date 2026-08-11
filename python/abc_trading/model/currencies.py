"""Currency value objects."""

from enum import Enum


class USD(str, Enum):
    USD = "USD"

    def __str__(self) -> str:
        return self.value
