package com.app.androidlab.sample.scenario_control;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import android.content.Context;
import com.xa.mass.worker.execution.WorkerEventDefinition;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public final class AndroidLabWorkerEventsTest {

    @Test
    public void exposesExactLocalEventSet() {
        assertEquals(
                Arrays.asList(
                        "extension.worker.android-lab.system-build-info",
                        "extension.worker.android-lab.system-properties",
                        "extension.worker.android-lab.scenario-catalog"
                ),
                AndroidLabWorkerEvents.eventNames()
        );
    }

    @Test
    public void exposesOnlyExtensionsAndValidScenarioReferences() {
        List<WorkerEventDefinition<?>> definitions =
                AndroidLabWorkerEvents.create(mock(Context.class));
        assertEquals(3, definitions.size());
        for (WorkerEventDefinition<?> definition : definitions) {
            assertEquals(true, definition.eventName().startsWith(
                    "extension.worker.android-lab."
            ));
        }
        HashSet<String> events = new HashSet<>(
                AndroidLabWorkerEvents.eventNames()
        );
        for (AndroidLabScenarioModels.GeneratedScenarioDefinition scenario
                : AndroidLabGeneratedContracts.buildScenarioDefinitions()) {
            assertEquals(true, events.contains(scenario.verify_event));
        }
    }
}
