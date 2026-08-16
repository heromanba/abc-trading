package com.abc.trading.backtest;

import com.abc.trading.execution.VenueId;

public record SimulatedVenueConfig(
        VenueId venue,
        boolean fillAtBarClose,
        boolean enableMatching) {
    public static SimulatedVenueConfig defaults(VenueId venue) {
        return new SimulatedVenueConfig(venue, true, true);
    }
}
