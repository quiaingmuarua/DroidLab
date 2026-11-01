package com.detect.app.sample.tool;

import android.util.Log;

import java.io.File;

public class FileHelper {


    static {
        System.loadLibrary("detect_sample");
        System.loadLibrary("asm_module");

    }

    private static final String TAG = "FileHelper";


    public static boolean fileExistResult(String path) {
        boolean flag = new File(path).exists();
        if (flag) Log.i(TAG, "Detected Root!!!(Java) :" + path);

        return flag;
    }



    public static native int check_file_exist_c(String filePath);


    public  static native  int check_file_exist_svc(String filePath);
}
