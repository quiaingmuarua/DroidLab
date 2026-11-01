package com.detect.app.sample.scenario_control;

import java.util.Arrays;
import java.util.List;

public final class DetectEnvGeneratedContracts {

    public static final int PORT = 5566;

    public static final String CAPABILITY_NETWORKING =
            "detect-env.networking";
    public static final String CAPABILITY_DEBUG_STATE =
            "detect-env.debug-state";
    public static final String CAPABILITY_DEVICE_PROPERTIES =
            "detect-env.device-properties";
    public static final String CAPABILITY_CLASS_LOADER_STATE =
            "detect-env.class-loader-state";
    public static final String CAPABILITY_SCENARIO_CATALOG =
            "detect-env.scenario-catalog";

    public static final String EVENT_NETWORKING =
            "extension.worker." + CAPABILITY_NETWORKING;
    public static final String EVENT_DEBUG_STATE =
            "extension.worker." + CAPABILITY_DEBUG_STATE;
    public static final String EVENT_DEVICE_PROPERTIES =
            "extension.worker." + CAPABILITY_DEVICE_PROPERTIES;
    public static final String EVENT_CLASS_LOADER_STATE =
            "extension.worker." + CAPABILITY_CLASS_LOADER_STATE;
    public static final String EVENT_SCENARIO_CATALOG =
            "extension.worker." + CAPABILITY_SCENARIO_CATALOG;

    private DetectEnvGeneratedContracts() {
    }

    public static List<DetectEnvScenarioModels.GeneratedScenarioDefinition> buildScenarioDefinitions() {
        return Arrays.asList(
                buildSingleCheckScenario(EVENT_NETWORKING),
                buildSingleCheckScenario(EVENT_DEBUG_STATE),
                buildSingleCheckScenario(EVENT_DEVICE_PROPERTIES),
                buildSingleCheckScenario(EVENT_CLASS_LOADER_STATE)
        );
    }

    private static DetectEnvScenarioModels.GeneratedScenarioDefinition buildSingleCheckScenario(String verifyEvent) {
        return new DetectEnvScenarioModels.GeneratedScenarioDefinition(
                verifyEvent,
                ".DetectEnvMainActivity"
        );
    }
}
