# Nautilus Trader Java Parity Roadmap

This roadmap tracks the remaining behavioral and API gap between the Java rewrite and the Rust/Python Nautilus Trader implementation. It is intentionally ordered by dependency: core value objects and identifiers first, then execution/accounting, then platform breadth.

## Current Baseline

- [x] Deterministic Java backtest runtime and lifecycle
- [x] L2/L3 matching and queue-ahead behavior
- [x] Account, margin, FX, liquidation, funding, and cross/isolated baseline
- [x] JSONL persistence, replay, and exact monetary event schema
- [x] Binance USD-M baseline adapter
- [x] Direct bus, Disruptor ingress, Redis Streams backing, and Aeron IPC backing
- [x] TWAP/VWAP execution algorithms
- [x] Transport and end-to-end trading workload benchmarks

## Phase 1: Core Model Parity

- [x] Add strongly typed Rust-style identifiers: `InstrumentId`, `VenueId`, `ClientOrderId`, `PositionId`, `StrategyId`.
- [x] Add nanosecond timestamp value semantics and monotonic/event-time ordering helpers.
- [x] Add fixed-point `Price`, exact `Money`, and `Decimal` value objects alongside existing `Quantity`.
- [x] Migrate market data, book levels, order models, fills, portfolio matching, command intents, risk, adapters, and execution boundaries to canonical `Price` values.
- [x] Add exact `Price`/`Decimal` fields and views to legacy command/event records; retain numeric fields only as explicit compatibility projections for existing Python/CSV APIs.
- [x] Migrate portfolio average fill prices and realized PnL calculations to BigDecimal arithmetic.
- [x] Add explicit instrument taxonomy metadata for equity, FX, futures, and perpetuals.
- [x] Add option and spread contract semantics, including exact intrinsic payoff, margin, and net spread valuation.
- [x] Preserve backward-compatible constructors while making exact types available as the preferred API.

## Phase 2: Order and Execution Parity

- [x] Add canonical lifecycle event variants and deterministic transition history to `OrderStateMachine`.
- [ ] Match all remaining Nautilus order-event variants and venue execution-report semantics exactly.
- [ ] Add venue/client/server order ID mapping and execution-report reconciliation.
- [ ] Add reduce-only, post-only, close-position, quote-quantity, and self-trade-prevention semantics.
- [ ] Add slippage, fill-probability, and market-impact models.
- [ ] Cover cancel/modify races, partial rejection, and duplicate execution reports.

## Phase 3: Backtest and Data Parity

- [ ] Merge bars, quotes, trades, L2/L3 books, funding, FX, and custom data into one deterministic timeline.
- [ ] Add multi-instrument and multi-venue ordering guarantees.
- [ ] Add catalog/streaming data interfaces and warm-up behavior.
- [ ] Match checkpoint/resume and deterministic replay across all input types.
- [ ] Add feature parity fixtures generated from Rust/Python outputs.

## Phase 4: Portfolio and Risk Parity

- [ ] Add complete leverage tiers and instrument-specific risk limits.
- [ ] Complete isolated collateral, cross-margin netting, and bankruptcy/insurance behavior.
- [ ] Add options Greeks and options margin.
- [ ] Add funding, borrow, dividends, settlements, expiry, and contract rollover behavior.
- [ ] Remove remaining double-based accounting projections where exact values are required.

## Phase 5: Actors, Python, and Adapters

- [ ] Implement actor mailbox, timers, scheduled tasks, restart, and fault semantics.
- [ ] Match Python object names/signatures and callback lifecycle.
- [ ] Add native Python event subscriptions and exact Decimal reporting.
- [ ] Generalize adapter contracts and add high-value venues beyond Binance.

## Phase 6: Reliability and Performance

- [x] Compare direct, Disruptor, Aeron, and Redis transport throughput/latency/allocation.
- [x] Add transport deployment selection and failure tests.
- [ ] Run longer soak tests with production payloads and subscriber counts.
- [ ] Add randomized/property-based differential tests.
- [ ] Re-run realistic trading workloads after core value-object migration.

## Active Next Slice

Implement Phase 1 in this order:

1. identifiers and nanosecond timestamp semantics
2. exact `Decimal`, `Price`, and `Money` APIs
3. instrument taxonomy and `InstrumentSpec` integration
4. migrate remaining numeric command/event wire fields when Python/CSV compatibility can be versioned
5. remove remaining `double` PnL and fee boundaries

Each slice must add Rust/Python parity fixtures and pass the full Java suite before the next slice begins.
