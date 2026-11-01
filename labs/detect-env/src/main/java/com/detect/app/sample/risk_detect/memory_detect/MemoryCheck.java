package com.detect.app.sample.risk_detect.memory_detect;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

public class MemoryCheck {

    private static final String TAG = "MemoryCheck";

    public static String getAllInfo(Context context) {

        Map<String, Object> result = new HashMap<>();

        result.put("checkStacktraceResult",checkStacktraceResult());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();


        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }





    public static Map<String, String> checkStacktraceResult(){
        Map<String, String> result = new HashMap<>();
        StackTraceElement[] elements=Thread.currentThread().getStackTrace();
        for(StackTraceElement element:elements){
            if(element.getClassName().contains("xposed")||element.getClassName().contains("EdHooker")){
                Log.i(TAG,"checkStacktrace :"+element.getClassName());
               result.put("xposed","found");
            }
        };
       return result;
    };



}


