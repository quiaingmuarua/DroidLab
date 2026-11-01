package com.detect.app.sample;

import android.app.Application;

import com.detect.app.sample.scenario_control.DetectEnvWorkerEvents;
import com.detect.app.sample.scenario_control.DetectEnvWorkerHostEvents;
import com.detect.app.sample.scenario_control.DuckSdkHttpEvents;
import com.detect.app.sample.worker.DetectEnvDeviceProperties;
import com.xa.mass.worker.android.AndroidWorker;
import com.xa.mass.worker.execution.WorkerEventDefinition;

import java.net.URI;
import java.util.Collection;

public class DetectEnvApplication extends Application {

    private AndroidWorker worker;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isMainProcess(getPackageName(), Application.getProcessName())) {
            return;
        }
        Collection<? extends WorkerEventDefinition<?>> businessDefinitions =
                DetectEnvWorkerEvents.create(this);
        URI runtimeApiBaseUrl = DetectEnvWorkerConfiguration
                .runtimeApiBaseUrl(BuildConfig.WORKER_RUNTIME_API_BASE_URL);
        if (runtimeApiBaseUrl == null) {
            HttpServerManager.initServer(this, DuckSdkHttpEvents.assemble(this, businessDefinitions));
            return;
        }

        worker = AndroidWorker.create(
                this,
                runtimeApiBaseUrl,
                DetectEnvWorkerConfiguration.workerGroupId(
                        BuildConfig.WORKER_GROUP_ID
                ),
                new DetectEnvDeviceProperties(this),
                businessDefinitions
        );
        HttpServerManager.initServer(
                this,
                DuckSdkHttpEvents.assemble(this, DetectEnvWorkerHostEvents.assemble(
                        businessDefinitions,
                        worker
                ))
        );
        worker.start();
    }

    static boolean isMainProcess(String packageName, String processName) {
        return packageName.equals(processName);
    }

    @Override
    public void onTerminate() {
        HttpServerManager.closeServer();
        if (worker != null) {
            worker.close();
        }
        super.onTerminate();
    }
}
