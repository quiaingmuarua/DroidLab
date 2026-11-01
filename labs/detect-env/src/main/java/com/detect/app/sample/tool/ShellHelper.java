package com.detect.app.sample.tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;


public class ShellHelper {

    static {
        System.loadLibrary("detect_sample");
        System.loadLibrary("asm_module");

    }


    public static String execResult(String cmd) {
        StringBuilder result = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
                result.append("\n");
            }
            reader.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "not support";
        }


        return String.valueOf(result);
    }







    public static native String exec_shell_ndk_c(String cmd);
}
