"""Base configuration for Python-owned strategies."""


class StrategyConfig:
    def __init__(self, **kwargs: object) -> None:
        for key, value in kwargs.items():
            setattr(self, key, value)
