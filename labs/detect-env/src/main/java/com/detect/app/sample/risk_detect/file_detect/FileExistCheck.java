package com.detect.app.sample.risk_detect.file_detect;

import android.util.Log;

import com.detect.app.sample.tool.FileHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileExistCheck {


    private static final String TAG = "FileExistCheck";

    private static List<String> getDetectFileExistList(){
        String [] paths={
                "/system/app/Superuser.apk",
                "/sbin/su", "/sbin/magisk32",
                "/system/bin/su", "/system/xbin/su",
                "/data/local/xbin/su", "/data/local/bin/su",
                "/system/sd/xbin/su","/sbin/magisk64",
                "/system/bin/failsafe/su", "/data/local/su",
                "/su/bin/su","/data/adb/magisk",
                "/sbin/magisk","/sbin/magiskinit",
                "/sbin/magiskpolicy","/sbin/riru.prop","/bin/magisk","/bin/magiskpolicy",
                "/sbin/supolicy",
                "system/lib/libriru_edxp.so.s","system/lib/libriru_edxp.so",
                "system/lib/libsandhook.edxp.so","system/lib64/libriru_edxp.so",
                "system/lib/libsandhook.edxp.so.s","system/lib64/libriru_edxp.so",
                "system/lib64/libsandhook.edxp.so.s","lib/armeabi-v7a/libriru.so",
                "lib/armeabi-v7a/libriruhide.so","lib/armeabi-v7a/libriruloader.so",
                "lib/arm64-v8a/libriru.so","lib/arm64-v8a/libriruhide.so",
                "lib/arm64-v8a/libriruloader.so","lib/arm64-v8a/librirud.so",
                "/data/adb/modules/riru-core/allow_install_app",
                "/data/misc/riru/api_version","/data/misc/riru/version_code",
                "/data/misc/riru/version_name",
        };

        return Arrays.asList(paths);

    }

    public static List<String> getDetectFileResultList(){
        // 使用 Arrays.asList() 方法将数组转换为 ArrayList
        return  new ArrayList<>(Arrays.asList(
        ));
    }



    public static String getAllInfo(){
        Map<String, Object> result = new HashMap<>();

        result.put("javaFileCheckResult",javaFileCheckResult());
        result.put("cFileCheckNDKResult",cFileCheckNDKResult());
        result.put("svcFileCheckNDkResult",svcFileCheckNDkResult());



        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;

    }

    private static Map<String, String> javaFileCheckResult() {
        Map<String, String> resultMap = new HashMap<>();
        getDetectFileExistList().forEach(m -> {
            if (FileHelper.fileExistResult(m)) {
                resultMap.put(m, "found");
            }

        });

        return resultMap;
    }

    private static Map<String,String> cFileCheckNDKResult(){
        Map<String,String> resultMap=new HashMap<>();
        getDetectFileExistList().forEach(m->{
            int result= FileHelper.check_file_exist_c(m);
            if (result ==1){
                resultMap.put(m,"ndk found");
            }
        });

        return resultMap;

    }

    private static Map<String,String> svcFileCheckNDkResult(){
        Map<String,String> resultMap=new HashMap<>();
        getDetectFileExistList().forEach(m->{
            int result= FileHelper.check_file_exist_svc(m);
            if (result ==1){
                resultMap.put(m,"svc found");
            }
        });

        return resultMap;
    }





}
