package com.detect.app.sample.fingerprint_detect.unique_fingerprint;

import android.content.Context;
import android.icu.util.TimeZone;
import android.media.AudioManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SystemInfoCollect {

    private static final String TAG = "SystemInfoCollect";

    public static String getAllInfo(Context context) {
        Map<String, Object> result = allMapInfo(context);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }

    public static Map<String, Object> allMapInfo(Context context){
        Map<String, Object> result = new HashMap<>();
        result.put("VolumeInfo", getVolumeInfo(context));
        result.put("TimeZoneInfo", getTimeZoneInfo());
        result.put("LocaleInfo", getLocaleInfo());
        return result;

    }


    private static Map<String, String> getVolumeInfo(Context context) {
        Map<String, String> result = new HashMap<>();
        // 获取 AudioManager 实例
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        result.put("alarmVolume", String.valueOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM)));
        result.put("mediaVolume", String.valueOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)));
        result.put("ringVolume", String.valueOf(audioManager.getStreamVolume(AudioManager.STREAM_RING)));

        return result;
    }


    private static Map<String, Object> getLocaleInfo() {
        Map<String, Object> result = new HashMap<>();
        Locale currentLocale = Locale.getDefault();

        result.put("DisplayName", currentLocale.getDisplayName());
        result.put("Language", currentLocale.getLanguage());
        result.put("Country", currentLocale.getCountry());
        result.put("ISO3Language", currentLocale.getISO3Language());
        result.put("ISO3Country", currentLocale.getISO3Country());
        result.put("DisplayLanguage", currentLocale.getDisplayLanguage());

        return result;
    }

    private static Map<String, Object> getTimeZoneInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("displayName",TimeZone.getDefault().getDisplayName());
        result.put("availableIDs",TimeZone.getDefault().getID());
        return result;

    }

}
