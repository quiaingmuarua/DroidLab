package com.app.androidlab.sample.scenario_control;

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

public final class AndroidLabWorkerEvents {

    private static final Gson GSON = new Gson();

    private AndroidLabWorkerEvents() {
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
                AndroidLabGeneratedContracts.CAPABILITY_SYSTEM_BUILD_INFO,
                AndroidLabCheckHandlers::buildSystemBuildInfoCheckResult
        ));
        definitions.add(appContextEvent(
                appContext,
                AndroidLabGeneratedContracts.CAPABILITY_SYSTEM_PROPERTIES,
                AndroidLabCheckHandlers::
                        buildSystemPropertiesCheckResult
        ));
        definitions.add(event(
                AndroidLabGeneratedContracts.CAPABILITY_SCENARIO_CATALOG,
                AndroidLabWorkerEvents::scenarioCatalog
        ));
        return Collections.unmodifiableList(definitions);
    }

    public static List<String> eventNames() {
        return Collections.unmodifiableList(Arrays.asList(
                AndroidLabGeneratedContracts.EVENT_SYSTEM_BUILD_INFO,
                AndroidLabGeneratedContracts.EVENT_SYSTEM_PROPERTIES,
                AndroidLabGeneratedContracts.EVENT_SCENARIO_CATALOG
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
                    Log.e("AndroidLabWorker", String.valueOf(result));
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

    private static AndroidLabScenarioModels.ScenarioCatalogResult
    scenarioCatalog() {
        return new AndroidLabScenarioModels.ScenarioCatalogResult(
                AndroidLabGeneratedContracts.EVENT_SCENARIO_CATALOG,
                "Collected generated scenario definitions "
                        + "for android-lab.",
                AndroidLabGeneratedContracts.buildScenarioDefinitions()
        );
    }

    @FunctionalInterface
    private interface LocalHandler {
        Object execute();
    }
}
