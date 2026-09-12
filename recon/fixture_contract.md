# Rust/Python/Java Fixture Contract

All parity fixtures use the same ordered event vocabulary and exact decimal text representation.

## Canonical Inputs

- `market_timestamp`: Unix nanoseconds
- `symbol`: canonical instrument symbol
- `event_type`: lifecycle/event kind
- `order_id`: client order identity
- `venue_order_id`: exchange/server identity when available
- `price`: decimal text, never binary float text
- `quantity`: fixed-point decimal text
- `current_position`: signed decimal text
- `realized_pnl`: decimal text
- `commission`: decimal text and currency
- account totals/margins/equity as decimal text

## Required Comparisons

1. Ordered lifecycle rows, including order status transitions and fills.
2. Exchange execution reports, with duplicate and stale reports removed by venue identity and event-time key.
3. Multi-instrument ordering by timestamp, then symbol, then source sequence.
4. Full replay projection versus checkpoint-resumed replay projection.
5. Option Greeks, premiums, expiry payoff, and margin values within declared decimal precision.
6. Actor lifecycle events, timer order, mailbox overflow, restart, and fault recovery.

The Java implementation uses `EventSequenceComparator` for JSONL event-store sequences and the existing Python CSV comparators for cross-language fixture files. Rust/Python fixture producers should emit the fields above without converting through `double`.
