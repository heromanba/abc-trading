package com.abc.trading.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VenueIdTest {
    @Test
    void validatesFormatButAllowsCustomVenueIds() {
        VenueId custom = new VenueId("BINANCE");

        assertEquals("BINANCE", custom.value());
        assertFalse(custom.isSupported());
    }

    @Test
    void resolvesKnownVenueCodesThroughTheSupportedRegistry() {
        VenueId venue = VenueId.fromCode("XCME");

        assertEquals("XCME", venue.value());
        assertTrue(venue.isSupported());
        assertEquals(venue, VenueId.supportedVenues().get("XCME"));
    }

    @Test
    void rejectsUnknownCodesOnlyThroughFromCode() {
        assertThrows(IllegalArgumentException.class, () -> VenueId.fromCode("NYSE"));
        assertDoesNotThrow(() -> new VenueId("NYSE"));
    }

    @Test
    void supportsSyntheticVenue() {
        VenueId synthetic = VenueId.synthetic();

        assertTrue(synthetic.isSynthetic());
        assertEquals("SYNTH", synthetic.value());
    }

    @Test
    void matchesRustAsciiAndWhitespaceValidation() {
        assertThrows(IllegalArgumentException.class, () -> new VenueId(""));
        assertThrows(IllegalArgumentException.class, () -> new VenueId("   "));
        assertThrows(IllegalArgumentException.class, () -> new VenueId("ÉU"));
    }

    private static void assertDoesNotThrow(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            throw new AssertionError("Expected no exception", exception);
        }
    }
}