package com.abc.trading.backtest;

import com.abc.trading.data.Bar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Historical data iterator boundary corresponding to Nautilus BacktestDataIterator. */
public final class BacktestDataIterator {
    private final List<Bar> data = new ArrayList<>();
    private int cursor;

    public void addData(List<Bar> bars, boolean sort) {
        data.addAll(bars);
        if (sort) data.sort(Comparator.comparingLong(Bar::tsInit).thenComparing(Bar::symbol));
        cursor = 0;
    }

    public boolean hasNext() {
        return cursor < data.size();
    }

    public Bar next() {
        if (!hasNext()) throw new IllegalStateException("No historical data remains");
        return data.get(cursor++);
    }

    public void reset() {
        cursor = 0;
    }

    public void clear() {
        data.clear();
        cursor = 0;
    }

    public int size() {
        return data.size();
    }
}
