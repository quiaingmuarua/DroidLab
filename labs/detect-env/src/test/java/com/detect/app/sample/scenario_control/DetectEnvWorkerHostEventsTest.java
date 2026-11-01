package com.detect.app.sample.scenario_control;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.content.Context;

import com.xa.mass.worker.execution.WorkerEventDefinition;
import com.xa.mass.worker.runtime.WorkerLifecycle;
import com.xa.mass.workerdelivery.json.Jsons;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DetectEnvWorkerHostEventsTest {

    @Test
    public void appendsLocalHostEventsWithoutChangingBusinessDefinitions()
            throws Exception {
        List<WorkerEventDefinition<?>> business =
                new ArrayList<>(DetectEnvWorkerEvents.create(
                        mock(Context.class)
                ));
        FakeWorker worker = new FakeWorker();

        List<WorkerEventDefinition<?>> assembled =
                DetectEnvWorkerHostEvents.assemble(business, worker);

        assertEquals(8, assembled.size());
        for (int index = 0; index < business.size(); index++) {
            assertSame(business.get(index), assembled.get(index));
        }
        assertEquals(
                DetectEnvWorkerHostEvents.SNAPSHOT_EVENT,
                assembled.get(5).eventName()
        );
        assertEquals(
                DetectEnvWorkerHostEvents.START_EVENT,
                assembled.get(6).eventName()
        );
        assertEquals(
                DetectEnvWorkerHostEvents.STOP_EVENT,
                assembled.get(7).eventName()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> assembled.add(business.get(0))
        );
    }

    @Test
    public void exposesSnapshotAndForwardsLifecycleRequests() throws Exception {
        FakeWorker worker = new FakeWorker();
        List<WorkerEventDefinition<?>> definitions =
                DetectEnvWorkerHostEvents.assemble(
                        DetectEnvWorkerEvents.create(mock(Context.class)),
                        worker
                );

        Map<String, Object> initial = invokeObject(
                find(definitions, DetectEnvWorkerHostEvents.SNAPSHOT_EVENT)
        );
        assertEquals("STOPPED", initial.get("state"));
        assertEquals("worker-proof-id", initial.get("workerId"));
        assertEquals(
                "ws://127.0.0.1:18083/worker",
                initial.get("endpointUri")
        );
        assertEquals("not started", initial.get("diagnosticMessage"));

        Map<String, Object> started = invokeObject(
                find(definitions, DetectEnvWorkerHostEvents.START_EVENT)
        );
        assertEquals(Boolean.TRUE, started.get("accepted"));
        assertEquals("RUNNING", started.get("requestedState"));
        assertEquals(1, worker.startCalls);
        assertEquals(WorkerLifecycle.State.RUNNING, worker.state);

        Map<String, Object> stopped = invokeObject(
                find(definitions, DetectEnvWorkerHostEvents.STOP_EVENT)
        );
        assertEquals(Boolean.TRUE, stopped.get("accepted"));
        assertEquals("STOPPED", stopped.get("requestedState"));
        assertEquals(1, worker.stopCalls);
        assertEquals(WorkerLifecycle.State.STOPPED, worker.state);
    }

    @Test
    public void rejectsMissingAssemblyInputs() {
        FakeWorker worker = new FakeWorker();
        assertThrows(
                NullPointerException.class,
                () -> DetectEnvWorkerHostEvents.assemble(null, worker)
        );
        assertThrows(
                NullPointerException.class,
                () -> DetectEnvWorkerHostEvents.assemble(
                        new ArrayList<>(),
                        null
                )
        );
    }

    private static WorkerEventDefinition<?> find(
            Collection<? extends WorkerEventDefinition<?>> definitions,
            String eventName
    ) {
        for (WorkerEventDefinition<?> definition : definitions) {
            if (definition.eventName().equals(eventName)) {
                return definition;
            }
        }
        throw new AssertionError("Missing definition " + eventName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> invokeObject(
            WorkerEventDefinition<?> definition
    ) throws Exception {
        WorkerEventDefinition<Map<String, Object>> typed =
                (WorkerEventDefinition<Map<String, Object>>) definition;
        Map<String, Object> parameters = typed.parameterResolver()
                .resolve("{}");
        return new LinkedHashMap<>(Jsons.parseObject(
                typed.handler().execute(parameters)
        ));
    }

    private static final class FakeWorker implements WorkerLifecycle {

        private State state = State.STOPPED;
        private int startCalls;
        private int stopCalls;

        @Override
        public void start() {
            startCalls++;
            state = State.RUNNING;
        }

        @Override
        public void stop() {
            stopCalls++;
            state = State.STOPPED;
        }

        @Override
        public Snapshot snapshot() {
            return new Snapshot(
                    state,
                    "worker-proof-id",
                    URI.create("ws://127.0.0.1:18083/worker"),
                    state == State.STOPPED ? "not started" : null
            );
        }

        @Override
        public void addListener(Listener listener) {
        }

        @Override
        public void removeListener(Listener listener) {
        }

        @Override
        public void close() {
        }
    }
}
