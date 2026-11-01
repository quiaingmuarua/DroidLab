package com.app.androidlab.sample.scenario_control;

import android.content.Context;

import com.app.androidlab.sample.system.JavaSysInfo;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AndroidLabCheckHandlers {

    private AndroidLabCheckHandlers() {
    }

    public static AndroidLabScenarioModels.AppCheckResult buildSystemBuildInfoCheckResult(Context context) {
        Map<String, Object> raw = parseJsonObjectMap(JavaSysInfo.getOsBuildInfo());
        String verdict = raw.isEmpty() ? "fail" : "pass";
        String reason = raw.isEmpty()
                ? "Build information could not be collected."
                : "Build information was collected successfully.";
        return new AndroidLabScenarioModels.AppCheckResult(
                AndroidLabGeneratedContracts.EVENT_SYSTEM_BUILD_INFO,
                "Collected android.os.Build information.",
                new AndroidLabScenarioModels.VerifyVerdict(verdict, reason),
                raw
        );
    }

    public static AndroidLabScenarioModels.AppCheckResult buildSystemPropertiesCheckResult(Context context) {
        Map<String, Object> raw = parseJsonObjectMap(JavaSysInfo.getSystemProperties());
        String verdict = raw.isEmpty() ? "fail" : "pass";
        String reason = raw.isEmpty()
                ? "System properties could not be collected."
                : "System properties were collected successfully.";
        return new AndroidLabScenarioModels.AppCheckResult(
                AndroidLabGeneratedContracts.EVENT_SYSTEM_PROPERTIES,
                "Collected selected android.os.SystemProperties values.",
                new AndroidLabScenarioModels.VerifyVerdict(verdict, reason),
                raw
        );
    }

    private static Map<String, Object> parseJsonObjectMap(String json) {
        try {
            java.lang.reflect.Type type = new TypeToken<LinkedHashMap<String, Object>>() {
            }.getType();
            Map<String, Object> parsed = new Gson().fromJson(json, type);
            return parsed == null ? Collections.emptyMap() : parsed;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }
}
