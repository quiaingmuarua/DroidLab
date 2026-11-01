package com.detect.app.sample.fingerprint_detect.ndk;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SystemPropertyInfoCollect {

    static {
        System.loadLibrary("fingerprint_module");

    }

    private static final String TAG = "SystemPropertyInfoCollect";

    public static String getAllInfo() {

        Map<String, Object> result = new HashMap<>();
        result.put("java_SystemProperties", getAllSystemProperties());
        result.put("ndk_SystemProperties", getAllNdkSystemProperties());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);
    }

    public static Map<String, String> getAllNdkSystemProperties() {
        Map<String, String> result = new HashMap<>();
        List<String> target_list = new ArrayList<>(Arrays.asList(
                "ro.build.id",
                "ro.build.display.id",
                "ro.build.version.incremental",
                "ro.build.version.sdk",
                "ro.build.version.codename",
                "ro.build.version.all_codenames",
                "ro.build.version.release",
                "ro.build.version.security_patch",
                "ro.build.version.base_os",
                "ro.build.date",
                "ro.build.date.utc",
                "ro.build.type",
                "ro.build.user",
                "ro.build.host",
                "ro.build.tags",
                "ro.build.flavor",
                "ro.product.model",
                "ro.product.brand",
                "ro.product.name",
                "ro.product.device",
                "ro.product.board",
                "ro.product.cpu.abi",
                "ro.product.cpu.abi2",
                "ro.product.cpu.abilist",
                "ro.product.cpu.abilist32",
                "ro.product.cpu.abilist64",
                "ro.product.manufacturer",
                "ro.product.locale.language",
                "ro.product.locale.region",
                "ro.wifi.channels",
                "ro.board.platform"));

        target_list.forEach(m -> {
            try {
                result.put(m, ndk_device_info(m));
            } catch (Exception e) {
                Log.d(TAG, "getAllNdkSystemProperties: " + e);

            }

        });


        return result;

    }


    private static Map<String, String> getAllSystemProperties() {
        Map<String, String> result = new HashMap<>();
        try {
            // 获取 SystemProperties 类
           Class<?> systemPropertiesClass = Class.forName("android.os.SystemProperties");


            // 获取 get 方法，该方法用于获取系统属性的值
            Method getMethod = systemPropertiesClass.getMethod("get", String.class);


            List<String> target_list = new ArrayList<>(Arrays.asList(
                    "ro.build.id",
                    "ro.build.display.id",
                    "ro.build.version.incremental",
                    "ro.build.version.sdk",
                    "ro.build.version.codename",
                    "ro.build.version.all_codenames",
                    "ro.build.version.release",
                    "ro.build.version.security_patch",
                    "ro.build.version.base_os",
                    "ro.build.date",
                    "ro.build.date.utc",
                    "ro.build.type",
                    "ro.build.user",
                    "ro.build.host",
                    "ro.build.tags",
                    "ro.build.flavor",
                    "ro.product.model",
                    "ro.product.brand",
                    "ro.product.name",
                    "ro.product.device",
                    "ro.product.board",
                    "ro.product.cpu.abi",
                    "ro.product.cpu.abi2",
                    "ro.product.cpu.abilist",
                    "ro.product.cpu.abilist32",
                    "ro.product.cpu.abilist64",
                    "ro.product.manufacturer",
                    "ro.product.locale.language",
                    "ro.product.locale.region",
                    "ro.wifi.channels",
                    "ro.board.platform"));
            target_list.forEach(m -> {
                try {
                    result.put(m, (String) getMethod.invoke(null,m));
                } catch (Exception e) {
                    Log.d(TAG, "getAllNdkSystemProperties: " + e);

                }

            });



        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }


    public static native String ndk_device_info(String property);


}
