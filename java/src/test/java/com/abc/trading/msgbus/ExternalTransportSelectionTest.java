package com.abc.trading.msgbus;

import com.abc.trading.system.NautilusKernel;
import com.abc.trading.system.NautilusKernelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExternalTransportSelectionTest {
    @Test
    void createsTheConfiguredAeronBackingThroughKernelConfig() {
        NautilusKernelConfig config = new NautilusKernelConfig(
                "aeron-kernel", false, false,
                ExternalTransportSelection.aeron(AeronMessageBusConfig.embedded()));
        try (NautilusKernel kernel = new NautilusKernel(config)) {
            assertEquals(ExternalTransportSelection.Type.AERON,
                    kernel.config().externalTransport().type());
            assertEquals(true, kernel.bus().isExternalConfigured());
            kernel.start();
        }
    }

    @Test
    void defaultsToNoExternalTransport() {
        assertEquals(ExternalTransportSelection.Type.NONE,
                NautilusKernelConfig.defaults().externalTransport().type());
    }
}
