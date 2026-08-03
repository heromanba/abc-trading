package com.abc.trading.msgbus.types;

public class QuoteTick {
    public String symbol;
    public double bid;
    public double ask;
    public long ts;

    public QuoteTick() { this("", 0.0, 0.0, 0L); }

    public QuoteTick(String symbol, double bid, double ask, long ts) {
        this.symbol = symbol;
        this.bid = bid;
        this.ask = ask;
        this.ts = ts;
    }

    @Override
    public String toString() {
        return "QuoteTick{" + "symbol='" + symbol + '\'' + ", bid=" + bid + ", ask=" + ask + ", ts=" + ts + '}';
    }
}
