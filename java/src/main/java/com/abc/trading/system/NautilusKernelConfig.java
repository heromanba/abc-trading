package com.abc.trading.system;

import com.abc.trading.msgbus.ExternalTransportSelection;

public record NautilusKernelConfig(
        String name,
        boolean loadState,
        boolean saveState,
        ExternalTransportSelection externalTransport) {
    public NautilusKernelConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (externalTransport == null) throw new IllegalArgumentException("externalTransport is required");
    }

    public NautilusKernelConfig(String name, boolean loadState, boolean saveState) {
        this(name, loadState, saveState, ExternalTransportSelection.none());
    }

    public static NautilusKernelConfig defaults() {
        return new NautilusKernelConfig("NautilusKernel", false, false,
                ExternalTransportSelection.none());
    }
}
