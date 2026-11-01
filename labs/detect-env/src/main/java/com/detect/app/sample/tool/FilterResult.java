package com.detect.app.sample.tool;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class FilterResult {

    public static void checkResult(Map<String, String> result, Class<?> systemProperties, Method getMethod, String propertyName, ResultCheckFunctional check) {

        checkResult(result,systemProperties,getMethod,propertyName,"found risk",check);
    }

    public static void checkResult(Map<String, String> result, Class<?> systemProperties, Method getMethod, String propertyName, String message, ResultCheckFunctional check) {
        try {
            String propertyValue = (String) getMethod.invoke(systemProperties, propertyName);
            if (check.isRisk(propertyValue)) {
                result.put(propertyName, message + " " + propertyValue);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
