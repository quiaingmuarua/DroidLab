package com.app.androidlab.sample.scenario_control;

import java.util.List;
import java.util.Map;

public final class AndroidLabScenarioModels {

    private AndroidLabScenarioModels() {
    }

    public static final class VerifyVerdict {
        public final String status;
        public final String reason;

        public VerifyVerdict(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }

    public static final class AppCheckResult {
        public final String check;
        public final String summary;
        public final VerifyVerdict verdict;
        public final Map<String, Object> raw;

        public AppCheckResult(String check,
                              String summary,
                              VerifyVerdict verdict,
                              Map<String, Object> raw) {
            this.check = check;
            this.summary = summary;
            this.verdict = verdict;
            this.raw = raw;
        }
    }

    public static final class GeneratedScenarioDefinition {
        public final String verify_event;
        public final String prepare_activity;

        public GeneratedScenarioDefinition(String verifyEvent, String prepareActivity) {
            this.verify_event = verifyEvent;
            this.prepare_activity = prepareActivity;
        }
    }

    public static final class ScenarioCatalogResult {
        public final String check;
        public final String summary;
        public final List<GeneratedScenarioDefinition> scenarios;
        public final int count;

        public ScenarioCatalogResult(String check,
                                     String summary,
                                     List<GeneratedScenarioDefinition> scenarios) {
            this.check = check;
            this.summary = summary;
            this.scenarios = scenarios;
            this.count = scenarios.size();
        }
    }
}
