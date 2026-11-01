package com.app.androidlab.sample.network.unified;

/**
 * HTTP 调用句柄，支持取消操作
 */
public interface HttpCall {
    /**
     * 取消请求
     */
    void cancel();
    
    /**
     * 检查是否已取消
     */
    boolean isCanceled();
}
