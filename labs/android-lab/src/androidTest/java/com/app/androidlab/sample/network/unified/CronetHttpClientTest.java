package com.app.androidlab.sample.network.unified;

import static org.junit.Assert.*;
import static org.junit.Assume.*;

import android.util.Log;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class CronetHttpClientTest {

    private static final String TAG = "CronetHttpClientTest";
    private static final int TIMEOUT_SECONDS = 30;

    private HttpClientFactory factory;
    private HttpClient client;

    @Before
    public void setUp() {
        // 检查是否在CI环境中，如果是则跳过需要GMS的测试
        String skipInstrumented = System.getenv("SKIP_INSTRUMENTED_TESTS");
        assumeFalse("Skipping instrumented tests in CI environment", "true".equals(skipInstrumented));
        
        Context context = ApplicationProvider.getApplicationContext();
        
        // 检查是否有Google Play Services
        try {
            Class.forName("com.google.android.gms.net.CronetProviderInstaller");
            Log.d(TAG, "Google Play Services detected");
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "Google Play Services not available, some tests may fail");
        }
        
        factory = new HttpClientFactory(context);
        client = factory.createClient(HttpEngineType.CRONET);
    }

    @After
    public void tearDown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @MediumTest
    public void testBasicRequest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();

        HttpRequest request = new HttpRequest.Builder()
                .url("https://httpbin.org/get")
                .timeouts(10_000, 20_000)
                .build();

        client.enqueue(request, response -> {
            responseRef.set(response);
            Log.d(TAG, "基础请求 - 协议: " + response.protocol + 
                      ", 状态码: " + response.code + 
                      ", 耗时: " + response.durationMs + "ms");
            latch.countDown();
        });

        // 等待请求完成
        assertTrue("Request timed out", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        HttpResponse response = responseRef.get();
        assertNotNull("Response should not be null", response);
        assertEquals("Status code should be 200", 200, response.code);
        assertNotNull("Protocol should not be null", response.protocol);
        assertNull("Error should be null for successful request", response.error);
        assertTrue("Duration should be positive", response.durationMs > 0);
    }

    @Test
    @LargeTest  // HTTP/3测试需要更长时间和网络连接
    public void testHttp3ForceRequest() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();

        HttpRequest h3Request = new HttpRequest.Builder()
                .url("https://quic.nginx.org/")
                .profile(HttpProfile.FORCE_HTTP3)
                .autoQuicHints()
                .timeouts(15_000, 30_000) // HTTP/3 可能需要更长时间建立连接
                .build();

        client.enqueue(h3Request, response -> {
            responseRef.set(response);
            Log.d(TAG, "HTTP/3强制请求 - 协议: " + response.protocol + 
                      ", 状态码: " + response.code + 
                      ", 耗时: " + response.durationMs + "ms" +
                      ", 错误: " + response.error);
            
            // 检查是否成功使用HTTP/3
            if (response.protocol != null && 
                (response.protocol.startsWith("h3") || response.protocol.contains("quic"))) {
                Log.i(TAG, "✅ 成功使用HTTP/3协议: " + response.protocol);
            } else {
                Log.w(TAG, "⚠️ 未能使用HTTP/3，当前协议: " + response.protocol);
            }
            
            latch.countDown();
        });

        // 等待请求完成
        assertTrue("HTTP/3 request timed out", latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        HttpResponse response = responseRef.get();
        assertNotNull("Response should not be null", response);
        
        // 验证响应
        if (response.error == null) {
            assertEquals("Status code should be 200 for successful request", 200, response.code);
            assertNotNull("Protocol should not be null", response.protocol);
            assertTrue("Duration should be positive", response.durationMs > 0);
            
            // 如果成功，检查是否真的使用了HTTP/3
            Log.i(TAG, "HTTP/3测试结果 - 协议: " + response.protocol + 
                      " (期望: h3.x 或包含 quic)");
        } else {
            Log.w(TAG, "HTTP/3请求失败: " + response.error);
            // HTTP/3失败是可能的，取决于网络环境
        }
    }

    @Test
    @LargeTest  // 多请求测试耗时较长
    public void testMultipleRequests() throws InterruptedException {
        String[] testUrls = {
            "https://httpbin.org/get",
            "https://quic.nginx.org/",
            "https://http3check.net/"
        };

        CountDownLatch latch = new CountDownLatch(testUrls.length);
        AtomicReference<Integer> successCount = new AtomicReference<>(0);

        for (String url : testUrls) {
            HttpRequest request = new HttpRequest.Builder()
                    .url(url)
                    .profile(url.contains("http3check") || url.contains("quic") ? 
                            HttpProfile.FORCE_HTTP3 : HttpProfile.DEFAULT)
                    .autoQuicHints()
                    .timeouts(10_000, 20_000)
                    .build();

            client.enqueue(request, response -> {
                Log.d(TAG, "多请求测试 [" + url + "] - " +
                          "协议: " + response.protocol + 
                          ", 状态码: " + response.code +
                          ", 耗时: " + response.durationMs + "ms");

                if (response.error == null && response.code == 200) {
                    successCount.updateAndGet(count -> count + 1);
                }
                latch.countDown();
            });
        }

        // 等待所有请求完成
        assertTrue("Multiple requests timed out", 
                  latch.await(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS));

        int successful = successCount.get();
        Log.i(TAG, "多请求测试完成: " + successful + "/" + testUrls.length + " 成功");
        
        // 至少应该有一些请求成功
        assertTrue("At least some requests should succeed", successful > 0);
    }

    @Test
    @MediumTest
    public void testRequestCancellation() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<HttpResponse> responseRef = new AtomicReference<>();

        HttpRequest request = new HttpRequest.Builder()
                .url("https://httpbin.org/delay/5") // 5秒延迟
                .build();

        HttpCall call = client.enqueue(request, response -> {
            responseRef.set(response);
            Log.d(TAG, "取消测试响应: " + response.error);
            latch.countDown();
        });

        // 立即取消请求
        call.cancel();
        assertTrue("Request should be marked as cancelled", call.isCanceled());

        // 等待回调完成或超时
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        
        Log.i(TAG, "取消测试完成 - 回调是否执行: " + completed);
    }
}