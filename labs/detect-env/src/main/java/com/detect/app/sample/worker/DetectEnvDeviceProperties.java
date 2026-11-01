package com.detect.app.sample.worker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.xa.mass.worker.android.AndroidWorkerProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Live Android properties supplied during each Worker Prepare. */
public final class DetectEnvDeviceProperties implements AndroidWorkerProperties {

    private final Context applicationContext;

    public DetectEnvDeviceProperties(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        Context resolved = context.getApplicationContext();
        applicationContext = resolved == null ? context : resolved;
    }

    @Override
    public Map<String, String> getProperties(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("context is required");
        }
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("runtime", "android");
        properties.put("capability", "detect-env");
        properties.put("packageName", applicationContext.getPackageName());
        properties.put("versionName", versionName());
        properties.put("sdkInt", Integer.toString(Build.VERSION.SDK_INT));
        properties.put("manufacturer", Build.MANUFACTURER);
        properties.put("model", Build.MODEL);
        return Collections.unmodifiableMap(properties);
    }

    private String versionName() {
        try {
            PackageInfo info = applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }
}
