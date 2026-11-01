package com.detect.app.sample.risk_detect.shell_detect;

import android.util.Log;

import com.detect.app.sample.tool.ShellHelper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ShellCheck {


    private static final String TAG = "ShellCheck";

    public static String getAllInfo() {
        Map<String, Object> result = new HashMap<>();

        result.put("cShellCheckNDKResult",cShellCheckNDKResult());
        result.put("javaShellCheckResult",javaShellCheckResult());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;

    }


    public static List<String> getDetectShellExistList(){
        // 使用 Arrays.asList() 方法将数组转换为 ArrayList
        return  new ArrayList<>(Arrays.asList(
                "su","which su"));
    }


    public static List<String> getDetectShellResultList(){
        // 使用 Arrays.asList() 方法将数组转换为 ArrayList
        return  new ArrayList<>(Arrays.asList(
                "uname -a","ps -ef"));
    }


    public static Map<String, String> javaShellCheckResult() {


        Map<String, String> resultMap = new HashMap<>();

        getDetectShellExistList().forEach(m -> {
            resultMap.put(m, ShellHelper.execResult(m));
        });


        getDetectShellResultList().forEach(m -> {
            resultMap.put(m, ShellHelper.execResult(m));
        });

        return resultMap;

    }
    private static Map<String,String> cShellCheckNDKResult(){
        Map<String,String> resultMap=new HashMap<>();
        getDetectShellResultList().forEach(m->{
            String result= ShellHelper.exec_shell_ndk_c(m);
            resultMap.put(m,result);
        });

        return resultMap;

    }





}
