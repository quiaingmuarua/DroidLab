package com.detect.app.sample.fingerprint_detect.third_party_detect;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.snail.antifake.deviceid.AndroidDeviceIMEIUtil;
import com.snail.antifake.jni.EmulatorDetectUtil;

import java.util.HashMap;
import java.util.Map;

public class AntiFakerAndroidChecker {
    private static final String TAG = "AntiFakerAndroidChecker";


    public static String getAllInfo(Context context) {
        Map<String, Object> result = new HashMap<>();

        try {
            //模拟器检测激进做法
            result.put("isEmulatorFromAll_active", EmulatorDetectUtil.isEmulatorFromAll(context));
            //模拟器保守做法
            result.put("isRunOnEmulator_protect", AndroidDeviceIMEIUtil.isRunOnEmulator(context));

            result.put("AndroidId", AndroidDeviceIMEIUtil.getAndroidId(context));
            result.put("DeviceId", AndroidDeviceIMEIUtil.getDeviceId(context));
            result.put("IMSI", AndroidDeviceIMEIUtil.getIMSI(context));
            result.put("board", AndroidDeviceIMEIUtil.getBoard());
        } catch (Exception e) {
            Log.d(TAG, "getAllInfo:  Exception" + e);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);
    }
}
