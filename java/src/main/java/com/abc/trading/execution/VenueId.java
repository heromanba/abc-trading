package com.abc.trading.execution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record VenueId(String value) {
    public static final String SYNTHETIC_VENUE = "SYNTH";
    private static final Map<String, VenueId> SUPPORTED = createSupportedVenues();

    public VenueId {
        validate(value);
    }

    public static VenueId fromCode(String code) {
        validate(code);
        VenueId venue = SUPPORTED.get(code);
        if (venue == null) throw new IllegalArgumentException("Unknown venue code: " + code);
        return venue;
    }

    public static Map<String, VenueId> supportedVenues() {
        return SUPPORTED;
    }

    public static VenueId synthetic() {
        return new VenueId(SYNTHETIC_VENUE);
    }

    public boolean isSynthetic() {
        return SYNTHETIC_VENUE.equals(value);
    }

    public boolean isSupported() {
        return SUPPORTED.containsKey(value);
    }

    private static void validate(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("invalid string for 'value', was empty");
        }
        boolean hasNonWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character)) hasNonWhitespace = true;
            if (character > 127) {
                throw new IllegalArgumentException("invalid non-ASCII venue value: " + value);
            }
        }
        if (!hasNonWhitespace) {
            throw new IllegalArgumentException("invalid string for 'value', was whitespace");
        }
    }

    private static Map<String, VenueId> createSupportedVenues() {
        Map<String, VenueId> venues = new LinkedHashMap<>();
        for (String code : new String[] {"CBCM", "GLBX", "NYUM", "XCBT", "XCEC", "XCME", "XFXS", "XNYM"}) {
            venues.put(code, new VenueId(code));
        }
        return Collections.unmodifiableMap(venues);
    }
}