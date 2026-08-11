"""Python configuration objects for the Java-backed engine."""

from dataclasses import dataclass


@dataclass(frozen=True)
class LoggingConfig:
    log_level: str = "INFO"


@dataclass(frozen=True)
class BacktestEngineConfig:
    trader_id: str = "BACKTEST_TRADER"
    logging: LoggingConfig = LoggingConfig()
