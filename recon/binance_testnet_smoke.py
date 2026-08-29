#!/usr/bin/env python3
"""Opt-in Binance USD-M Futures Testnet smoke check.

By default this validates public API connectivity, exchange-info discovery, and
adapter lifecycle only. Order placement requires --place-test-order and the
BINANCE_SMOKE_ALLOW_ORDER=1 environment guard.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from decimal import Decimal, ROUND_CEILING, ROUND_FLOOR
from pathlib import Path

from abc_trading.backtest.engine import BacktestEngine, shutdown_jvm


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbol", default=os.environ.get("BINANCE_SMOKE_SYMBOL", "BTCUSDT"))
    parser.add_argument("--seconds", type=float, default=10.0)
    parser.add_argument("--place-test-order", action="store_true")
    parser.add_argument("--side", choices=("BUY", "SELL"), default="BUY")
    parser.add_argument("--quantity", type=Decimal, default=Decimal("0.001"))
    args = parser.parse_args()

    api_key = os.environ.get("BINANCE_API_KEY")
    api_secret = os.environ.get("BINANCE_API_SECRET")
    if args.place_test_order and os.environ.get("BINANCE_SMOKE_ALLOW_ORDER") != "1":
        raise SystemExit("Refusing order placement: set BINANCE_SMOKE_ALLOW_ORDER=1 explicitly")
    if (api_key is None) != (api_secret is None):
        raise SystemExit("BINANCE_API_KEY and BINANCE_API_SECRET must be provided together")
    if args.place_test_order and (not api_key or not api_secret):
        raise SystemExit("Order placement requires BINANCE_API_KEY and BINANCE_API_SECRET")

    output_dir = Path(os.environ.get("BINANCE_SMOKE_OUTPUT", "recon/output"))
    output_dir.mkdir(parents=True, exist_ok=True)
    event_path = output_dir / "binance_testnet_smoke_events.csv"
    event_store_path = output_dir / "binance_testnet_smoke_events.jsonl"
    engine = BacktestEngine(event_path, event_store_path)
    try:
        runtime = engine.add_binance_futures(
            [args.symbol], "TESTNET", api_key, api_secret,
        )
        metadata = runtime.discoverInstruments()
        instrument = next(item for item in metadata if item.symbol() == args.symbol.upper())
        print(f"exchange_info symbol={instrument.symbol()} tick={instrument.priceTickSize()} step={instrument.quantityStepSize()}")
        if api_key and api_secret:
            account = runtime.synchronizeAccount()
            print(f"account currency={account.currency()} wallet={account.walletBalance()} available={account.availableBalance()}")
        engine.start()
        print(f"streams market={runtime.adapter().marketStreamUrl()}")
        if args.place_test_order:
            depth = json.loads(str(runtime.adapter().depthJson(args.symbol, 5)))
            if args.side == "BUY":
                reference = Decimal(depth["bids"][0][0])
                tick_size = Decimal(str(instrument.priceTickSize()))
                price = (reference * Decimal("0.5")).quantize(tick_size, rounding=ROUND_FLOOR)
            else:
                reference = Decimal(depth["asks"][0][0])
                tick_size = Decimal(str(instrument.priceTickSize()))
                price = (reference * Decimal("1.5")).quantize(tick_size, rounding=ROUND_CEILING)
            step_size = Decimal(str(instrument.quantityStepSize()))
            minimum_quantity = Decimal(str(instrument.minQuantity()))
            quantity = args.quantity.quantize(step_size, rounding=ROUND_FLOOR)
            if quantity < minimum_quantity:
                raise SystemExit(f"quantity {quantity} is below exchange minimum {instrument.minQuantity()}")
            limit_order = java_limit_order(args.symbol, args.side, int(quantity), float(price))
            runtime.submitLimitOrder(limit_order)
            time.sleep(min(args.seconds, 3.0))
            cancel = java_cancel_order(args.symbol, str(limit_order.orderId()))
            if not runtime.cancelOrder(cancel):
                raise SystemExit("Binance cancel request failed")
            print(f"SMOKE PASS submit-cancel symbol={args.symbol.upper()} side={args.side} quantity={quantity} price={price}")
            return
        time.sleep(max(0.0, args.seconds))
        print("SMOKE PASS public/lifecycle")
    finally:
        engine.close()
        shutdown_jvm()


def java_limit_order(symbol: str, side: str, quantity: int, price: float) -> object:
    limit_type = java_class("com.abc.trading.execution.LimitOrderIntent")
    direction = java_class("com.abc.trading.execution.SignalDirection").valueOf(side)
    return limit_type("binance-smoke", symbol.upper(), 1, time.time_ns(), "binance-smoke",
                      f"smoke-{time.time_ns()}", direction, quantity, price, 0, 0.0)


def java_cancel_order(symbol: str, client_order_id: str) -> object:
    cancel_type = java_class("com.abc.trading.execution.commands.CancelOrder")
    return cancel_type("binance-smoke", symbol.upper(), client_order_id,
                       f"cancel-{time.time_ns()}", time.time_ns())


if __name__ == "__main__":
    main()
