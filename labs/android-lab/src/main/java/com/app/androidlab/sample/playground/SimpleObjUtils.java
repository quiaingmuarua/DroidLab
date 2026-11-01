package com.app.androidlab.sample.playground;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.Map;

public class SimpleObjUtils {

    static {
        System.loadLibrary("playground_sample");
    }

    public static String getJavaObjStr() {
        Map<String, String> resultMap = new HashMap<>();
        ModelDemo javaModelDemo = new ModelDemo();
        resultMap.put("classPrivateStaticField", ModelDemo.getPrivateStaticStringField());
        resultMap.put("classPublicStaticField", ModelDemo.publicStaticFunc());
        resultMap.put("classPrivateStaticMethod", ModelDemo.privateStaticFuncWrapper());
        resultMap.put("instancePublicField", javaModelDemo.publicStringField);
        resultMap.put("instancePrivateField", javaModelDemo.getPrivateStringField());
        resultMap.put("instancePublicMethod", javaModelDemo.publicFunc());
        resultMap.put("instancePrivateMethod", javaModelDemo.privateFuncWrapper());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultMap);
    }

    public static String getNdkObjStr() {
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("stringFromJNICpp", stringFromJNICpp());
        resultMap.put("stringFromDynamic", stringFromDynamic());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultMap);
    }

    public static native String stringFromJNICpp();

    public static native String stringFromDynamic();
}
