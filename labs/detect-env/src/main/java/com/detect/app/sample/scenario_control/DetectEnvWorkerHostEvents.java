package com.detect.app.sample.scenario_control;

import com.xa.mass.worker.execution.WorkerEventDefinition;
import com.xa.mass.worker.execution.WorkerEventParameterResolvers;
import com.xa.mass.worker.runtime.WorkerLifecycle;
import com.xa.mass.workerdelivery.json.Jsons;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Local-only lifecycle controls for the detect-env Android Worker host. */
public final class DetectEnvWorkerHostEvents {

    public static final String SNAPSHOT_EVENT =
            "extension.worker.detect-env.host.snapshot";
    public static final String START_EVENT =
            "extension.worker.detect-env.host.start";
    public static final String STOP_EVENT =
            "extension.worker.detect-env.host.stop";

    private static final String SNAPSHOT_CAPABILITY =
            "detect-env.host.snapshot";
    private static final String START_CAPABILITY =
            "detect-env.host.start";
    private static final String STOP_CAPABILITY =
            "detect-env.host.stop";

    private DetectEnvWorkerHostEvents() {
    }

    public static List<WorkerEventDefinition<?>> assemble(
            Collection<? extends WorkerEventDefinition<?>> businessDefinitions,
            WorkerLifecycle worker
    ) {
        Objects.requireNonNull(businessDefinitions, "businessDefinitions");
        WorkerLifecycle resolvedWorker = Objects.requireNonNull(
                worker,
                "worker"
        );
        List<WorkerEventDefinition<?>> definitions = new ArrayList<>(
                businessDefinitions.size() + 3
        );
        for (WorkerEventDefinition<?> definition : businessDefinitions) {
            definitions.add(Objects.requireNonNull(
                    definition,
                    "businessDefinition"
            ));
        }
        definitions.add(WorkerEventDefinition.extension(
                SNAPSHOT_CAPABILITY,
                WorkerEventParameterResolvers.jsonMap(),
                ignored -> snapshot(resolvedWorker)
        ));
        definitions.add(WorkerEventDefinition.extension(
                START_CAPABILITY,
                WorkerEventParameterResolvers.jsonMap(),
                ignored -> requestStart(resolvedWorker)
        ));
        definitions.add(WorkerEventDefinition.extension(
                STOP_CAPABILITY,
                WorkerEventParameterResolvers.jsonMap(),
                ignored -> requestStop(resolvedWorker)
        ));
        return Collections.unmodifiableList(definitions);
    }

    private static String snapshot(WorkerLifecycle worker) {
        WorkerLifecycle.Snapshot snapshot = worker.snapshot();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("state", snapshot.state().name());
        result.put("workerId", snapshot.workerId());
        URI endpointUri = snapshot.endpointUri();
        result.put(
                "endpointUri",
                endpointUri == null ? null : endpointUri.toString()
        );
        result.put("diagnosticMessage", snapshot.diagnosticMessage());
        return Jsons.toJson(result);
    }

    private static String requestStart(WorkerLifecycle worker) {
        worker.start();
        return accepted(WorkerLifecycle.State.RUNNING);
    }

    private static String requestStop(WorkerLifecycle worker) {
        worker.stop();
        return accepted(WorkerLifecycle.State.STOPPED);
    }

    private static String accepted(WorkerLifecycle.State requestedState) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("requestedState", requestedState.name());
        return Jsons.toJson(result);
    }
}
