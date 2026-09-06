package com.abc.trading.model.identifiers;

final class Identifier {
    private Identifier() { }

    static void validate(String value, String type) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(type + " must not be blank");
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) > 127) throw new IllegalArgumentException(type + " must be ASCII");
        }
    }
}
