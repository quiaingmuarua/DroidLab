package com.app.androidlab.sample;

import android.content.Context;
import android.util.Log;

import com.app.androidlab.sample.scenario_control.AndroidLabGeneratedContracts;
import com.app.androidlab.sample.scenario_control.AndroidLabWorkerEvents;
import com.xa.mass.android.capabilityhttp.AndroidCapabilityHttpServer;

public class HttpServerManager {
    /**
     * App-side control-surface entrypoint for android-lab.
     *
     * <p>Keep this class thin. It should wire server startup only. Scenario-facing capability
     * definitions belong in {@code scenario_control.AndroidLabWorkerEvents}.
     */
    private static AndroidCapabilityHttpServer server;

    private HttpServerManager() {
    }

    public static synchronized void initServer(Context context) {
        if (server != null) {
            Log.i(
                    "AndroidLabHttpServer",
                    "server already started on port="
                            + AndroidLabGeneratedContracts.PORT
            );
            return;
        }

        try {
            server = AndroidCapabilityHttpServer.create(
                    AndroidLabGeneratedContracts.PORT,
                    AndroidLabWorkerEvents.create(context)
            );
            server.start();
            Log.i(
                    "AndroidLabHttpServer",
                    "server started on port="
                            + AndroidLabGeneratedContracts.PORT
            );
        } catch (Exception e) {
            server = null;
            Log.e(
                    "AndroidLabHttpServer",
                    "initServer failed",
                    e
            );
        }
    }
}
