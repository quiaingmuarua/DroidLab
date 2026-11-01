package com.app.androidlab.sample.scenario_control;

import java.util.Arrays;
import java.util.List;

public final class AndroidLabGeneratedContracts {

    public static final int PORT = 5567;

    public static final String CAPABILITY_SYSTEM_BUILD_INFO =
            "android-lab.system-build-info";
    public static final String CAPABILITY_SYSTEM_PROPERTIES =
            "android-lab.system-properties";
    public static final String CAPABILITY_SCENARIO_CATALOG =
            "android-lab.scenario-catalog";

    public static final String EVENT_SYSTEM_BUILD_INFO =
            "extension.worker." + CAPABILITY_SYSTEM_BUILD_INFO;
    public static final String EVENT_SYSTEM_PROPERTIES =
            "extension.worker." + CAPABILITY_SYSTEM_PROPERTIES;
    public static final String EVENT_SCENARIO_CATALOG =
            "extension.worker." + CAPABILITY_SCENARIO_CATALOG;

    private AndroidLabGeneratedContracts() {
    }

    public static List<AndroidLabScenarioModels.GeneratedScenarioDefinition> buildScenarioDefinitions() {
        return Arrays.asList(
                new AndroidLabScenarioModels.GeneratedScenarioDefinition(
                        EVENT_SYSTEM_BUILD_INFO,
                        ".AndroidLabMainActivity"
                ),
                new AndroidLabScenarioModels.GeneratedScenarioDefinition(
                        EVENT_SYSTEM_PROPERTIES,
                        ".AndroidLabMainActivity"
                )
        );
    }
}
