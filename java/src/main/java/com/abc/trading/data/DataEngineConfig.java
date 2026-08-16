package com.abc.trading.data;

public record DataEngineConfig(
        boolean validateData,
        boolean runAggregators) {
    public static DataEngineConfig defaults() {
        return new DataEngineConfig(true, true);
    }
}
