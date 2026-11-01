package com.detect.app.sample.scenario_control;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import android.content.Context;
import com.xa.mass.worker.execution.WorkerEventDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public final class DetectEnvWorkerEventsTest {

    @Test
    public void exposesExactLocalEventSet() {
        assertEquals(
                Arrays.asList(
                        "extension.worker.detect-env.networking",
                        "extension.worker.detect-env.debug-state",
                        "extension.worker.detect-env.device-properties",
                        "extension.worker.detect-env.class-loader-state",
                        "extension.worker.detect-env.scenario-catalog"
                ),
                DetectEnvWorkerEvents.eventNames()
        );
    }

    @Test
    public void exposesOnlyExtensionsAndValidScenarioReferences() {
        List<WorkerEventDefinition<?>> definitions =
                DetectEnvWorkerEvents.create(mock(Context.class));
        assertEquals(5, definitions.size());
        for (WorkerEventDefinition<?> definition : definitions) {
            assertEquals(true, definition.eventName().startsWith(
                    "extension.worker.detect-env."
            ));
        }
        HashSet<String> events = new HashSet<>(
                DetectEnvWorkerEvents.eventNames()
        );
        for (DetectEnvScenarioModels.GeneratedScenarioDefinition scenario
                : DetectEnvGeneratedContracts.buildScenarioDefinitions()) {
            assertEquals(true, events.contains(scenario.getVerify_event()));
        }
    }
}
