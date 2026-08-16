"""Python-owned strategy lifecycle."""

from __future__ import annotations


class Strategy:
    def __init__(self, config: object | None = None) -> None:
        self.config = config
        self.context: object | None = None

    def on_start(self) -> None:
        pass

    def on_bar(self, bar: object) -> None:
        raise NotImplementedError

    def on_stop(self) -> None:
        pass
