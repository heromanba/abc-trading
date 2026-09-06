package com.abc.trading.model.identifiers;

public record InstrumentId(String value) {
    public InstrumentId {
        Identifier.validate(value, "InstrumentId");
    }
    @Override public String toString() { return value; }
}
