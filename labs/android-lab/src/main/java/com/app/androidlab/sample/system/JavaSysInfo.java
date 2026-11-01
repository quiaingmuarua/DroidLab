package com.app.androidlab.sample.system;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

public class JavaSysInfo {

    private static final String TAG = "JavaSysInfo";

    public static String getSettingSecureInfo(Context context) {
        Map<String, String> resultMap = new HashMap<>();

        try {
            //原始获取android id
            @SuppressLint("HardwareIds")
            String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
            String bluetoothAddress = Settings.Secure.getString(context.getContentResolver(), "bluetooth_address");

            resultMap.put("android_id", androidId);
            resultMap.put("bluetoothAddress", bluetoothAddress);
        } catch (Exception e) {
            Log.d(TAG, "getSettingSecureInfo" + e);
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultMap);
    }

    public static String getOsBuildInfo() {
        Map<String, String> deviceInfoMap = new HashMap<>();
        try {
            deviceInfoMap.put("DEVICE", Build.DEVICE);
            deviceInfoMap.put("MODEL", Build.MODEL);
            deviceInfoMap.put("BRAND", Build.BRAND);
            deviceInfoMap.put("PRODUCT", Build.PRODUCT);
            deviceInfoMap.put("HARDWARE", Build.HARDWARE);
            deviceInfoMap.put("MANUFACTURER", Build.MANUFACTURER);
            deviceInfoMap.put("RELEASE", Build.VERSION.RELEASE);
            deviceInfoMap.put("SDK_INT", String.valueOf(Build.VERSION.SDK_INT));
            deviceInfoMap.put("CODENAME", Build.VERSION.CODENAME);
            deviceInfoMap.put("ID", Build.ID);
            deviceInfoMap.put("DISPLAY", Build.DISPLAY);
            deviceInfoMap.put("TYPE", Build.TYPE);
            deviceInfoMap.put("Build.TIME", String.valueOf(Build.TIME));
            deviceInfoMap.put("FINGERPRINT", Build.FINGERPRINT);
            deviceInfoMap.put("BOARD", Build.BOARD);
            deviceInfoMap.put("BOOTLOADER", Build.BOOTLOADER);
            deviceInfoMap.put("RadioVersion", Build.getRadioVersion());
            deviceInfoMap.put("USER", Build.USER);
            deviceInfoMap.put("HOST", Build.HOST);
            deviceInfoMap.put("TAGS", Build.TAGS);
            deviceInfoMap.put("INCREMENTAL", Build.VERSION.INCREMENTAL);
            deviceInfoMap.put("CPU_ABI", Build.CPU_ABI);
            deviceInfoMap.put("CPU_ABI2", Build.CPU_ABI2);
            deviceInfoMap.put("SERIAL", Build.SERIAL);
            deviceInfoMap.put("TIME(UTC)", String.valueOf(Build.TIME / 1000));
        } catch (Exception e) {
            Log.d(TAG, "getOsBuildInfo" + e);
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(deviceInfoMap);
    }


    public static String getTelephonyInfo(Context context) {
        Map<String, String> telephonyInfoMap = new HashMap<>();

        try {
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {

                // 获取设备唯一标识符（IMEI 或 MEID）
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    telephonyInfoMap.put("IMEI/MEID", telephonyManager.getImei());
//                } else {
//                    telephonyInfoMap.put("IMEI/MEID", telephonyManager.getDeviceId());
//                }

                // 获取 SIM 卡序列号
//                telephonyInfoMap.put("SIM Serial Number", telephonyManager.getSimSerialNumber());

                // 获取注册的网络运营商的名称
                telephonyInfoMap.put("Network Operator", telephonyManager.getNetworkOperator());

                // 获取移动数据网络的类型
                telephonyInfoMap.put("Network Type", String.valueOf(telephonyManager.getNetworkType()));

                // 获取设备的电话类型
                telephonyInfoMap.put("Phone Type", String.valueOf(telephonyManager.getPhoneType()));

                // 判断设备是否正在漫游
                telephonyInfoMap.put("Roaming", String.valueOf(telephonyManager.isNetworkRoaming()));

                // 获取 SIM 卡所在国家/地区的运营商代码
                telephonyInfoMap.put("SIM Operator", telephonyManager.getSimOperator());

                // 获取 SIM 卡所在国家/地区的运营商名称
                telephonyInfoMap.put("SIM Operator Name", telephonyManager.getSimOperatorName());

                // 获取语音邮件号码
                telephonyInfoMap.put("Voice Mail Number", telephonyManager.getVoiceMailNumber());

                // 获取语音邮件标签
                telephonyInfoMap.put("Voice Mail Alpha Tag", telephonyManager.getVoiceMailAlphaTag());
            } else {
                telephonyInfoMap.put("Permission", "READ_PHONE_STATE permission not granted");

            }

        } catch (Exception e) {
            Log.d(TAG, "getTelephonyInfo " + e);
        }


        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(telephonyInfoMap);

    }




    public static String getSystemProperties() {

        Map<String, Object> result = new HashMap<>();
        try {
            result.put("ro.product.board", getSystemProperty("ro.product.board"));
            result.put("ro.board.platform", getSystemProperty("ro.board.platform"));
        } catch (Exception e) {
            Log.d(TAG, "getAllSystemProperties" + e);
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String resultString = gson.toJson(result);
        return resultString;
    }



    private static String getSystemProperty(String str) throws Exception {
        Class<?> cls = Class.forName("android.os.SystemProperties");
        return (String) cls.getMethod("get", String.class).invoke(cls, str);
    }

}
