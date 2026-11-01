package com.detect.app.sample.scenario_control;

import android.content.Context;
import android.util.Log;
import com.google.gson.Gson;
import com.xa.mass.workerdelivery.json.Jsons;
import com.xa.mass.worker.execution.WorkerEventDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class DetectEnvWorkerEvents {

    private static final Gson GSON = new Gson();

    private DetectEnvWorkerEvents() {
    }

    public static List<WorkerEventDefinition<?>> create(
            Context context
    ) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        Context appContext = context.getApplicationContext();
        if (appContext == null) {
            appContext = context;
        }
        List<WorkerEventDefinition<?>> definitions =
                new ArrayList<>();
        definitions.add(appContextEvent(
                appContext,
                DetectEnvGeneratedContracts.CAPABILITY_NETWORKING,
                DetectEnvCheckHandlers::buildNetworkingCheckResult
        ));
        definitions.add(appContextEvent(
                appContext,
                DetectEnvGeneratedContracts.CAPABILITY_DEBUG_STATE,
                DetectEnvCheckHandlers::buildDebugStateCheckResult
        ));
        definitions.add(appContextEvent(
                appContext,
                DetectEnvGeneratedContracts.CAPABILITY_DEVICE_PROPERTIES,
                DetectEnvCheckHandlers::buildDevicePropertyCheckResult
        ));
        definitions.add(appContextEvent(
                appContext,
                DetectEnvGeneratedContracts.CAPABILITY_CLASS_LOADER_STATE,
                DetectEnvCheckHandlers::buildClassLoaderStateCheckResult
        ));
        definitions.add(event(
                DetectEnvGeneratedContracts.CAPABILITY_SCENARIO_CATALOG,
                DetectEnvWorkerEvents::scenarioCatalog
        ));
        return Collections.unmodifiableList(definitions);
    }

    public static List<String> eventNames() {
        return Collections.unmodifiableList(Arrays.asList(
                DetectEnvGeneratedContracts.EVENT_NETWORKING,
                DetectEnvGeneratedContracts.EVENT_DEBUG_STATE,
                DetectEnvGeneratedContracts.EVENT_DEVICE_PROPERTIES,
                DetectEnvGeneratedContracts.EVENT_CLASS_LOADER_STATE,
                DetectEnvGeneratedContracts.EVENT_SCENARIO_CATALOG
        ));
    }

    private static <T> WorkerEventDefinition<String>
    appContextEvent(
            Context context,
            String capabilityName,
            Function<Context, T> handler
    ) {
        return event(
                capabilityName,
                () -> {
                    T result = handler.apply(context);
                    Log.i(
                            "DetectEnvWorker",
                            "event completed: extension.worker."
                                    + capabilityName
                    );
                    return result;
                }
        );
    }

    private static WorkerEventDefinition<String> event(
            String capabilityName,
            LocalHandler handler
    ) {
        return WorkerEventDefinition.extension(
                capabilityName,
                payload -> {
                    Jsons.parseObject(payload);
                    return payload;
                },
                ignored -> GSON.toJson(handler.execute())
        );
    }

    private static DetectEnvScenarioModels.ScenarioCatalogResult
    scenarioCatalog() {
        return new DetectEnvScenarioModels.ScenarioCatalogResult(
                DetectEnvGeneratedContracts.EVENT_SCENARIO_CATALOG,
                "Collected generated scenario definitions for detect-env.",
                DetectEnvGeneratedContracts.buildScenarioDefinitions()
        );
    }

    @FunctionalInterface
    private interface LocalHandler {
        Object execute();
    }
}
