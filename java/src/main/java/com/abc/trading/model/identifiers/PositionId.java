package com.abc.trading.model.identifiers;

public record PositionId(String value) {
    public PositionId {
        Identifier.validate(value, "PositionId");
    }
    @Override public String toString() { return value; }
}
