package com.abc.trading.backtest;

import com.abc.trading.execution.VenueId;
import com.abc.trading.execution.FeeModel;
import com.abc.trading.execution.LatencyModel;
import com.abc.trading.execution.MakerTakerFeeModel;
import com.abc.trading.execution.StaticLatencyModel;

public record SimulatedVenueConfig(
        VenueId venue,
        boolean fillAtBarClose,
        boolean enableMatching,
        LatencyModel latencyModel,
        FeeModel feeModel) {
    public static SimulatedVenueConfig defaults(VenueId venue) {
        return new SimulatedVenueConfig(
                venue, true, true, StaticLatencyModel.zero(), MakerTakerFeeModel.zero());
    }
}
