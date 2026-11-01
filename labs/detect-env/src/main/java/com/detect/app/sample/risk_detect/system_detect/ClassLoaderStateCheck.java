package com.detect.app.sample.risk_detect.system_detect;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassLoaderStateCheck {

    private static final String TAG = "ClassLoaderStateCheck";

    public static String getAllInfo(Context context) {
        ClassLoader appLoader = context.getClassLoader();
        ClassLoader threadLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

        Map<String, Object> appChain = collectClassLoaderChain(appLoader);
        Map<String, Object> threadChain = collectClassLoaderChain(threadLoader);
        Map<String, Object> systemChain = collectClassLoaderChain(systemLoader);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", buildSummary(appChain, threadChain, systemChain));
        result.put("interpretation", buildInterpretation(appLoader, threadLoader, systemLoader));
        result.put("runtime", collectRuntimeState(context, appLoader, threadLoader, systemLoader));
        result.put("appClassLoaderChain", appChain);
        result.put("threadContextClassLoaderChain", threadChain);
        result.put("systemClassLoaderChain", systemChain);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String resultStr = gson.toJson(result);
        Log.d(TAG, resultStr);
        return resultStr;
    }

    private static Map<String, Object> buildSummary(
            Map<String, Object> appChain,
            Map<String, Object> threadChain,
            Map<String, Object> systemChain
    ) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("appLoaderClass", appChain.get("rootClassName"));
        summary.put("threadLoaderClass", threadChain.get("rootClassName"));
        summary.put("systemLoaderClass", systemChain.get("rootClassName"));
        summary.put("appVsThreadSameInstance", isSameIdentity(appChain.get("rootIdentity"), threadChain.get("rootIdentity")));
        summary.put("threadVsSystemSameInstance", isSameIdentity(threadChain.get("rootIdentity"), systemChain.get("rootIdentity")));
        summary.put("appChainDepth", appChain.get("depth"));
        summary.put("threadChainDepth", threadChain.get("depth"));
        summary.put("systemChainDepth", systemChain.get("depth"));
        return summary;
    }

    private static Map<String, Object> buildInterpretation(
            ClassLoader appLoader,
            ClassLoader threadLoader,
            ClassLoader systemLoader
    ) {
        Map<String, Object> interpretation = new LinkedHashMap<>();
        interpretation.put(
                "appLoader",
                appLoader == null
                        ? "App class loader is null, which is unusual for a normal app process."
                        : "App class loader root is " + appLoader.getClass().getName() + "."
        );
        interpretation.put(
                "threadContextLoader",
                threadLoader == null
                        ? "Thread context class loader is null."
                        : "Thread context class loader root is " + threadLoader.getClass().getName() + "."
        );
        interpretation.put(
                "systemLoader",
                systemLoader == null
                        ? "System class loader is null."
                        : "System class loader root is " + systemLoader.getClass().getName() + "."
        );
        interpretation.put(
                "appVsThreadRelation",
                appLoader == threadLoader
                        ? "App loader and thread context loader are the same instance."
                        : "App loader and thread context loader are different instances."
        );
        interpretation.put(
                "hookUsefulness",
                "Class loader identity, parent chain, and dex path contents are useful hook and anti-hook observation points."
        );
        return interpretation;
    }

    private static Map<String, Object> collectRuntimeState(
            Context context,
            ClassLoader appLoader,
            ClassLoader threadLoader,
            ClassLoader systemLoader
    ) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("packageName", context.getPackageName());
        runtime.put("processThreadName", Thread.currentThread().getName());
        runtime.put("sdkInt", Build.VERSION.SDK_INT);
        runtime.put("appLoaderEqualsThreadLoader", appLoader == threadLoader);
        runtime.put("threadLoaderEqualsSystemLoader", threadLoader == systemLoader);
        runtime.put("javaClassPath", System.getProperty("java.class.path"));
        return runtime;
    }

    private static Map<String, Object> collectClassLoaderChain(ClassLoader root) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> chain = new ArrayList<>();

        ClassLoader current = root;
        int depth = 0;
        while (current != null && depth < 12) {
            chain.add(describeClassLoader(current, depth));
            current = current.getParent();
            depth++;
        }

        result.put("rootClassName", root == null ? "null" : root.getClass().getName());
        result.put("rootIdentity", root == null ? "null" : identity(root));
        result.put("depth", chain.size());
        result.put("chain", chain);
        result.put("reachesBootstrap", current == null);
        return result;
    }

    private static Map<String, Object> describeClassLoader(ClassLoader loader, int depth) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("depth", depth);
        item.put("className", loader.getClass().getName());
        item.put("identity", identity(loader));
        item.put("toString", String.valueOf(loader));
        item.put("dexPathList", readDexPathList(loader));
        item.put("dexElementCount", countDexElements(loader));
        return item;
    }

    private static Object readDexPathList(ClassLoader loader) {
        try {
            Field pathListField = findField(loader.getClass(), "pathList");
            if (pathListField == null) {
                return "not-available";
            }
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(loader);
            return pathList == null ? "null" : String.valueOf(pathList);
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static Object countDexElements(ClassLoader loader) {
        try {
            Field pathListField = findField(loader.getClass(), "pathList");
            if (pathListField == null) {
                return "not-available";
            }
            pathListField.setAccessible(true);
            Object pathList = pathListField.get(loader);
            if (pathList == null) {
                return "null";
            }
            Field dexElementsField = findField(pathList.getClass(), "dexElements");
            if (dexElementsField == null) {
                return "not-available";
            }
            dexElementsField.setAccessible(true);
            Object[] dexElements = (Object[]) dexElementsField.get(pathList);
            return dexElements == null ? 0 : dexElements.length;
        } catch (Throwable t) {
            return "error:" + t.getClass().getSimpleName();
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String identity(Object value) {
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
    }

    private static boolean isSameIdentity(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }
}
