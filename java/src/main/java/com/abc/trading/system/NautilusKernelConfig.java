package com.abc.trading.system;

public record NautilusKernelConfig(
        String name,
        boolean loadState,
        boolean saveState) {
    public NautilusKernelConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
    }

    public static NautilusKernelConfig defaults() {
        return new NautilusKernelConfig("NautilusKernel", false, false);
    }
}
