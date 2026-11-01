package com.detect.app.sample.fingerprint_detect.unique_fingerprint;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class SettingPropInfoCollect {

    private static final String TAG = "SettingPropInfoCollect";


    public static String getAllInfo(Context context) {
        Map<String, Object> result = allMapInfo(context);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }


    public static Map<String, Object> allMapInfo(Context context){

        Map<String, Object> result = new HashMap<>();
        result.put("OsBuildInfo", getAllOsBuildInfo());
        result.put("OsBuildVersionInfo", getAllOsBuildVersionInfo());
        result.put("SecureSettings", getAllSecureSettings(context));
        result.put("GlobalSettings", getAllGlobalSettings(context));
        result.put("SystemSettings", getAllSystemSettings(context));


        return result;

    }

    private static Map<String, Object> getAllOsBuildInfo() {
        Field[] fields = Build.class.getDeclaredFields();
        return getALLFieldInfo(fields);

    }
    private static Map<String, Object> getAllOsBuildVersionInfo() {
        Field[] fields = Build.VERSION.class.getDeclaredFields();
        return getALLFieldInfo(fields);

    }

    private static Map<String, String> getAllGlobalSettings(Context context) {
        Class<?> secureClass = Settings.Global.class;

        // 获取 Settings.Secure 中的所有字段
        Field[] fields = secureClass.getFields();

        return getCommonSettingInfo(context, fields);

    }


    private static Map<String, String> getAllSystemSettings(Context context) {
        Class<?> secureClass = Settings.System.class;
        // 获取 Settings.Secure 中的所有字段
        Field[] fields = secureClass.getFields();

        return getCommonSettingInfo(context, fields);

    }


    private static Map<String, String> getAllSecureSettings(Context context) {
        Class<?> secureClass = Settings.Secure.class;

        // 获取 Settings.Secure 中的所有字段
        Field[] fields = secureClass.getFields();

        return getCommonSettingInfo(context, fields);

    }

    private static Map<String, String> getCommonSettingInfo(Context context, Field[] fields) {
        Map<String, String> secureSettingsMap = new HashMap<>();

        for (Field field : fields) {
            try {
                Object filedValue = field.get(null); // 静态属性，传入 null 表示不依赖于具体的实例


                if (filedValue != null) {
                    String filedValueStr = filedValue.toString();
                    // 使用 Settings.Secure 类的 get 方法获取属性值
                    String value = Settings.Secure.getString(context.getContentResolver(), filedValueStr);
                    if (value != null) {
                        // 存储到 Map 中
                        secureSettingsMap.put(filedValueStr, value);
                    }

                }


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return secureSettingsMap;
    }


    private static Map<String, Object> getALLFieldInfo(Field[] fields) {

        Map<String, Object> buildInfoMap = new HashMap<>();
        for (Field field : fields) {
            try {
                // 设置可访问，因为有些属性可能是 private 的
                field.setAccessible(true);

                // 获取属性名和对应的值
                String fieldName = field.getName();
                Object value = field.get(null); // 静态属性，传入 null 表示不依赖于具体的实例
                if (value != null) {
                    // 存储到 Map 中
                    buildInfoMap.put(fieldName, value);
                }


            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return buildInfoMap;

    }




}
