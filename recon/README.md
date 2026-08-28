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

## Liquidation parity check

The shared liquidation fixture covers a position crash, cancellation of a
resting order, forced execution at the current bid, and completion:

```bash
PYTHONPATH=python python recon/java_liquidation_recon.py
python recon/nautilus_liquidation_recon.py
python recon/compare_liquidation_recon.py \
	recon/output/nautilus_liquidation_events.csv \
	recon/output/java_liquidation_events.csv
```

Expected result:

```text
MATCH liquidation rows=4
```

## Account parity check

The shared account fixture compares a linear perpetual after an opening fill
and mark-price move. It checks total, locked, and free balance, initial and
maintenance margin, unrealized PnL, and equity:

```bash
PYTHONPATH=python python recon/java_account_parity.py
python recon/nautilus_account_parity.py
python recon/compare_account_recon.py \
	recon/output/nautilus_account_state.csv \
	recon/output/java_account_state.csv
```

Expected result:

```text
MATCH account state fields=8
```

## Persistent event replay

`BacktestEngine` accepts an optional second path for an append-only JSONL event
store. Each record has a schema version and monotonic offset. Java consumers
can replay it into the synchronous `MessageBus` and reconstruct order,
position, realized-PnL, and account-state projections with `EventReplayer`.
`EventCheckpoint` stores the next offset and sequence values for resume:

```java
try (BacktestEngine engine = new BacktestEngine("events.csv", "events.jsonl")) {
	// configure and run the backtest
}

EventReplayResult result = EventReplayer.replay(
		Path.of("events.jsonl"), new MessageBus(null), Path.of("events.checkpoint.json"));
```

The persistent-store tests cover append/reopen offsets, schema validation,
checkpoint resume, bus delivery, and state projection recovery.

## Binance USD-M Futures adapter

The Java adapter follows the Nautilus Binance split between public market data,
signed execution REST, and authenticated user-data streams. Testnet uses
`https://demo-fapi.binance.com` and `wss://stream.binancefuture.com`; live uses
the `fapi.binance.com` and `fstream.binance.com` routes.

Credentials are optional for public market data and must be provided through
runtime configuration, never committed to the repository:

```java
BinanceFuturesConfig config = new BinanceFuturesConfig(
	BinanceEnvironment.TESTNET,
	System.getenv("BINANCE_API_KEY"),
	System.getenv("BINANCE_API_SECRET"),
	List.of("BTCUSDT"));
BinanceFuturesAdapter adapter = new BinanceFuturesAdapter(config, marketHandler, executionHandler);
adapter.start();
```

The adapter preserves decimal Binance quantities and maps `depthUpdate`,
`aggTrade`, `markPriceUpdate`, `ORDER_TRADE_UPDATE`, and `ACCOUNT_UPDATE` into
typed records. It maps market, limit, stop, and trailing-stop-market orders;
trailing stop-limit is explicitly rejected because the Nautilus Binance
adapter does not support that Binance Futures order type.
