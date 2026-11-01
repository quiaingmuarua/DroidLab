package com.detect.app.sample.fingerprint_detect.unique_fingerprint;

import static android.content.Context.SENSOR_SERVICE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.core.app.ActivityCompat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HardwareInfoCollect {

    private static final String TAG = "HardwareInfoCollect";


    public static String getAllInfo(Context context) {
        Map<String, Object> result = allMapInfo(context);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }

    public  static Map<String, Object> allMapInfo(Context context){


        Map<String, Object> result = new HashMap<>();

        result.put("SensorInfo", HardwareInfoCollect.getSensorInfo(context));
        result.put("CameraInfo", HardwareInfoCollect.getCameraInfo(context));
        result.put("DisplayInfo", HardwareInfoCollect.getDisplayInfo(context));

        result.put("MemoryInfo", HardwareInfoCollect.getMemoryInfo(context));
        result.put("BatteryInfo", HardwareInfoCollect.getBatteryInfo(context));
        result.put("getSimInfo",HardwareInfoCollect.getSimInfo(context));

        return result;
    }


    /*
    传感器 sensor 检测
     */
    public static ArrayList<Map<String, String>> getSensorInfo(Context context) {
        ArrayList<Map<String, String>> result = new ArrayList<>();
        SensorManager sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        List<Sensor> deviceSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        deviceSensors.forEach(m -> {
            Map<String, String> item = new HashMap<>();
            item.put("name", m.getName());
            item.put("vendor", m.getVendor());
            item.put("type", m.getStringType());
            item.put("power", String.valueOf(m.getPower()));
            item.put("resolution", String.valueOf(m.getResolution()));
            item.put("minDelay", String.valueOf(m.getMinDelay()));
            item.put("maximum", String.valueOf(m.getMaximumRange()));
            item.put("version", String.valueOf(m.getVersion()));
            result.add(item);

        });
        return result;

    }


    /*
         相机信息检测
       */
    public static ArrayList<Map<String, String>> getCameraInfo(Context context) {
        ArrayList<Map<String, String>> result = new ArrayList<>();
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Map<String, String> item = new HashMap<>();
                item.put("Camera ID", cameraId);
                item.put("Lens Facing", characteristics.get(CameraCharacteristics.LENS_FACING).toString());
                item.put("Hardware Level", characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL).toString());
                result.add(item);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return result;
    }


    @SuppressLint("DefaultLocale")
    public static Map<String, String> getDisplayInfo(Context context) {
        Map<String, String> result = new HashMap<>();

        // 获取DisplayMetrics实例
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();

        // 屏幕宽度和高度（像素）
        result.put("width", String.valueOf(metrics.widthPixels));
        result.put("height", String.valueOf(metrics.heightPixels));
        result.put("xdpi", String.valueOf(metrics.xdpi));
        result.put("ydpi", String.valueOf(metrics.ydpi));

        // 屏幕密度（PPI）
        result.put("density", String.valueOf(metrics.densityDpi));

        // 获取系统屏幕亮度设置
        try {
            int brightness = Settings.System.getInt(
                    context.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            result.put("brightness", String.valueOf(brightness));
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
            result.put("brightness", "Not Found");
        }

        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);

        // 计算屏幕的实际尺寸（英寸）
        double x = Math.pow(metrics.widthPixels / metrics.xdpi, 2);
        double y = Math.pow(metrics.heightPixels / metrics.ydpi, 2);
        double screenInches = Math.sqrt(x + y);

        result.put("Screen Size (inches)", String.format("%.2f", screenInches));
        result.put("Resolution", metrics.widthPixels + "x" + metrics.heightPixels);


        return result;
    }




    public static Map<String, String> getMemoryInfo(Context context) {
        Map<String, String> result = new HashMap<>();

        // 获取内存信息
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(mi);
        result.put("Total Memory", String.valueOf(mi.totalMem / 1048576L));
        result.put("Available Memory", String.valueOf(mi.availMem / 1048576L));

        // 获取存储空间信息
        StatFs internalStatFs = new StatFs(Environment.getDataDirectory().getPath());
        long internalTotal = internalStatFs.getBlockCountLong() * internalStatFs.getBlockSizeLong();
        long internalFree = internalStatFs.getAvailableBlocksLong() * internalStatFs.getBlockSizeLong();
        result.put("Internal Storage Total", String.valueOf(internalTotal / 1048576L));
        result.put("Internal Storage Free", String.valueOf(internalFree / 1048576L));

        // 检查外部存储是否可用 MB
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            StatFs externalStatFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
            long externalTotal = externalStatFs.getBlockCountLong() * externalStatFs.getBlockSizeLong();
            long externalFree = externalStatFs.getAvailableBlocksLong() * externalStatFs.getBlockSizeLong();
            result.put("External_Storage_Total", String.valueOf(externalTotal / 1048576L));
            result.put("External Storage Free", String.valueOf(externalFree / 1048576L));
        } else {
            result.put("External Storage Total", "Not available");
            result.put("External Storage Free", "Not available");
        }

        return result;
    }


    public static Map<String, String> getBatteryInfo(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int status = 0;
        if (batteryStatus != null) {
            status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        }
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : 0;
        int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : 0;

        float batteryPct = level * 100 / (float)scale;

        Map<String, String> result = new HashMap<>();
        result.put("Is Charging", String.valueOf(isCharging));
        result.put("Battery Percentage", String.valueOf(batteryPct) + "%");

        return result;
    }


    public static JSONObject getSimInfo(Context context) {
        Map<String, String> result = new HashMap<>();
        TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null) {
            // 获取 ICC ID (SIM 卡序列号)
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    @SuppressLint("MissingPermission")
                    String iccId = telephonyManager.getSimSerialNumber();
                    result.put("iccId", iccId);
                } else {
                    result.put("iccId", "Permission Denied");
                }
            } catch (SecurityException e) {
                result.put("iccId", "Permission Denied");
            }

            // 获取电话号码
            try {
                String phoneNumber = telephonyManager.getLine1Number();
                result.put("phoneNumber", phoneNumber);
            } catch (SecurityException e) {
                result.put("phoneNumber", "Permission Denied");
            }

            // 获取 Carrier ID (可能需要特定 API 级别)
            try {
                int carrierId = telephonyManager.getSimCarrierId();
                result.put("carrierId", String.valueOf(carrierId));
            } catch (SecurityException | UnsupportedOperationException e) {
                result.put("carrierId", "");
            }

            // 获取 SIM 卡运营商代码
            String simOperator = telephonyManager.getSimOperator();
            result.put("simOperator", simOperator);

            // 获取订阅者 ID
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    @SuppressLint("MissingPermission")
                    String subscriberId = telephonyManager.getSubscriberId();
                    result.put("subscriberId", subscriberId);
                } else {
                    result.put("subscriberId", "");
                }
            } catch (SecurityException e) {
                result.put("subscriberId", "");
            }

            // 获取 SIM 卡运营商名称
            String simOperatorName = telephonyManager.getSimOperatorName();
            result.put("simOperatorName", simOperatorName);

            // 获取 SIM 卡的国家 ISO
            String simCountryIso = telephonyManager.getSimCountryIso();
            result.put("simCountryIso", simCountryIso);

            // 获取 MCC 和 MNC
            if (simOperator != null && simOperator.length() >= 3) {
                String mcc = simOperator.substring(0, 3);
                String mnc = simOperator.substring(3);
                result.put("mcc", mcc);
                result.put("mnc", mnc);
            }
        }

        // 将结果转换为 JSON 对象
        return new JSONObject(result);
    }

    private static String getSimCountryIsoCode(String simCountryIso) {
        // 使用 Locale 获取国家代码
        Locale locale = new Locale("", simCountryIso.toUpperCase());
        return locale.getCountry();
    }
}
