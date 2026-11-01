package com.detect.app.sample;

import java.net.URI;

final class DetectEnvWorkerConfiguration {

    private DetectEnvWorkerConfiguration() {
    }

    static URI runtimeApiBaseUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        URI resolved = URI.create(value.trim());
        String scheme = resolved.getScheme();
        if (!resolved.isAbsolute()
                || resolved.getHost() == null
                || (!("http".equalsIgnoreCase(scheme))
                && !("https".equalsIgnoreCase(scheme)))) {
            throw new IllegalArgumentException(
                    "Worker Runtime API base URL must be absolute HTTP(S)"
            );
        }
        return resolved;
    }

    static String workerGroupId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "WorkerGroup ID must be non-blank"
            );
        }
        return value.trim();
    }
}
