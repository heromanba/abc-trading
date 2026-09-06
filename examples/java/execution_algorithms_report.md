# Execution Algorithms

## TWAP

`TwapExecutionAlgorithm` divides a target quantity across timestamped schedule slices. Bars and market-data snapshots trigger due slices. Remainders are distributed across the remaining slices, so the target is submitted exactly without a final oversized remainder when the quantity permits.

## VWAP / Participation

`VwapExecutionAlgorithm` consumes `TradeTick` volume because the Java `Bar` type does not carry volume. It maintains a cumulative participation budget, submits only when the minimum slice is available, and applies maximum-slice and remaining-target caps.

## Shared behavior

Both algorithms submit existing `OrderIntent` child orders through the normal risk, execution, fill, fee, and accounting paths. They track submitted and filled quantities, publish cancellation commands for working children, and expose `ExecutionAlgorithmState` snapshots for restart continuation.

A short JUnit suite covers exact TWAP slicing, VWAP participation, fill tracking, cancellation, and state restoration. Full market-impact and fee outcomes remain owned by the configured execution venue and fee model rather than duplicated in the algorithms.
