package com.detect.app.sample.risk_detect.system_detect;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Debug;
import android.provider.Settings;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DebugStateCheck {

    private static final String TAG = "DebugStateCheck";

    public static String getAllInfo(Context context) {
        Map<String, Object> debuggerState = collectDebuggerState();
        Map<String, Object> developerOptions = collectDeveloperOptions(context);
        Map<String, Object> buildFlags = collectBuildFlags(context);
        Map<String, Object> systemProperties = collectSystemProperties();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", buildSummary(debuggerState, developerOptions, buildFlags, systemProperties));
        result.put("interpretation", buildInterpretation(debuggerState, developerOptions, buildFlags, systemProperties));
        result.put("debuggerState", debuggerState);
        result.put("developerOptions", developerOptions);
        result.put("buildFlags", buildFlags);
        result.put("systemProperties", systemProperties);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String resultStr = gson.toJson(result);
        Log.d(TAG, resultStr);
        return resultStr;
    }

    private static Map<String, Object> buildSummary(
            Map<String, Object> debuggerState,
            Map<String, Object> developerOptions,
            Map<String, Object> buildFlags,
            Map<String, Object> systemProperties
    ) {
        List<String> activeSignals = new ArrayList<>();

        if (isTrue(debuggerState.get("isDebuggerConnected"))) {
            activeSignals.add("debuggerConnected");
        }
        if (isTrue(debuggerState.get("isWaitingForDebugger"))) {
            activeSignals.add("waitingForDebugger");
        }
        if (isEnabledValue(developerOptions.get("adbEnabled"))) {
            activeSignals.add("adbEnabled");
        }
        if (isEnabledValue(developerOptions.get("developmentSettingsEnabled"))) {
            activeSignals.add("developmentSettingsEnabled");
        }
        if (isEnabledValue(developerOptions.get("installNonMarketApps"))) {
            activeSignals.add("installNonMarketApps");
        }
        if (isTrue(buildFlags.get("appDebuggable"))) {
            activeSignals.add("appDebuggable");
        }
        if (hasValue(systemProperties.get("service.adb.root"))) {
            activeSignals.add("adbRootPropertyVisible");
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeSignalCount", activeSignals.size());
        summary.put("developerSurfaceDetected", !activeSignals.isEmpty());
        summary.put("signals", activeSignals);
        return summary;
    }

    private static Map<String, Object> buildInterpretation(
            Map<String, Object> debuggerState,
            Map<String, Object> developerOptions,
            Map<String, Object> buildFlags,
            Map<String, Object> systemProperties
    ) {
        Map<String, Object> interpretation = new LinkedHashMap<>();
        interpretation.put("isDebuggerConnected", isTrue(debuggerState.get("isDebuggerConnected"))
                ? "A Java debugger is currently attached."
                : "No Java debugger is currently attached.");
        interpretation.put("isWaitingForDebugger", isTrue(debuggerState.get("isWaitingForDebugger"))
                ? "The process is blocked waiting for a debugger."
                : "The process is not waiting for a debugger.");
        interpretation.put("adbEnabled", isEnabledValue(developerOptions.get("adbEnabled"))
                ? "ADB is enabled in developer options."
                : "ADB does not appear to be enabled.");
        interpretation.put("developmentSettingsEnabled", isEnabledValue(developerOptions.get("developmentSettingsEnabled"))
                ? "Developer options are enabled."
                : "Developer options do not appear to be enabled.");
        interpretation.put("installNonMarketApps", isEnabledValue(developerOptions.get("installNonMarketApps"))
                ? "Side-loading from non-market sources appears to be enabled."
                : "Side-loading from non-market sources does not appear to be enabled.");
        interpretation.put("appDebuggable", isTrue(buildFlags.get("appDebuggable"))
                ? "This app build is debuggable."
                : "This app build is not debuggable.");
        interpretation.put("buildType", "Build.TYPE=" + stringify(buildFlags.get("buildType")));
        interpretation.put("buildTags", "Build.TAGS=" + stringify(buildFlags.get("buildTags")));
        interpretation.put("ro.debuggable", hasValue(systemProperties.get("ro.debuggable"))
                ? "ro.debuggable is visible: " + stringify(systemProperties.get("ro.debuggable"))
                : "ro.debuggable is empty or not exposed.");
        interpretation.put("ro.secure", hasValue(systemProperties.get("ro.secure"))
                ? "ro.secure is visible: " + stringify(systemProperties.get("ro.secure"))
                : "ro.secure is empty or not exposed.");
        interpretation.put("service.adb.root", hasValue(systemProperties.get("service.adb.root"))
                ? "service.adb.root is visible: " + stringify(systemProperties.get("service.adb.root"))
                : "service.adb.root is empty or not exposed.");
        interpretation.put("ro.boot.vbmeta.device_state", hasValue(systemProperties.get("ro.boot.vbmeta.device_state"))
                ? "Verified boot device state: " + stringify(systemProperties.get("ro.boot.vbmeta.device_state"))
                : "Verified boot device state is empty or not exposed.");
        return interpretation;
    }

    private static Map<String, Object> collectDebuggerState() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isDebuggerConnected", Debug.isDebuggerConnected());
        result.put("isWaitingForDebugger", Debug.waitingForDebugger());
        return result;
    }

    private static Map<String, Object> collectDeveloperOptions(Context context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("adbEnabled", readGlobalSetting(context, Settings.Global.ADB_ENABLED));
        result.put("developmentSettingsEnabled", readGlobalSetting(context, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED));
        result.put("installNonMarketApps", readSecureSetting(context, Settings.Secure.INSTALL_NON_MARKET_APPS));
        return result;
    }

    private static Map<String, Object> collectBuildFlags(Context context) {
        Map<String, Object> result = new LinkedHashMap<>();
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        result.put("appDebuggable", (applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
        result.put("buildType", Build.TYPE);
        result.put("buildTags", Build.TAGS);
        result.put("buildUser", Build.USER);
        result.put("buildHost", Build.HOST);
        result.put("sdkInt", Build.VERSION.SDK_INT);
        return result;
    }

    private static Map<String, Object> collectSystemProperties() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ro.debuggable", readSystemProperty("ro.debuggable"));
        result.put("ro.secure", readSystemProperty("ro.secure"));
        result.put("service.adb.root", readSystemProperty("service.adb.root"));
        result.put("ro.boot.vbmeta.device_state", readSystemProperty("ro.boot.vbmeta.device_state"));
        return result;
    }

    private static Object readGlobalSetting(Context context, String key) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), key);
        } catch (Settings.SettingNotFoundException e) {
            return "not-found";
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static Object readSecureSetting(Context context, String key) {
        try {
            String value = Settings.Secure.getString(context.getContentResolver(), key);
            return value == null ? "null" : value;
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, "");
        } catch (Exception e) {
            return "error:" + e.getClass().getSimpleName();
        }
    }

    private static boolean isTrue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isEnabledValue(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return !text.isEmpty() && !"null".equalsIgnoreCase(text) && !text.startsWith("error:");
    }

    private static String stringify(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
