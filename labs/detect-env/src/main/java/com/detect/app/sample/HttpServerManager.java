package com.detect.app.sample;

import android.content.Context;
import android.util.Log;

import com.detect.app.sample.scenario_control.DetectEnvGeneratedContracts;
import com.xa.mass.android.capabilityhttp.AndroidCapabilityHttpServer;
import com.xa.mass.worker.execution.WorkerEventDefinition;

import java.util.Collection;


public class HttpServerManager {
    /**
     * App-side control-surface entrypoint for detect-env.
     *
     * <p>Keep this class thin. It should wire server startup only. Scenario-facing capability
     * definitions belong in {@code scenario_control.DetectEnvWorkerEvents}; check-specific
     * verdict shaping belongs in {@code scenario_control.DetectEnvCheckHandlers}; generated scenario
     * truth belongs in {@code scenario_control.DetectEnvGeneratedContracts}.
     */
    private static AndroidCapabilityHttpServer server;

    private HttpServerManager() {
    }

    public static synchronized void initServer(
            Context context,
            Collection<? extends WorkerEventDefinition<?>> definitions
    ) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        if (definitions == null) {
            throw new IllegalArgumentException("definitions are required");
        }
        if (server != null) {
            Log.i(
                    "HttpServerManager",
                    "server already started on port="
                            + DetectEnvGeneratedContracts.PORT
            );
            return;
        }

        try {
            server = AndroidCapabilityHttpServer.create(
                    DetectEnvGeneratedContracts.PORT,
                    definitions
            );
            server.start();
            Log.i(
                    "HttpServerManager",
                    "server started on port="
                            + DetectEnvGeneratedContracts.PORT
            );
        } catch (Exception e) {
            server = null;
            Log.e("HttpServerManager", "initServer failed", e);
        }
    }

    public static synchronized void closeServer() {
        if (server == null) {
            return;
        }
        server.close();
        server = null;
    }
}
