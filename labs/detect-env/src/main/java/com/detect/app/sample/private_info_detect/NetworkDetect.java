package com.detect.app.sample.private_info_detect;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;


public class NetworkDetect {


    public static String getAllInfo(Context context) {
        Map<String, Object> result = new HashMap<>();
        result.put("System.getProperty",proxyInfo(context));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(result);


    }


    public static  Map<String, String> proxyInfo(Context context) {
        Map<String, String> result = new HashMap<>();
        String proxyAddress = System.getProperty("http.proxyHost");    //获取代理主机
        String portStr = System.getProperty("http.proxyPort");  //获取代理端口
        result.put("http.proxyHost",proxyAddress);
        result.put("http.proxyPort",portStr);
        return result;

    }


}
