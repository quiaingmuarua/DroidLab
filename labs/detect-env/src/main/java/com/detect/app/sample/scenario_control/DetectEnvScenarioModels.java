package com.detect.app.sample.scenario_control;

import java.util.List;
import java.util.Map;

/**
 * Serialization-only DTO container for the detect-env scenario control surface.
 *
 * <p>These models exist to shape app-local HTTP responses. Keeping them together reduces file
 * sprawl and makes the control-surface contract easier to scan from one place.
 */
public final class DetectEnvScenarioModels {

    private DetectEnvScenarioModels() {
    }

    public static final class VerifyVerdict {

        private final String status;
        private final String reason;

        public VerifyVerdict(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }

        public String getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }

    public static final class AppCheckResult {

        private final String check;
        private final String summary;
        private final VerifyVerdict verdict;
        private final Map<String, Object> raw;

        public AppCheckResult(String check,
                              String summary,
                              VerifyVerdict verdict,
                              Map<String, Object> raw) {
            this.check = check;
            this.summary = summary;
            this.verdict = verdict;
            this.raw = raw;
        }

        public String getCheck() {
            return check;
        }

        public String getSummary() {
            return summary;
        }

        public VerifyVerdict getVerdict() {
            return verdict;
        }

        public Map<String, Object> getRaw() {
            return raw;
        }
    }

    public static final class GeneratedScenarioDefinition {

        private final String verify_event;
        private final String prepare_activity;

        public GeneratedScenarioDefinition(String verifyEvent, String prepareActivity) {
            this.verify_event = verifyEvent;
            this.prepare_activity = prepareActivity;
        }

        public String getVerify_event() {
            return verify_event;
        }

        public String getPrepare_activity() {
            return prepare_activity;
        }
    }

    public static final class ScenarioCatalogResult {

        private final String check;
        private final String summary;
        private final List<GeneratedScenarioDefinition> scenarios;
        private final int count;

        public ScenarioCatalogResult(String check,
                                     String summary,
                                     List<GeneratedScenarioDefinition> scenarios) {
            this.check = check;
            this.summary = summary;
            this.scenarios = scenarios;
            this.count = scenarios == null ? 0 : scenarios.size();
        }

        public String getCheck() {
            return check;
        }

        public String getSummary() {
            return summary;
        }

        public List<GeneratedScenarioDefinition> getScenarios() {
            return scenarios;
        }

        public int getCount() {
            return count;
        }
    }
}
