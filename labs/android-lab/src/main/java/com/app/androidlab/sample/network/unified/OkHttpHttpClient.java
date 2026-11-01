package com.app.androidlab.sample.network.unified;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * OkHttp HTTP 客户端实现
 * 支持 HTTP/2 和 HTTP/1.1
 */
public class OkHttpHttpClient implements HttpClient {
    private static final String TAG = "OkHttpHttpClient";
    
    private final OkHttpClient okHttpClient;
    
    public OkHttpHttpClient() {
        this.okHttpClient = createDefaultOkHttpClient();
    }
    
    public OkHttpHttpClient(OkHttpClient customClient) {
        this.okHttpClient = customClient;
    }
    
    private OkHttpClient createDefaultOkHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .protocols(java.util.Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .build();
    }
    
    /**
     * 根据请求配置创建定制的 OkHttpClient
     */
    private OkHttpClient createOkHttpClient(HttpRequest request) {
        OkHttpClient.Builder builder = okHttpClient.newBuilder()
                .connectTimeout(request.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(request.readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(request.readTimeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(request.followRedirects)
                .followSslRedirects(request.followRedirects);
        
        // 根据 Profile 配置协议
        HttpProfile profile = request.profile;
        if (profile.forceHttp3) {
            // OkHttp 目前不支持 HTTP/3，记录警告
            Log.w(TAG, "OkHttp 不支持 HTTP/3，将使用 HTTP/2");
            builder.protocols(java.util.Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        } else {
            // 默认支持的协议
            builder.protocols(java.util.Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1));
        }
        
        // 添加拦截器用于限制响应体大小
        builder.addNetworkInterceptor(chain -> {
            Response originalResponse = chain.proceed(chain.request());
            ResponseBody originalBody = originalResponse.body();
            
            if (originalBody != null) {
                long contentLength = originalBody.contentLength();
                if (contentLength > request.maxBodyBytes) {
                    Log.w(TAG, "响应体过大 (" + contentLength + " bytes)，截断为 " + request.maxBodyBytes + " bytes");
                }
            }
            
            return originalResponse;
        });
        
        return builder.build();
    }
    
    @Override
    public HttpCall enqueue(HttpRequest request, HttpCallback callback) {
        OkHttpHttpCall call = new OkHttpHttpCall();
        
        // 创建定制的客户端
        OkHttpClient client = needsCustomClient(request) ? 
            createOkHttpClient(request) : okHttpClient;
        
        long startTime = System.currentTimeMillis();
        
        // 构建 OkHttp 请求
        Request.Builder requestBuilder = new Request.Builder()
                .url(request.url)
                .method(request.method, createRequestBody(request));
        
        // 添加请求头
        for (Map.Entry<String, String> header : request.headers.entrySet()) {
            requestBuilder.addHeader(header.getKey(), header.getValue());
        }
        
        Request okHttpRequest = requestBuilder.build();
        Call okHttpCall = client.newCall(okHttpRequest);
        call.setOkHttpCall(okHttpCall);
        
        // 执行异步请求
        okHttpCall.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (call.isCanceled()) {
                    return;
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Log.e(TAG, "Request failed", e);
                
                // 构建错误响应
                HttpResponse response = new HttpResponse(
                    0,
                    null,
                    new HashMap<>(),
                    null,
                    duration,
                    e.getMessage(),
                    0,
                    request.url
                );
                
                callback.onComplete(response);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (call.isCanceled()) {
                    response.close();
                    return;
                }
                
                long duration = System.currentTimeMillis() - startTime;
                
                try {
                    // 读取响应体
                    byte[] responseBody = null;
                    long receivedBytes = 0;
                    
                    ResponseBody body = response.body();
                    if (body != null) {
                        try {
                            long contentLength = body.contentLength();
                            
                            // 限制响应体大小
                            if (contentLength > 0 && contentLength <= request.maxBodyBytes) {
                                responseBody = body.bytes();
                                receivedBytes = responseBody.length;
                            } else if (contentLength <= 0) {
                                // 未知长度，尝试读取但限制大小
                                byte[] buffer = new byte[Math.min(request.maxBodyBytes, 8192)];
                                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                                java.io.InputStream is = body.byteStream();
                                
                                int totalRead = 0;
                                int bytesRead;
                                while ((bytesRead = is.read(buffer)) != -1 && totalRead + bytesRead <= request.maxBodyBytes) {
                                    baos.write(buffer, 0, bytesRead);
                                    totalRead += bytesRead;
                                }
                                
                                responseBody = baos.toByteArray();
                                receivedBytes = responseBody.length;
                                
                                if (totalRead >= request.maxBodyBytes) {
                                    Log.w(TAG, "Response body truncated at " + request.maxBodyBytes + " bytes");
                                }
                            } else {
                                Log.w(TAG, "Response body too large (" + contentLength + " bytes), skipped");
                                receivedBytes = contentLength;
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error reading response body", e);
                        }
                    }
                    
                    // 提取响应头
                    Map<String, List<String>> responseHeaders = new HashMap<>();
                    for (String name : response.headers().names()) {
                        responseHeaders.put(name, response.headers().values(name));
                    }
                    
                    // 获取协议信息
                    String protocol = getProtocolString(response.protocol());
                    
                    // 构建响应对象
                    HttpResponse httpResponse = new HttpResponse(
                        response.code(),
                        protocol,
                        responseHeaders,
                        responseBody,
                        duration,
                        null, // 成功时 error 为 null
                        receivedBytes,
                        request.url
                    );
                    
                    Log.d(TAG, "Request succeeded - Protocol: " + protocol + 
                              ", Status: " + response.code() + 
                              ", Duration: " + duration + "ms, Bytes: " + receivedBytes);
                    
                    callback.onComplete(httpResponse);
                    
                } finally {
                    response.close();
                }
            }
        });
        
        return call;
    }
    
    private RequestBody createRequestBody(HttpRequest request) {
        if (request.body == null || request.body.length == 0) {
            if (needsRequestBody(request.method)) {
                return RequestBody.create(new byte[0], null);
            }
            return null;
        }
        
        // 尝试从请求头中获取 Content-Type
        String contentType = request.headers.get("Content-Type");
        if (contentType == null) {
            contentType = request.headers.get("content-type");
        }
        
        MediaType mediaType = null;
        if (contentType != null) {
            try {
                mediaType = MediaType.parse(contentType);
            } catch (Exception e) {
                Log.w(TAG, "Invalid Content-Type: " + contentType, e);
            }
        }
        
        return RequestBody.create(request.body, mediaType);
    }
    
    private boolean needsRequestBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }
    
    private String getProtocolString(Protocol protocol) {
        if (protocol == null) return "unknown";
        
        switch (protocol) {
            case HTTP_1_0:
                return "http/1.0";
            case HTTP_1_1:
                return "http/1.1";
            case HTTP_2:
                return "h2";
            case H2_PRIOR_KNOWLEDGE:
                return "h2c";
            case QUIC:
                return "h3"; // 虽然 OkHttp 目前不支持，但为了兼容性
            default:
                return protocol.toString().toLowerCase();
        }
    }
    
    private boolean needsCustomClient(HttpRequest request) {
        return request.connectTimeoutMs != 10_000 ||
               request.readTimeoutMs != 20_000 ||
               !request.followRedirects ||
               request.profile != HttpProfile.DEFAULT ||
               request.maxBodyBytes != 128 * 1024;
    }
    
    @Override
    public HttpEngineType getEngineType() {
        return HttpEngineType.OKHTTP;
    }
    
    @Override
    public void shutdown() {
        // OkHttp 会自动管理连接池，通常不需要显式关闭
        if (okHttpClient != null) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
        }
    }
    
    /**
     * OkHttp HTTP 调用实现
     */
    private static class OkHttpHttpCall implements HttpCall {
        private final AtomicBoolean canceled = new AtomicBoolean(false);
        private volatile Call okHttpCall;
        
        public void setOkHttpCall(Call call) {
            this.okHttpCall = call;
        }
        
        @Override
        public void cancel() {
            if (canceled.compareAndSet(false, true)) {
                if (okHttpCall != null) {
                    okHttpCall.cancel();
                }
            }
        }
        
        @Override
        public boolean isCanceled() {
            return canceled.get() || (okHttpCall != null && okHttpCall.isCanceled());
        }
    }
}
