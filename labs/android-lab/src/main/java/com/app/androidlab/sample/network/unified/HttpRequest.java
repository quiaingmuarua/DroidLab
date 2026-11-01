package com.app.androidlab.sample.network.unified;

import java.util.*;

/**
 * HTTP 请求对象
 */
public final class HttpRequest {
    public final String url;
    public final String method;
    public final Map<String, String> headers;
    public final byte[] body;

    public final int connectTimeoutMs;
    public final int readTimeoutMs;
    public final boolean followRedirects;
    public final int maxBodyBytes;

    // Profile / hints（给 Cronet 用，其它引擎忽略）
    public final HttpProfile profile;
    public final List<QuicHint> quicHints; // 可为空

    private HttpRequest(Builder b) {
        url = b.url;
        method = b.method;
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
        body = b.body;
        connectTimeoutMs = b.connectTimeoutMs;
        readTimeoutMs = b.readTimeoutMs;
        followRedirects = b.followRedirects;
        maxBodyBytes = b.maxBodyBytes;
        profile = b.profile;
        quicHints = b.quicHints == null ? null : Collections.unmodifiableList(new ArrayList<>(b.quicHints));
    }

    public static class Builder {
        private String url, method = "GET";
        private Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body;
        private int connectTimeoutMs = 10_000, readTimeoutMs = 20_000, maxBodyBytes = 128*1024;
        private boolean followRedirects = true;
        private HttpProfile profile = HttpProfile.DEFAULT;
        private List<QuicHint> quicHints;

        public Builder url(String u){ this.url=u; return this; }
        public Builder method(String m){ this.method=m; return this; }
        public Builder header(String k,String v){ headers.put(k,v); return this; }
        public Builder headers(Map<String,String> hs){ headers.putAll(hs); return this; }
        public Builder body(byte[] b){ this.body=b; return this; }
        public Builder body(String text) { 
            this.body = text != null ? text.getBytes() : null; 
            return this; 
        }
        public Builder timeouts(int connectMs, int readMs){ 
            this.connectTimeoutMs=connectMs; this.readTimeoutMs=readMs; return this; 
        }
        public Builder followRedirects(boolean v){ this.followRedirects=v; return this; }
        public Builder maxBodyBytes(int v){ this.maxBodyBytes=v; return this; }
        public Builder profile(HttpProfile p){ this.profile=p; return this; }
        public Builder quicHints(List<QuicHint> hints){ this.quicHints=hints; return this; }
        
        // 便利方法：从 URL 自动生成 QUIC hints
        public Builder autoQuicHints() {
            if (url != null) {
                QuicHint hint = QuicHint.fromUrl(url);
                if (hint != null) {
                    this.quicHints = Arrays.asList(hint);
                }
            }
            return this;
        }
        
        public HttpRequest build(){ 
            if (url == null || url.trim().isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            return new HttpRequest(this); 
        }
    }
}
