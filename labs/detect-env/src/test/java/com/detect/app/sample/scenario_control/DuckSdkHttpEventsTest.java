package com.detect.app.sample.scenario_control;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import android.content.Context;
import com.xa.mass.worker.execution.WorkerEventDefinition;
import com.xa.mass.worker.runtime.WorkerLifecycle;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;

public class DuckSdkHttpEventsTest {
    @Test public void duckIsLocalOnlyAndBothHttpAssembliesHaveExactSets() {
        Context context = mock(Context.class);
        List<WorkerEventDefinition<?>> business = DetectEnvWorkerEvents.create(context);
        assertEquals(5, business.size());
        List<WorkerEventDefinition<?>> standalone = DuckSdkHttpEvents.assemble(context, business);
        assertEquals(6, standalone.size());
        assertEquals(DuckSdkHttpEvents.SCAN_EVENT, standalone.get(5).eventName());
        List<WorkerEventDefinition<?>> configured = DuckSdkHttpEvents.assemble(context,
                DetectEnvWorkerHostEvents.assemble(business, mock(WorkerLifecycle.class)));
        assertEquals(9, configured.size());
        HashSet<String> names = new HashSet<>();
        for (WorkerEventDefinition<?> definition : configured) names.add(definition.eventName());
        assertEquals(9, names.size());
        for (int index = 0; index < business.size(); index++) {
            assertSame(business.get(index), standalone.get(index));
            assertSame(business.get(index), configured.get(index));
            assertNotEquals(DuckSdkHttpEvents.SCAN_EVENT, business.get(index).eventName());
        }
    }
}
