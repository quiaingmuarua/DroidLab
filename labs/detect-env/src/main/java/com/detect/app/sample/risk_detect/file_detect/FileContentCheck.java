package com.detect.app.sample.risk_detect.file_detect;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileContentCheck {
    static {
        System.loadLibrary("detect_sample");
        System.loadLibrary("asm_module");

    }

    private static final String TAG = "ProcFileCheck";

    public static String getAllInfo() {

        Map<String, Object> result = new HashMap<>();

        result.put("javaFileContentCheckResult",javaFileContentCheckResult());
        result.put("cppFileContentCheckNDKResult",cppFileContentCheckNDKResult());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String resultStr = gson.toJson(result);

        Log.d(TAG, new GsonBuilder().create().toJson(result));

        return resultStr;
    }


    private static Map<String,String> javaFileContentCheckResult(){
        Map<String, String> resultMap = new HashMap<>();
        getDetectFileContentList().forEach(m->{
          resultMap.put(m,checkFileSensitiveWord(m,getSensitiveWords()))  ;

        });

        return resultMap;
    }

    private static Map<String,String> cppFileContentCheckNDKResult(){
        Map<String, String> resultMap = new HashMap<>();
        getDetectFileContentList().forEach(m->{
            try {
                resultMap.put(m,cppCheckNDKFileSensitiveWord(m,"linker64;edxp;magisk;zygisk;Hook;frida"))  ;

            }catch (Exception e){
                Log.d(TAG, String.valueOf(e));
            }

        });

        return resultMap;
    }


    private static List<String> getDetectFileContentList(){
        String [] paths={
//                "/proc/self/maps",
                "/proc/self/task/tid/status"
        };

        return Arrays.asList(paths);
    }
    private static List<String> getSensitiveWords(){

        String [] paths={
                "zygisk",
                "magisk",
                "lsp",
                "frida",
                "gmain",
                "linker64"
        };

        return Arrays.asList(paths);
    }


    private static String checkFileSensitiveWord(String path, List<String> sensitiveWords) {
        Set<String> foundWords = new HashSet<>(); // 使用集合避免重复的敏感词

        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                for (String word : sensitiveWords) {
                    if (line.toLowerCase().contains(word)) {
                        foundWords.add(word);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace(); // 处理文件读取错误
            return null;
        }

        return String.join(",", foundWords); // 将找到的敏感词用逗号连接成字符串
    }


    public static native String cppCheckNDKFileSensitiveWord(String filePath,String sensitiveWords);
}
