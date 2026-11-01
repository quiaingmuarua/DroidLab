package com.app.androidlab.sample.network.unified;

/**
 * 测试/运行 Profile
 */
public final class HttpProfile {
    // 正常：允许回退（h3/h2/h1），不启 NetLog
    public static final HttpProfile DEFAULT = new HttpProfile(false, false, 0, null);
    
    // 强制 H3 + NetLog（示例）
    public static HttpProfile h3Test(String netLogDirAbsPath, int maxLogBytes) {
        return new HttpProfile(true, true, maxLogBytes, netLogDirAbsPath);
    }
    
    // 强制 H3 但不启用 NetLog
    public static final HttpProfile FORCE_HTTP3 = new HttpProfile(true, false, 0, null);

    public final boolean forceHttp3;      // true 则禁用 h2/h1，只测 h3
    public final boolean enableNetLog;    // 开启 Cronet NetLog
    public final int netLogMaxSizeBytes;  // NetLog 大小
    public final String netLogDir;        // NetLog 输出目录（绝对路径）

    public HttpProfile(boolean forceHttp3, boolean enableNetLog, int size, String dir) {
        this.forceHttp3 = forceHttp3;
        this.enableNetLog = enableNetLog;
        this.netLogMaxSizeBytes = size;
        this.netLogDir = dir;
    }
}
