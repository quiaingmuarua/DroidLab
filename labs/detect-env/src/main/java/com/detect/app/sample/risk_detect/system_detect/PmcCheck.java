package com.detect.app.sample.risk_detect.system_detect;

import android.util.Log;

import com.detect.app.sample.tool.ShellHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PmcCheck {

    private static final String TAG = "PmcCheck";


    public static String getAllInfo() {
        Map<String, Object> result = new HashMap<>();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        result.put("javaShellQueryResult",javaShellQueryResult());
        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;

    }



    public static List<String> getDetectAppListResultList(){

        String [] paths={
                "com.topjohnwu.magisk",
                "io.github.vvb2060.magisk",
                "io.github.vvb2060.magisk.lite",
                "de.robv.android.xposed.installer",
                "org.meowcat.edxposed.manager",
                "org.lsposed.manager",
                "top.canyie.dreamland.manager",
                "me.weishu.exp",
                "com.tsng.hidemyapplist",
                "cn.geektang.privacyspace",
                "moe.shizuku.redirectstorage",
                "com.github.uiautomator"
        };

        return Arrays.asList(paths);
    }



    private static Map<String,Object> javaShellQueryResult() {
        Map<String, Object> result = new HashMap<>();

        List<String> detectAppList = new ArrayList<>();

        // 已知风险应用列表
        List<String> knownRiskApps = getDetectAppListResultList();

        // 执行 shell 命令获取已安装的应用包名
        String shellResult = ShellHelper.execResult("pm list packages");
        String[] packages = shellResult.split("\n");

        for (String packageInfo : packages) {
            // 提取包名
            String packageName = packageInfo.substring(packageInfo.indexOf(':') + 1);
            // 检查包名是否在已知风险应用列表中
            if (knownRiskApps.contains(packageName)) {
                detectAppList.add(packageName);
            }
        }

        result.put("detectAppList",detectAppList);
        result.put("app_total_cnt",packages.length);

        return result;
    }

}
