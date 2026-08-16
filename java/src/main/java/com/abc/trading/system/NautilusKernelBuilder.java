package com.abc.trading.system;

public final class NautilusKernelBuilder {
    private NautilusKernelConfig config = NautilusKernelConfig.defaults();

    public NautilusKernelBuilder config(NautilusKernelConfig config) {
        this.config = config;
        return this;
    }

    public NautilusKernel build() {
        return new NautilusKernel(config);
    }
}
