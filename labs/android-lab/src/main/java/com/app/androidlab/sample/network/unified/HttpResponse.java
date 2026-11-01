package com.app.androidlab.sample.network.unified;

import java.util.*;

/**
 * HTTP 响应对象
 */
public final class HttpResponse {
    public final int code;
    public final String protocol; // "h3", "h2", "http/1.1" 等
    public final Map<String, List<String>> headers;
    public final byte[] body;
    public final long durationMs;
    public final String error; // null = 成功
    
    // 额外信息
    public final long receivedBytes;
    public final String url;
    
    public HttpResponse(int code, String protocol, Map<String, List<String>> headers, 
                       byte[] body, long durationMs, String error, long receivedBytes, String url) {
        this.code = code;
        this.protocol = protocol;
        this.headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        this.body = body;
        this.durationMs = durationMs;
        this.error = error;
        this.receivedBytes = receivedBytes;
        this.url = url;
    }
    
    public boolean isSuccessful() {
        return error == null && code >= 200 && code < 300;
    }
    
    /**
     * 获取响应体字符串，自动根据 Content-Type 解码
     */
    public String getBodyAsString() {
        if (body == null) return null;
        return CharsetUtils.decodeResponse(body, getContentType());
    }
    
    /**
     * 获取 Content-Type
     */
    public String getContentType() {
        List<String> contentTypes = headers.get("content-type");
        if (contentTypes != null && !contentTypes.isEmpty()) {
            return contentTypes.get(0);
        }
        contentTypes = headers.get("Content-Type");
        return contentTypes != null && !contentTypes.isEmpty() ? contentTypes.get(0) : null;
    }
    
    /**
     * 获取格式化的结果字符串用于显示
     */
    public String getFormattedResult() {
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 HTTP 测试结果\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        if (error != null) {
            sb.append("❌ 请求失败: ").append(error).append("\n");
        } else {
            sb.append("✅ 请求成功\n");
        }
        
        sb.append("📊 状态码: HTTP ").append(code).append("\n");
        sb.append("🔗 URL: ").append(url).append("\n");
        sb.append("🚀 协议: ").append(protocol != null ? protocol : "unknown").append("\n");
        sb.append("⏱️ 耗时: ").append(durationMs).append(" ms\n");
        sb.append("📦 接收字节: ").append(receivedBytes).append(" bytes\n");
        sb.append("📄 响应头数量: ").append(headers.size()).append("\n\n");
        
        if (body != null && body.length > 0) {
            String bodyStr = getBodyAsString();
            if (bodyStr != null) {
                String preview = bodyStr.length() > 300 ? bodyStr.substring(0, 300) + "..." : bodyStr;
                String cleanPreview = preview.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
                if (cleanPreview.length() > 200) {
                    cleanPreview = cleanPreview.substring(0, 200) + "...";
                }
                sb.append("📄 响应体预览 (").append(bodyStr.length()).append(" 字符):\n");
                sb.append("┌─────────────────────────────────┐\n");
                sb.append(cleanPreview);
                sb.append("\n└─────────────────────────────────┘\n");
            }
        }
        
        // 显示协议特殊信息
        if (protocol != null) {
            if (protocol.startsWith("h3")) {
                sb.append("\n🎉 成功使用 HTTP/3 协议！");
            } else if (protocol.startsWith("h2")) {
                sb.append("\n✨ 使用 HTTP/2 协议");
            } else {
                sb.append("\n📡 使用 HTTP/1.1 协议");
            }
        }
        
        return sb.toString();
    }
}
