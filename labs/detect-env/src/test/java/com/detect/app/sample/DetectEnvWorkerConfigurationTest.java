package com.detect.app.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.net.URI;

public final class DetectEnvWorkerConfigurationTest {

    @Test
    public void blankRuntimeUrlKeepsStandaloneMode() {
        assertNull(DetectEnvWorkerConfiguration.runtimeApiBaseUrl(null));
        assertNull(DetectEnvWorkerConfiguration.runtimeApiBaseUrl("  "));
    }

    @Test
    public void acceptsAbsoluteHttpRuntimeUrl() {
        assertEquals(
                URI.create("http://127.0.0.1:18082"),
                DetectEnvWorkerConfiguration.runtimeApiBaseUrl(
                        " http://127.0.0.1:18082 "
                )
        );
        assertEquals(
                URI.create("https://runtime.example.test/api"),
                DetectEnvWorkerConfiguration.runtimeApiBaseUrl(
                        "https://runtime.example.test/api"
                )
        );
    }

    @Test
    public void rejectsUnsupportedOrRelativeRuntimeUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DetectEnvWorkerConfiguration.runtimeApiBaseUrl(
                        "ws://127.0.0.1:18083"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DetectEnvWorkerConfiguration.runtimeApiBaseUrl(
                        "/api/v1"
                )
        );
    }

    @Test
    public void normalizesAndRequiresWorkerGroupId() {
        assertEquals(
                "agentforge-detect-env-workers",
                DetectEnvWorkerConfiguration.workerGroupId(
                        " agentforge-detect-env-workers "
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DetectEnvWorkerConfiguration.workerGroupId(" ")
        );
    }
}
