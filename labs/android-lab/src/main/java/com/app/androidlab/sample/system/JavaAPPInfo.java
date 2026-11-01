package com.app.androidlab.sample.system;

import android.app.ActivityManager;
import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaAPPInfo {


    public static String processList(Context context){
        Map<String, String> resultMap = new HashMap<>();
        // 获取当前进程列表
        List<ActivityManager.RunningAppProcessInfo> processList = getRunningProcesses(context);
        // 打印进程信息
        for (ActivityManager.RunningAppProcessInfo processInfo : processList) {

            resultMap.put(String.valueOf(processInfo.pid),processInfo.processName);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultMap);
    }



    private static List<ActivityManager.RunningAppProcessInfo> getRunningProcesses(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getRunningAppProcesses();
    }
}





