package com.app.androidlab.sample.network.unified;

/**
 * 客户端引擎类型
 */
public enum HttpEngineType {
    AUTO,     // 自动选择（优先 Cronet，降级到 OkHttp）
    CRONET,   // 强制使用 Cronet
    OKHTTP    // 强制使用 OkHttp
}
