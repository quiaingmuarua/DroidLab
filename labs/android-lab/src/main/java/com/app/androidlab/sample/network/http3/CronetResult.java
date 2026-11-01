package com.app.androidlab.sample.network.http3;

public class CronetResult {
    private String title;
    private String protocol;
    private int statusCode;
    private String url;
    private String responseBody;
    private long receivedBytes;
    private boolean isQuicUsed;
    private String error;

    public CronetResult(String title) {
        this.title = title;
    }

    // Getters and Setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
        this.isQuicUsed = protocol != null && (protocol.startsWith("h3") || protocol.contains("quic"));
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public void setReceivedBytes(long receivedBytes) {
        this.receivedBytes = receivedBytes;
    }

    public boolean isQuicUsed() {
        return isQuicUsed;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getFormattedResult() {
        if (error != null) {
            return "❌ 请求失败\n错误: " + error;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🌐 Cronet HTTP/3 测试结果\n");
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        
        // 协议信息
        String protocolIcon = isQuicUsed ? "🚀" : "⚡";
        String protocolStatus = isQuicUsed ? "QUIC/HTTP3 ✅" : "降级协议 ⚠️";
        sb.append(protocolIcon).append(" 协议: ").append(protocol).append(" (").append(protocolStatus).append(")\n");
        
        // 请求信息
        sb.append("📊 状态码: HTTP ").append(statusCode).append("\n");
        sb.append("🔗 URL: ").append(url).append("\n");
        sb.append("📦 接收字节: ").append(receivedBytes).append(" bytes\n\n");
        
        // 响应体预览
        if (responseBody != null && !responseBody.isEmpty()) {
            String preview = responseBody.length() > 300 ? 
                responseBody.substring(0, 300) + "..." : responseBody;
            sb.append("📄 响应体预览 (").append(responseBody.length()).append(" 字符):\n");
            sb.append("┌─────────────────────────────────┐\n");
            // 处理HTML标签，提取可读文本
            String cleanPreview = preview.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            if (cleanPreview.length() > 200) {
                cleanPreview = cleanPreview.substring(0, 200) + "...";
            }
            sb.append(cleanPreview);
            sb.append("\n└─────────────────────────────────┘\n");
        }
        
        // 技术细节
        sb.append("\n🔧 技术特性:\n");
        sb.append("• UDP 传输: ").append(isQuicUsed ? "是" : "否").append("\n");
        sb.append("• 多路复用: ").append(isQuicUsed ? "QUIC 原生支持" : "HTTP/2 多路复用").append("\n");
        sb.append("• 0-RTT: ").append(isQuicUsed ? "支持" : "不支持").append("\n");
        
        return sb.toString();
    }
}
