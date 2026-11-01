package com.detect.app.sample.fingerprint_detect.gather;

import android.content.Context;

import com.detect.app.sample.fingerprint_detect.unique_fingerprint.HardwareInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.SettingPropInfoCollect;
import com.detect.app.sample.fingerprint_detect.unique_fingerprint.SystemInfoCollect;
import com.detect.app.sample.private_info_detect.NetworkCollect;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

public class FingerprintAllInOne   {

    public static String getAllInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        result.put("hardwareInfo", getHardwareInfo(context));
        result.put("settingPropInfo", SettingPropInfoCollect.allMapInfo(context));
        result.put("systemInfo", SystemInfoCollect.allMapInfo(context));
        result.put("systemPropsInfo", new HashMap<String, Object>());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);

    }


    private static Map<String, Object> getHardwareInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        result.putAll(HardwareInfoCollect.allMapInfo(context));
        result.putAll(NetworkCollect.allMapInfo(context));
        return result;
    }


}
