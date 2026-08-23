# Reconciliation Workflow

This folder contains the deterministic parity workflow for the Java rewrite.

## Goal

For now, parity is validated on `ORDER_SUBMIT` events only. The strategy is
defined in Python; Java is a library backend invoked through JPype/JNI.

## Immutable Input Stream

Use a fixed CSV for market input. Example test fixture:

- `java/src/test/resources/recon/sample_ticks.csv`

Install the bridge in the workspace environment:

```bash
.venv/bin/python -m pip install JPype1 yfinance
```

To fetch real historical bars from Yahoo into the same schema:

```bash
.venv/bin/python recon/download_yahoo_1m.py --symbol AAPL --start 2026-06-22 --end 2026-06-23 --output recon/data/aapl_2026-06-22_1m.csv
```

The downloader uses the same `yfinance` library and `yf.download` settings as
the Nautilus example, normalizes Yahoo MultiIndex columns, rejects empty/error
responses, and reuses an existing output unless `--force` is set. Yahoo
intraday intervals have a maximum 30-day request range and may still be
rate-limited; do not substitute synthetic data for a failed download.

CSV schema:

- `market_timestamp`
- `symbol`
- `price`

## Run the example

From repository root, build the Java library and run the Python-owned strategy
through the Java backtest engine:

```bash
mvn -pl java test
PYTHONPATH=python .venv/bin/python recon/abc_trading_stock_multi_instrument_example.py
```

## L3 MBO parity check

The L3 fixture uses individual venue orders, queue-ahead trades, and a market
order consuming a named ask. Run both backends from the repository root:

```bash
mvn -pl java test
PYTHONPATH=python /home/wangchu/anaconda3/envs/py312_nt/bin/python recon/java_l3_order_book_recon.py
/home/wangchu/anaconda3/envs/py312_nt/bin/python recon/nautilus_l3_order_book_recon.py
/home/wangchu/anaconda3/envs/py312_nt/bin/python recon/compare_l3_order_book_recon.py \
	recon/output/nautilus_l3_fills.csv recon/output/java_l3_fills.csv
```

The comparator checks client order, price, quantity, liquidity side, and fill
order. Nautilus `venue_order_id` identifies the client order assigned by the
venue; it does not expose the passive L3 book order ID on `OrderFilled`. The
Java output retains its passive book-order attribution as a diagnostic field.

## Java library boundary

The Java `BacktestEngine` is an importable JPype library, not a command-line
application. The Python example creates it, registers venues, instruments, and
Python strategies, calls `start()`, and delegates the chronological bar loop
to Java. Java owns event ordering, positions, order routing, deterministic IDs,
and event logging; Python owns Yahoo data loading, strategy callbacks, and
traces.

The public Python entry point is a normal package import:

```python
from abc_trading.backtest.engine import BacktestEngine
```

Run the example directly:

```bash
mvn -pl java test
PYTHONPATH=python .venv/bin/python recon/abc_trading_stock_multi_instrument_example.py
```
