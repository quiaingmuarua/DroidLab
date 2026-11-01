package com.app.androidlab.sample.network.unified;

/**
 * HTTP 回调接口
 */
public interface HttpCallback {
    /**
     * 请求完成回调（成功或失败）
     */
    void onComplete(HttpResponse response);
}
