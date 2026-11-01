package com.detect.app.sample.scenario_control;

import android.content.Context;
import com.detect.app.sample.duck.DuckSdk;
import com.xa.mass.worker.execution.WorkerEventDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Duck scans are local HTTP capabilities, never WorkerGroup business definitions. */
public final class DuckSdkHttpEvents {
    public static final String SCAN_EVENT = "extension.worker.detect-env.duck.scan";

    private DuckSdkHttpEvents() {}

    public static List<WorkerEventDefinition<?>> assemble(
            Context context, Collection<? extends WorkerEventDefinition<?>> existing) {
        Context application = context.getApplicationContext();
        List<WorkerEventDefinition<?>> definitions = new ArrayList<>(existing);
        definitions.add(WorkerEventDefinition.extension(
                "detect-env.duck.scan", DuckSdk::parseDetectorIds,
                ids -> DuckSdk.scanBlocking(application, ids)));
        return Collections.unmodifiableList(definitions);
    }
}
