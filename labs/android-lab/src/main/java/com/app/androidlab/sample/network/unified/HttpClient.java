package com.app.androidlab.sample.network.unified;

/**
 * HTTP 客户端核心接口
 */
public interface HttpClient {
    /**
     * 异步执行 HTTP 请求
     * 
     * @param request HTTP 请求对象
     * @param callback 回调接口
     * @return HttpCall 对象，可用于取消请求
     */
    HttpCall enqueue(HttpRequest request, HttpCallback callback);
    
    /**
     * 获取客户端引擎类型
     */
    HttpEngineType getEngineType();
    
    /**
     * 释放资源（可选实现）
     */
    default void shutdown() {
        // 默认空实现
    }
}
