package com.app.androidlab.sample.network.http3;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.net.CronetProviderInstaller;

import org.chromium.net.CronetEngine;
import org.chromium.net.CronetException;
import org.chromium.net.UrlRequest;
import org.chromium.net.UrlResponseInfo;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CronetHttp3Utils {

    public static void testMultipleQuicSites(Context context, String title, CronetCallback callback) {
        String[] quicSites = {
            "https://quic.nginx.org/",
            "https://cloudflare-quic.com/",
            "https://http3check.net/"
        };
        
        Log.i("CronetHttp3Utils", "开始测试多个 QUIC/HTTP3 站点");
        for (int i = 0; i < quicSites.length; i++) {
            String url = quicSites[i];
            String siteTitle = title + " (站点 " + (i + 1) + ")";
            Log.i("CronetHttp3Utils", "测试站点: " + url);
            getUrl(context, url, siteTitle, callback);
        }
    }

    public static void getUrl(Context context, String url, String title, CronetCallback callback) {
        // 异步安装 Google Play Services 提供的 Cronet Provider
        CronetProviderInstaller.installProvider(context).addOnCompleteListener(task -> {
            if (task.getException() == null) {
                Log.i("CronetHttp3Utils", "Cronet Provider installed successfully");
            } else {
                Log.w("CronetHttp3Utils", "Cronet Provider installation failed", task.getException());
            }
        });

        // 创建 Cronet 引擎，强制使用 QUIC/HTTP3
        CronetEngine cronetEngine = new CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(false)  // 显式禁用 HTTP/2
                .enableBrotli(true)  // 启用 Brotli 压缩
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_IN_MEMORY, 100 * 1024) // 100KB cache
                // 添加 QUIC hint - 指定支持 QUIC 的主机
                .addQuicHint("http3check.net", 443, 443)
                .addQuicHint("quic.nginx.org", 443, 443)
                .addQuicHint("cloudflare-quic.com", 443, 443)
                .build();

        Executor executor = Executors.newSingleThreadExecutor();

        // 创建结果对象
        CronetResult result = new CronetResult(title);
        result.setUrl(url);

        UrlRequest.Callback requestCallback = new UrlRequest.Callback() {
            private final ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024);
            private final StringBuilder responseBodyBuilder = new StringBuilder();
            private boolean isFirstRead = true;

            @Override
            public void onRedirectReceived(UrlRequest request, UrlResponseInfo info, String newLocationUrl) {
                request.followRedirect();
            }

            @Override
            public void onResponseStarted(UrlRequest request, UrlResponseInfo info) {
                String protocol = info.getNegotiatedProtocol();
                
                // 保存响应信息到结果对象
                result.setProtocol(protocol);
                result.setStatusCode(info.getHttpStatusCode());
                result.setReceivedBytes(info.getReceivedByteCount());
                
                Log.i("CronetHttp3Utils", "=== QUIC/HTTP3 测试结果 ===");
                Log.i("CronetHttp3Utils", "协商协议: " + protocol);
                Log.i("CronetHttp3Utils", "HTTP状态码: " + info.getHttpStatusCode());
                Log.i("CronetHttp3Utils", "URL: " + info.getUrl());
                Log.i("CronetHttp3Utils", "接收字节数: " + info.getReceivedByteCount());
                
                // 检查是否成功使用了 QUIC/HTTP3
                if (protocol != null && (protocol.startsWith("h3") || protocol.contains("quic"))) {
                    Log.i("CronetHttp3Utils", "✅ 成功使用 QUIC/HTTP3 协议！");
                } else {
                    Log.w("CronetHttp3Utils", "⚠️ 未能使用 QUIC/HTTP3，当前协议: " + protocol);
                }
                
                request.read(buffer);
            }

            @Override
            public void onReadCompleted(UrlRequest request, UrlResponseInfo info, ByteBuffer byteBuffer) {
                // 读取缓冲区中的数据
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);
                String chunk = new String(bytes);
                responseBodyBuilder.append(chunk);
                
                // 限制响应体大小，避免内存溢出
                if (responseBodyBuilder.length() > 10000) { // 限制10KB
                    if (isFirstRead) {
                        responseBodyBuilder.append("\n...(响应体过大，已截断)");
                        isFirstRead = false;
                    }
                } else {
                    Log.d("CronetHttp3Utils", "读取数据块: " + bytes.length + " bytes");
                }
                
                byteBuffer.clear();
                request.read(byteBuffer);
            }

            @Override
            public void onSucceeded(UrlRequest request, UrlResponseInfo info) {
                String protocol = info.getNegotiatedProtocol();
                Log.i("CronetHttp3Utils", "=== 请求完成 ===");
                Log.i("CronetHttp3Utils", "最终结果: HTTP " + info.getHttpStatusCode() + " via " + protocol);
                
                // 完善结果对象
                result.setProtocol(protocol);
                result.setStatusCode(info.getHttpStatusCode());
                result.setReceivedBytes(info.getReceivedByteCount());
                result.setResponseBody(responseBodyBuilder.toString());
                
                if (protocol != null && (protocol.startsWith("h3") || protocol.contains("quic"))) {
                    Log.i("CronetHttp3Utils", "🎉 QUIC/HTTP3 测试成功！协议: " + protocol);
                } else {
                    Log.w("CronetHttp3Utils", "❌ QUIC 测试失败，降级到: " + protocol);
                    Log.w("CronetHttp3Utils", "可能原因: 1) 服务器不支持HTTP/3 2) 网络环境限制 3) Cronet配置问题");
                }
                Log.i("CronetHttp3Utils", "响应体长度: " + responseBodyBuilder.length() + " 字符");
                Log.i("CronetHttp3Utils", "=============================");
                
                // 通过回调返回结果
                if (callback != null) {
                    callback.onCronetFinish(result);
                }
            }

            @Override
            public void onFailed(UrlRequest request, UrlResponseInfo info, CronetException error) {
                Log.e("CronetHttp3Utils", "请求失败: " + error);
                
                // 设置错误信息
                result.setError(error != null ? error.getMessage() : "Unknown error");
                
                // 尝试获取部分响应信息
                if (info != null) {
                    result.setProtocol(info.getNegotiatedProtocol());
                    result.setStatusCode(info.getHttpStatusCode());
                    result.setReceivedBytes(info.getReceivedByteCount());
                }
                
                // 通过回调返回错误结果
                if (callback != null) {
                    callback.onCronetFinish(result);
                }
            }
        };

        UrlRequest request = cronetEngine.newUrlRequestBuilder(url, requestCallback, executor).build();
        request.start();
    }
}
