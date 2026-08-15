package com.abc.trading.reconciliation;

public record ReconciliationResult(boolean matched, long comparedRows, String mismatch) {
    public static ReconciliationResult matched(long comparedRows) {
        return new ReconciliationResult(true, comparedRows, "");
    }

    public static ReconciliationResult mismatch(long row, String detail) {
        return new ReconciliationResult(false, row, detail);
    }
}
