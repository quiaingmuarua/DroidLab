package com.detect.app.sample.fingerprint_detect.unique_fingerprint;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppInfoCollect {

    private static final String TAG = "AppInfoCollect";

    public static String getAllInfo(Context context) {

        Map<String, Object> result = new HashMap<>();

        result.put("packageManagerInfo", packageManagerInfo(context));
        result.put("appPermissionInfo", appPermissionInfo(context));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);


    }

    public static  Map<String, Object> packageManagerInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        PackageManager packageManager = context.getPackageManager();
        String packageName = context.getPackageName(); // 替换为目标应用的包名
        try {
            // 获取应用信息
            ApplicationInfo appInfo = packageManager.getApplicationInfo(packageName, 0);
            PackageInfo packageInfo=packageManager.getPackageInfo(packageName, 0);
            result.put("appFile", appInfo.sourceDir);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                result.put("firstInstallTime",packageInfo.firstInstallTime);
                result.put("lastUpdateTime",packageInfo.lastUpdateTime);
                result.put("packageName",packageInfo.packageName);
                result.put("versionName",packageInfo.versionName);
            }


        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }


        return result;
    }



    public static Map<String, Object> appPermissionInfo(Context context) {
        Map<String, Object> permissionsInfo = new HashMap<>();
        PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();

        try {
            PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            String[] requestedPermissions = packageInfo.requestedPermissions;
            if (requestedPermissions != null) {
                List<String> grantedPermissions = new ArrayList<>();
                List<String> deniedPermissions = new ArrayList<>();

                for (String perm : requestedPermissions) {
                    if (pm.checkPermission(perm, packageName) == PackageManager.PERMISSION_GRANTED) {
                        grantedPermissions.add(perm);
                    } else {
                        deniedPermissions.add(perm);
                    }
                }

                permissionsInfo.put("granted_permissions", grantedPermissions);
                permissionsInfo.put("denied_permissions", deniedPermissions);
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return permissionsInfo;
    }


}
