package com.detect.app.sample.tool;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JsonCompare {

    public static Map<String, Object> flattenJson(String jsonStr) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
        Map<String, Object> map = gson.fromJson(jsonStr, type);
        return flattenMap(map, "", new HashMap<>());
    }

    private static Map<String, Object> flattenMap(Map<String, Object> map, String prefix, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof Map<?, ?>) {
                flattenMap((Map<String, Object>) entry.getValue(), key, result);
            } else {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    public static Map<String, Object[]> compareDifferences(String jsonStr1, String jsonStr2) {
        try {
            Map<String, Object> map1 = flattenJson(jsonStr1);
            Map<String, Object> map2 = flattenJson(jsonStr2);

            return compareDifferences(map1, map2);
        } catch (Exception e){

            return new HashMap<>();
        }


    }

    public static Map<String, Object[]> compareDifferences(Map<String, Object> map1, Map<String, Object> map2) {
        Map<String, Object[]> differences = new HashMap<>();

        Set<String> allKeys = new HashSet<>(map1.keySet());
        allKeys.addAll(map2.keySet());

        for (String key : allKeys) {
            Object val1 = map1.get(key);
            Object val2 = map2.get(key);

            if ((val1 == null && val2 != null) || (val1 != null && !val1.equals(val2))) {
                differences.put(key, new Object[]{val1, val2});
            }
        }

        return differences;
    }
}
