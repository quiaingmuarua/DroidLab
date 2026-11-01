package com.detect.app.sample.risk_detect.system_detect;



import static com.detect.app.sample.tool.FilterResult.checkResult;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class DevicePropertyCheck {

    private static final String TAG = "DevicePropertyCheck";


    public static String getAllInfo(Context context) {

        Map<String, Object> result = new HashMap<>();

        result.put("JavaRiskEnvDetectResult",detectSecurityRisk(context));
        result.put("detectSystemEnv",detectSystemEnv(context));
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }


    public static Map<String, String> detectSecurityRisk(Context context) {
        Map<String, String> result = new HashMap<>();
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get", String.class);

            checkResult(result, systemProperties, getMethod, "ro.debuggable", "Device is in debuggable mode",
                    value -> "1".equals(value) || "true".equals(value));

            checkResult(result, systemProperties, getMethod, "service.adb.root", "ADB root access is enabled",
                    value -> value != null && !value.isEmpty());

            checkResult(result, systemProperties, getMethod, "ro.secure", "Device is not in secure mode",
                    value -> !value.isEmpty() && !"1".equals(value) && !"true".equals(value));

            checkResult(result, systemProperties, getMethod, "ro.build.fingerprint", "Device is not a release version",
                    value -> value == null || !value.contains("release-keys"));

            checkResult(result, systemProperties, getMethod, "ro.build.tags", "Device is not a release version",
                    value -> !"release-keys".equals(value));

            checkResult(result, systemProperties, getMethod, "ro.kernel.qemu", "Device is running in an emulator",
                    value -> !value.isEmpty());

            checkResult(result, systemProperties, getMethod, "ro.boot.flash.locked", "Device locked bootloader",
                    value -> "0".equals(value));

            checkResult(result, systemProperties, getMethod, "ro.allow.mock.location", "Mock location is allowed",
                    value -> !("0".equals(value) || "false".equals(value)));

            checkResult(result, systemProperties, getMethod, "ro.build.selinux", "SELinux is not in enforcing mode",
                    value -> !value.isEmpty() && !"enforcing".equals(value));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }








    private static  Map<String, String> detectSystemEnv(Context context) {
        Map<String, String> result = new HashMap<>();
        for (String pathDir: System.getenv("PATH").split(":")) {
            if (new File(pathDir + "su").exists()) {
                result.put("su","found in  path");
            }
        }
        return result;
    }



}
