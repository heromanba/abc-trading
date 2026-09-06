package com.abc.trading.model.identifiers;

public record StrategyId(String value) {
    public StrategyId {
        Identifier.validate(value, "StrategyId");
    }
    @Override public String toString() { return value; }
}
