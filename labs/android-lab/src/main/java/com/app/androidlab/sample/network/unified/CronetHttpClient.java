package com.app.androidlab.sample.network.unified;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.net.CronetProviderInstaller;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cronet HTTP 客户端实现
 * 支持 HTTP/3、NetLog、动态 QUIC hints
 */
public class CronetHttpClient implements HttpClient {
    private static final String TAG = "CronetHttpClient";
    private static final int DEFAULT_BUFFER_SIZE = 32 * 1024; // 32KB
    
    private final Context context;
    private final CronetEngine cronetEngine;
    private final CronetEngine http3Engine; // 专门用于HTTP/3的引擎
    private final Executor executor;
    
    public CronetHttpClient(Context context) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.cronetEngine = createDefaultCronetEngine();
        this.http3Engine = createHttp3CronetEngine(); // 创建专门的HTTP/3引擎
        
        // 异步安装 Cronet Provider
        installCronetProvider();
    }
    
    public CronetHttpClient(Context context, CronetEngine customEngine) {
        this.context = context;
        this.executor = Executors.newCachedThreadPool();
        this.cronetEngine = customEngine;
        this.http3Engine = createHttp3CronetEngine(); // 创建专门的HTTP/3引擎
        
        installCronetProvider();
    }
    
    private void installCronetProvider() {
        CronetProviderInstaller.installProvider(context).addOnCompleteListener(task -> {
            if (task.getException() == null) {
                Log.i(TAG, "Cronet Provider installed successfully");
            } else {
                Log.w(TAG, "Cronet Provider installation failed", task.getException());
            }
        });
    }
    
    private CronetEngine createDefaultCronetEngine() {
        return new CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024) // 100KB
                .build();
    }
    
    /**
     * 创建专门用于HTTP/3的CronetEngine (仿照CronetHttp3Utils的实现)
     */
    private CronetEngine createHttp3CronetEngine() {
        Log.d(TAG, "创建专门的HTTP/3引擎");
        return new CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(false)  // 显式禁用 HTTP/2
                .enableBrotli(true)  // 启用 Brotli 压缩
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024) // 100KB cache
                // 添加 QUIC hint - 指定支持 QUIC 的主机
                .addQuicHint("http3check.net", 443, 443)
                .addQuicHint("quic.nginx.org", 443, 443)
                .addQuicHint("cloudflare-quic.com", 443, 443)
                .addQuicHint("www.google.com", 443, 443)
                .addQuicHint("blog.cloudflare.com", 443, 443)
                .build();
    }
    
    @Override
    public HttpCall enqueue(HttpRequest request, HttpCallback callback) {
        CronetHttpCall call = new CronetHttpCall();
        
        // 选择合适的引擎（重用预建的引擎而不是每次创建新的）
        CronetEngine engine = request.profile.forceHttp3 ? http3Engine : cronetEngine;
        
        if (request.profile.forceHttp3) {
            Log.d(TAG, "使用专门的HTTP/3引擎");
        } else {
            Log.d(TAG, "使用默认引擎（支持协议回退）");
        }
        
        long startTime = System.currentTimeMillis();
        
        UrlRequest.Callback urlCallback = new UrlRequest.Callback() {
            private final ByteBuffer buffer = ByteBuffer.allocateDirect(DEFAULT_BUFFER_SIZE);
            private final List<byte[]> responseChunks = new ArrayList<>();
            private final Map<String, List<String>> responseHeaders = new HashMap<>();
            private int totalBytesRead = 0;
            
            @Override
            public void onRedirectReceived(UrlRequest urlRequest, UrlResponseInfo info, String newLocationUrl) {
                if (call.isCanceled()) {
                    urlRequest.cancel();
                    return;
                }
                
                if (request.followRedirects) {
                    urlRequest.followRedirect();
                } else {
                    // 不跟随重定向，直接完成
                    handleResponse(urlRequest, info);
                }
            }
            
            @Override
            public void onResponseStarted(UrlRequest urlRequest, UrlResponseInfo info) {
                if (call.isCanceled()) {
                    urlRequest.cancel();
                    return;
                }
                
                String protocol = info.getNegotiatedProtocol();
                Log.d(TAG, "=== Response Started ===");
                Log.d(TAG, "Protocol: " + protocol);
                Log.d(TAG, "Status: " + info.getHttpStatusCode());
                Log.d(TAG, "URL: " + info.getUrl());
                Log.d(TAG, "Was cached: " + info.wasCached());
                Log.d(TAG, "Response headers: " + info.getAllHeaders().keySet());
                
                // 检查是否成功使用HTTP/3
                if (request.profile.forceHttp3) {
                    if (protocol != null && (protocol.startsWith("h3") || protocol.contains("quic"))) {
                        Log.i(TAG, "✅ 成功强制使用HTTP/3: " + protocol);
                    } else {
                        Log.w(TAG, "❌ 强制HTTP/3失败，实际协议: " + protocol);
                        Log.w(TAG, "可能原因: 服务器不支持HTTP/3或网络环境限制");
                    }
                }
                
                // 保存响应头
                responseHeaders.clear();
                responseHeaders.putAll(info.getAllHeaders());
                
                urlRequest.read(buffer);
            }
            
            @Override
            public void onReadCompleted(UrlRequest urlRequest, UrlResponseInfo info, ByteBuffer byteBuffer) {
                if (call.isCanceled()) {
                    urlRequest.cancel();
                    return;
                }
                
                // 正确处理 ByteBuffer (onReadCompleted中已经是读模式，不需要flip)
                int bytesToRead = byteBuffer.remaining();
                if (bytesToRead > 0 && totalBytesRead + bytesToRead <= request.maxBodyBytes) {
                    byte[] chunk = new byte[bytesToRead];
                    byteBuffer.get(chunk);
                    responseChunks.add(chunk);
                    totalBytesRead += bytesToRead;
                    
                    Log.d(TAG, "Read " + bytesToRead + " bytes, total: " + totalBytesRead);
                } else if (totalBytesRead + bytesToRead > request.maxBodyBytes) {
                    Log.w(TAG, "Response body too large, truncating at " + request.maxBodyBytes + " bytes");
                }
                
                byteBuffer.clear(); // 为下次读取准备
                urlRequest.read(byteBuffer);
            }
            
            @Override
            public void onSucceeded(UrlRequest urlRequest, UrlResponseInfo info) {
                if (call.isCanceled()) {
                    return;
                }
                
                handleResponse(urlRequest, info);
            }
            
            @Override
            public void onFailed(UrlRequest urlRequest, UrlResponseInfo info, CronetException error) {
                if (call.isCanceled()) {
                    return;
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Request failed", error);
                
                // 构建错误响应
                HttpResponse response = new HttpResponse(
                    info != null ? info.getHttpStatusCode() : 0,
                    info != null ? info.getNegotiatedProtocol() : null,
                    info != null ? info.getAllHeaders() : new HashMap<>(),
                    null,
                    duration,
                    error.getMessage(),
                    info != null ? info.getReceivedByteCount() : 0,
                    request.url
                );
                
                callback.onComplete(response);
            }
            
            private void handleResponse(UrlRequest urlRequest, UrlResponseInfo info) {
                long duration = System.currentTimeMillis() - startTime;
                
                // 合并响应体
                byte[] responseBody = null;
                if (!responseChunks.isEmpty()) {
                    int totalSize = 0;
                    for (byte[] chunk : responseChunks) {
                        totalSize += chunk.length;
                    }
                    
                    responseBody = new byte[totalSize];
                    int offset = 0;
                    for (byte[] chunk : responseChunks) {
                        System.arraycopy(chunk, 0, responseBody, offset, chunk.length);
                        offset += chunk.length;
                    }
                }
                
                // 构建响应对象
                HttpResponse response = new HttpResponse(
                    info.getHttpStatusCode(),
                    info.getNegotiatedProtocol(),
                    new HashMap<>(responseHeaders),
                    responseBody,
                    duration,
                    null, // 成功时 error 为 null
                    info.getReceivedByteCount(),
                    request.url
                );
                
                Log.d(TAG, "Request succeeded - Protocol: " + info.getNegotiatedProtocol() + 
                          ", Duration: " + duration + "ms, Bytes: " + totalBytesRead);
                
                callback.onComplete(response);
            }
        };
        
        // 构建请求
        UrlRequest.Builder requestBuilder = engine.newUrlRequestBuilder(request.url, urlCallback, executor)
                .setHttpMethod(request.method);
        
        // 设置超时
        // 注意：Cronet 不直接支持超时设置，需要通过其他方式实现
        
        // 添加请求头
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }
        
        // 强制HTTP/3时添加特殊头信息
        if (request.profile.forceHttp3) {
            requestBuilder.addHeader("User-Agent", "CronetHttp3Client/1.0 (Android; HTTP/3 Test)");
            Log.d(TAG, "添加强制HTTP/3请求头");
        }
        
        // 添加请求体
        if (request.body != null && request.body.length > 0) {
            requestBuilder.setUploadDataProvider(
                new ByteArrayUploadDataProvider(request.body), executor);
        }
        
        UrlRequest urlRequest = requestBuilder.build();
        call.setUrlRequest(urlRequest);
        
        // 启动请求
        urlRequest.start();
        
        return call;
    }
    
    @Override
    public HttpEngineType getEngineType() {
        return HttpEngineType.CRONET;
    }
    
    @Override
    public void shutdown() {
        if (cronetEngine != null) {
            cronetEngine.shutdown();
        }
        if (http3Engine != null) {
            http3Engine.shutdown();
        }
    }
    
    /**
     * Cronet HTTP 调用实现
     */
    private static class CronetHttpCall implements HttpCall {
        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private volatile UrlRequest urlRequest;
        
        public void setUrlRequest(UrlRequest urlRequest) {
            this.urlRequest = urlRequest;
        }
        
        @Override
        public void cancel() {
            if (canceled.compareAndSet(false, true)) {
                if (urlRequest != null) {
                    urlRequest.cancel();
                }
            }
        }
        
        @Override
        public boolean isCanceled() {
            return canceled.get();
        }
    }
    
    /**
     * 字节数组上传数据提供者
     */
    private static class ByteArrayUploadDataProvider extends org.chromium.net.UploadDataProvider {
        private final byte[] data;
        
        public ByteArrayUploadDataProvider(byte[] data) {
            this.data = data;
        }
        
        @Override
        public long getLength() {
            return data.length;
        }
        
        @Override
        public void read(org.chromium.net.UploadDataSink uploadDataSink, ByteBuffer byteBuffer) {
            int remaining = byteBuffer.remaining();
            int toRead = Math.min(remaining, data.length);
            byteBuffer.put(data, 0, toRead);
            uploadDataSink.onReadSucceeded(false); // false 表示还有更多数据或已完成
        }
        
        @Override
        public void rewind(org.chromium.net.UploadDataSink uploadDataSink) {
            uploadDataSink.onRewindSucceeded();
        }
    }
}
