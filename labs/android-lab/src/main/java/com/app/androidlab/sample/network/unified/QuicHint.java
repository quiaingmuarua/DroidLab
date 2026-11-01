package com.app.androidlab.sample.network.unified;

/**
 * 可选的 QUIC hint 数据结构
 */
public final class QuicHint {
    public final String host;
    public final int port;
    public final int altPort;
    
    public QuicHint(String host, int port, int altPort) {
        this.host = host; 
        this.port = port; 
        this.altPort = altPort;
    }
    
    public QuicHint(String host, int port) {
        this(host, port, port);
    }
    
    /**
     * 从 URL 自动提取 host 创建 QuicHint
     */
    public static QuicHint fromUrl(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port == -1) {
                String scheme = uri.getScheme();
                if ("https".equals(scheme)) {
                    port = 443;
                } else if ("http".equals(scheme)) {
                    port = 80;
                } else {
                    port = 443; // 默认HTTPS端口
                }
            }
            return new QuicHint(host, port);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public String toString() {
        return host + ":" + port + (altPort != port ? ("/" + altPort) : "");
    }
}
