package com.abc.trading.data;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuantityTest {
    @Test
    void preservesRustStyleRawValueAndPrecision() {
        Quantity quantity = Quantity.fromString("0.001", 3);

        assertEquals(1L, quantity.raw());
        assertEquals(3, quantity.precision());
        assertEquals(new BigDecimal("0.001"), quantity.asDecimal());
    }

    @Test
    void performsExactArithmeticAcrossPrecisions() {
        Quantity left = Quantity.fromString("1.25", 2);
        Quantity right = Quantity.fromString("0.125", 3);

        assertEquals(new BigDecimal("1.375"), left.add(right).asDecimal());
        assertEquals(new BigDecimal("1.125"), left.subtract(right).asDecimal());
    }

    @Test
    void rejectsUnrepresentablePrecisionAndNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> Quantity.fromString("0.0015", 3));
        assertThrows(IllegalArgumentException.class, () -> Quantity.fromString("-1", 0));
    }
}
