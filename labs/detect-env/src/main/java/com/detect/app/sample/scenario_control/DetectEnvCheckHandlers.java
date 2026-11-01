package com.detect.app.sample.scenario_control;

import android.content.Context;

import com.detect.app.sample.private_info_detect.NetworkCollect;
import com.detect.app.sample.risk_detect.system_detect.ClassLoaderStateCheck;
import com.detect.app.sample.risk_detect.system_detect.DebugStateCheck;
import com.detect.app.sample.risk_detect.system_detect.DevicePropertyCheck;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DetectEnvCheckHandlers {

    private DetectEnvCheckHandlers() {
    }

    public static DetectEnvScenarioModels.AppCheckResult buildNetworkingCheckResult(Context context) {
        Map<String, Object> raw = NetworkCollect.allMapInfo(context);
        return buildNetworkingVerifyResult(raw);
    }

    public static DetectEnvScenarioModels.AppCheckResult buildDebugStateCheckResult(Context context) {
        Map<String, Object> raw = parseJsonObjectMap(DebugStateCheck.getAllInfo(context));
        return buildDebugStateVerifyResult(raw);
    }

    public static DetectEnvScenarioModels.AppCheckResult buildDevicePropertyCheckResult(Context context) {
        Map<String, Object> raw = parseJsonObjectMap(DevicePropertyCheck.getAllInfo(context));
        return buildDevicePropertyVerifyResult(raw);
    }

    public static DetectEnvScenarioModels.AppCheckResult buildClassLoaderStateCheckResult(Context context) {
        Map<String, Object> raw = parseJsonObjectMap(ClassLoaderStateCheck.getAllInfo(context));
        return buildClassLoaderStateVerifyResult(raw);
    }

    static DetectEnvScenarioModels.AppCheckResult buildNetworkingVerifyResult(Map<String, Object> raw) {
        String verdict = "unknown";
        String reason = "No active network state was found.";

        Object networkInfoObj = raw.get("NetworkInfo");
        if (networkInfoObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> networkInfo = (Map<String, Object>) networkInfoObj;
            Object connectedObj = networkInfo.get("network_connected");
            if (connectedObj instanceof Boolean) {
                boolean connected = (Boolean) connectedObj;
                verdict = connected ? "pass" : "fail";
                reason = connected
                        ? "Active network connection is available."
                        : "Active network connection is not available.";
            }
        }

        return new DetectEnvScenarioModels.AppCheckResult(
                DetectEnvGeneratedContracts.EVENT_NETWORKING,
                "Collected network-related device information.",
                new DetectEnvScenarioModels.VerifyVerdict(verdict, reason),
                raw
        );
    }

    static DetectEnvScenarioModels.AppCheckResult buildDebugStateVerifyResult(Map<String, Object> raw) {
        String verdict = "unknown";
        String reason = "Developer-surface state could not be interpreted.";

        Object summaryObj = raw.get("summary");
        if (summaryObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) summaryObj;
            Object developerSurfaceDetectedObj = summary.get("developerSurfaceDetected");
            Object activeSignalCountObj = summary.get("activeSignalCount");
            if (developerSurfaceDetectedObj instanceof Boolean) {
                boolean developerSurfaceDetected = (Boolean) developerSurfaceDetectedObj;
                verdict = developerSurfaceDetected ? "fail" : "pass";
                reason = developerSurfaceDetected
                        ? "Developer-surface signals are visible."
                        : "No developer-surface signals are visible.";
                if (activeSignalCountObj != null) {
                    reason = reason + " activeSignalCount=" + activeSignalCountObj;
                }
            }
        }

        return new DetectEnvScenarioModels.AppCheckResult(
                DetectEnvGeneratedContracts.EVENT_DEBUG_STATE,
                "Collected debugger and developer-surface state.",
                new DetectEnvScenarioModels.VerifyVerdict(verdict, reason),
                raw
        );
    }

    static DetectEnvScenarioModels.AppCheckResult buildDevicePropertyVerifyResult(Map<String, Object> raw) {
        String verdict = "unknown";
        String reason = "Device-property risk state could not be interpreted.";

        int riskSignalCount = countMapEntries(raw.get("JavaRiskEnvDetectResult")) + countMapEntries(raw.get("detectSystemEnv"));
        verdict = riskSignalCount == 0 ? "pass" : "fail";
        reason = riskSignalCount == 0
                ? "No suspicious device-property signals are visible."
                : "Suspicious device-property signals are visible. signalCount=" + riskSignalCount;

        return new DetectEnvScenarioModels.AppCheckResult(
                DetectEnvGeneratedContracts.EVENT_DEVICE_PROPERTIES,
                "Collected suspicious device-property state.",
                new DetectEnvScenarioModels.VerifyVerdict(verdict, reason),
                raw
        );
    }

    static DetectEnvScenarioModels.AppCheckResult buildClassLoaderStateVerifyResult(Map<String, Object> raw) {
        String verdict = "unknown";
        String reason = "Class-loader state could not be interpreted.";

        Object summaryObj = raw.get("summary");
        if (summaryObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) summaryObj;
            Object appVsThreadSameInstanceObj = summary.get("appVsThreadSameInstance");
            Object appLoaderClassObj = summary.get("appLoaderClass");
            if (appVsThreadSameInstanceObj instanceof Boolean) {
                boolean appVsThreadSameInstance = (Boolean) appVsThreadSameInstanceObj;
                verdict = appVsThreadSameInstance ? "pass" : "fail";
                reason = appVsThreadSameInstance
                        ? "App and thread context class loaders match."
                        : "App and thread context class loaders diverge.";
                if (appLoaderClassObj != null) {
                    reason = reason + " appLoaderClass=" + appLoaderClassObj;
                }
            }
        }

        return new DetectEnvScenarioModels.AppCheckResult(
                DetectEnvGeneratedContracts.EVENT_CLASS_LOADER_STATE,
                "Collected app, thread, and system class-loader state.",
                new DetectEnvScenarioModels.VerifyVerdict(verdict, reason),
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

    private static int countMapEntries(Object value) {
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        return 0;
    }
}
