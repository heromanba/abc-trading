package com.abc.trading.risk;

public record RiskDecision(boolean approved, String reason) {
    public static RiskDecision allow() {
        return new RiskDecision(true, "approved");
    }

    public static RiskDecision rejected(String reason) {
        return new RiskDecision(false, reason);
    }
}