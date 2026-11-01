package com.app.androidlab.sample.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SysCallUtils {

    static {
        System.loadLibrary("asm_module");
    }

    public static String fileCheckStr() {
        Map<String, String> resultMap = new HashMap<>();
        ArrayList<String> list = new ArrayList<>(Arrays.asList(
                "/sbin/su",
                "/system/bin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su",
                "/system/xbin/su"
        ));

        list.forEach(m -> {
            int result = -1;
            try {
                result = check_file_exist_syscall(m);
            } finally {
                if (result == 1) {
                    resultMap.put(m, "found");
                } else if (result == -1) {
                    resultMap.put(m, "check_failed");
                } else {
                    resultMap.put(m, "not_found");
                }
            }
        });

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(resultMap);
    }

    public static native int check_file_exist_syscall(String filePath);
}
