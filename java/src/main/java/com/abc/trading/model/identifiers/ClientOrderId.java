package com.abc.trading.model.identifiers;

public record ClientOrderId(String value) {
    public ClientOrderId {
        Identifier.validate(value, "ClientOrderId");
    }
    @Override public String toString() { return value; }
}
