package com.app.androidlab.sample.playground;

import android.util.Log;

public class ModelDemo {
    public static String publicStaticStringField = "this is publicStaticStringField";
    public String publicStringField = "this is publicStringField";

    private static final String TAG = "ModelDemo";

    String field_str;


    int field_int;

    public static String getPrivateStaticStringField() {
        return privateStaticStringField;
    }

    private static String privateStaticStringField = "this is my privateStaticStringField";
    private String privateStringField = "this is my privateStringField";

    public ModelDemo() {
        Log.d("xiaojianbang", "this is ModelDemo()");
    }

    public ModelDemo(String str) {
        Log.d("xiaojianbang", "this is ModelDemo(String str)");
    }

    public ModelDemo(String str, int i) {
        Log.d(TAG, String.format("this is ModelDemo(String %s, int %d)", str, i));
        field_int = i;
        field_str = str;
    }

    public static String publicStaticFunc() {

        Log.d(TAG, "this is publicStaticFunc");
        return "this is publicStaticFunc ";
    }

    public String publicFunc() {
        Log.d(TAG, "this is publicFunc");
        return "this is publicFunc";
    }

    public static String privateStaticFuncWrapper() {
        Log.d(TAG, "this is privateStaticFunc");
        return  privateStaticFunc();
    }


    private static String privateStaticFunc() {
        Log.d(TAG, "this is privateStaticFunc");
        return "this is privateStaticFunc";
    }



    private String privateFunc() {

        Log.d(TAG, "this is privateFunc");
        return "this is privateFunc";
    }


    public  String privateFuncWrapper(){
        return  privateFunc();
    }

    public String getPrivateStringField() {
        return privateStringField;
    }
}

