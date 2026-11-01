package com.app.androidlab.sample.network.unified;

import android.content.Context;
import android.util.Log;

/**
 * HTTP 客户端工厂实现类
 * 支持 AUTO/CRONET/OKHTTP 引擎选择
 */
public class HttpClientFactory {
    private static final String TAG = "HttpClientFactory";
    
    private final Context context;
    
    public HttpClientFactory(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * 创建 HTTP 客户端
     * 
     * @param engineType 引擎类型
     * @return HTTP 客户端实例
     */
    public HttpClient createClient(HttpEngineType engineType) {
        switch (engineType) {
            case AUTO:
                return createAutoClient();
            case CRONET:
                return createCronetClient();
            case OKHTTP:
                return createOkHttpClient();
            default:
                Log.w(TAG, "Unknown engine type: " + engineType + ", using AUTO");
                return createAutoClient();
        }
    }
    
    /**
     * 自动选择引擎（优先 Cronet，降级到 OkHttp）
     */
    private HttpClient createAutoClient() {
        // 尝试创建 Cronet 客户端
        try {
            // 检查 Cronet 是否可用
            Class.forName("org.chromium.net.CronetEngine");
            Class.forName("com.google.android.gms.net.CronetProviderInstaller");
            
            Log.d(TAG, "Cronet is available, using CronetHttpClient");
            return new CronetHttpClient(context);
        } catch (Exception e) {
            Log.w(TAG, "Cronet not available, falling back to OkHttp: " + e.getMessage());
        }
        
        // 降级到 OkHttp
        try {
            return createOkHttpClient();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create OkHttp client", e);
            throw new RuntimeException("Unable to create any HTTP client", e);
        }
    }
    
    /**
     * 强制创建 Cronet 客户端
     */
    private HttpClient createCronetClient() {
        try {
            // 检查依赖是否存在
            Class.forName("org.chromium.net.CronetEngine");
            Class.forName("com.google.android.gms.net.CronetProviderInstaller");
            
            Log.d(TAG, "Creating CronetHttpClient");
            return new CronetHttpClient(context);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cronet dependencies not found. " +
                "Please add Cronet library to your dependencies:\n" +
                "implementation 'org.chromium.net:cronet-api:xxx'\n" +
                "implementation 'com.google.android.gms:play-services-cronet:xxx'", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Cronet client", e);
        }
    }
    
    /**
     * 强制创建 OkHttp 客户端
     */
    private HttpClient createOkHttpClient() {
        try {
            // 检查依赖是否存在
            Class.forName("okhttp3.OkHttpClient");
            
            Log.d(TAG, "Creating OkHttpHttpClient");
            return new OkHttpHttpClient();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("OkHttp dependencies not found. " +
                "Please add OkHttp library to your dependencies:\n" +
                "implementation 'com.squareup.okhttp3:okhttp:xxx'", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create OkHttp client", e);
        }
    }
    
    /**
     * 检查指定引擎是否可用
     * 
     * @param engineType 引擎类型
     * @return 如果可用返回 true
     */
    public boolean isEngineAvailable(HttpEngineType engineType) {
        try {
            switch (engineType) {
                case CRONET:
                    Class.forName("org.chromium.net.CronetEngine");
                    Class.forName("com.google.android.gms.net.CronetProviderInstaller");
                    return true;
                case OKHTTP:
                    Class.forName("okhttp3.OkHttpClient");
                    return true;
                case AUTO:
                    return isEngineAvailable(HttpEngineType.CRONET) || 
                           isEngineAvailable(HttpEngineType.OKHTTP);
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取推荐的引擎类型
     */
    public HttpEngineType getRecommendedEngine() {
        if (isEngineAvailable(HttpEngineType.CRONET)) {
            return HttpEngineType.CRONET;
        } else if (isEngineAvailable(HttpEngineType.OKHTTP)) {
            return HttpEngineType.OKHTTP;
        } else {
            return HttpEngineType.AUTO; // 尽管可能失败，但让工厂自行处理
        }
    }
    
    /**
     * 获取引擎支持的功能描述
     */
    public String getEngineFeatures(HttpEngineType engineType) {
        switch (engineType) {
            case CRONET:
                return "支持 HTTP/3, QUIC, HTTP/2, NetLog, 连接迁移";
            case OKHTTP:
                return "支持 HTTP/2, HTTP/1.1, 连接池, 拦截器";
            case AUTO:
                return "自动选择最佳引擎（优先 Cronet）";
            default:
                return "未知引擎";
        }
    }
    
    /**
     * 获取引擎的详细信息
     */
    public EngineInfo getEngineInfo(HttpEngineType engineType) {
        switch (engineType) {
            case CRONET:
                return new EngineInfo(
                    "Cronet",
                    "Google 提供的网络库，基于 Chromium 网络栈",
                    isEngineAvailable(HttpEngineType.CRONET),
                    "支持 HTTP/3 (QUIC), HTTP/2, 网络质量估算, NetLog, 连接迁移"
                );
            case OKHTTP:
                return new EngineInfo(
                    "OkHttp",
                    "Square 提供的 HTTP 客户端库",
                    isEngineAvailable(HttpEngineType.OKHTTP),
                    "支持 HTTP/2, HTTP/1.1, 连接池, 透明 GZIP, 请求/响应拦截器"
                );
            case AUTO:
                return new EngineInfo(
                    "自动选择",
                    "优先使用 Cronet，降级到 OkHttp",
                    isEngineAvailable(HttpEngineType.CRONET) || isEngineAvailable(HttpEngineType.OKHTTP),
                    "动态选择最佳可用引擎"
                );
            default:
                return new EngineInfo("未知", "未知引擎", false, "无");
        }
    }
    
    /**
     * 引擎信息数据类
     */
    public static class EngineInfo {
        public final String name;
        public final String description;
        public final boolean available;
        public final String features;
        
        public EngineInfo(String name, String description, boolean available, String features) {
            this.name = name;
            this.description = description;
            this.available = available;
            this.features = features;
        }
        
        @Override
        public String toString() {
            return name + " (" + (available ? "可用" : "不可用") + "): " + description + " - " + features;
        }
    }
}
