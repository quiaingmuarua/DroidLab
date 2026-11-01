package com.app.androidlab.sample.network.unified;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 批量并发测试工具
 * 支持多 URL、多引擎并发测试
 */
public class BatchTestUtils {
    private static final String TAG = "BatchTestUtils";
    
    /**
     * 批量测试结果接口
     */
    public interface BatchTestCallback {
        /**
         * 单个测试完成
         * @param index 测试索引
         * @param testCase 测试用例
         * @param response 响应结果
         */
        void onTestComplete(int index, TestCase testCase, HttpResponse response);
        
        /**
         * 所有测试完成
         * @param results 所有测试结果
         * @param summary 测试摘要
         */
        void onAllTestsComplete(List<TestResult> results, TestSummary summary);
        
        /**
         * 测试进度更新
         * @param completed 已完成数量
         * @param total 总数量
         */
        default void onProgress(int completed, int total) {
            // 默认空实现
        }
    }
    
    /**
     * 测试用例
     */
    public static class TestCase {
        public final String name;
        public final HttpRequest request;
        public final HttpEngineType engineType;
        public final int index;
        
        public TestCase(String name, HttpRequest request, HttpEngineType engineType, int index) {
            this.name = name;
            this.request = request;
            this.engineType = engineType;
            this.index = index;
        }
        
        @Override
        public String toString() {
            return name + " (" + engineType + ")";
        }
    }
    
    /**
     * 测试结果
     */
    public static class TestResult {
        public final TestCase testCase;
        public final HttpResponse response;
        public final long timestamp;
        
        public TestResult(TestCase testCase, HttpResponse response) {
            this.testCase = testCase;
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isSuccessful() {
            return response.isSuccessful();
        }
        
        public String getProtocol() {
            return response.protocol != null ? response.protocol : "unknown";
        }
    }
    
    /**
     * 测试摘要
     */
    public static class TestSummary {
        public final int totalTests;
        public final int successfulTests;
        public final int failedTests;
        public final long totalDuration;
        public final long averageDuration;
        public final List<String> protocols;
        public final List<HttpEngineType> engines;
        
        public TestSummary(List<TestResult> results) {
            this.totalTests = results.size();
            
            int successful = 0;
            long totalTime = 0;
            List<String> protocolsList = new ArrayList<>();
            List<HttpEngineType> enginesList = new ArrayList<>();
            
            for (TestResult result : results) {
                if (result.isSuccessful()) {
                    successful++;
                }
                totalTime += result.response.durationMs;
                
                String protocol = result.getProtocol();
                if (!protocolsList.contains(protocol)) {
                    protocolsList.add(protocol);
                }
                
                HttpEngineType engine = result.testCase.engineType;
                if (!enginesList.contains(engine)) {
                    enginesList.add(engine);
                }
            }
            
            this.successfulTests = successful;
            this.failedTests = totalTests - successful;
            this.totalDuration = totalTime;
            this.averageDuration = totalTests > 0 ? totalTime / totalTests : 0;
            this.protocols = Collections.unmodifiableList(protocolsList);
            this.engines = Collections.unmodifiableList(enginesList);
        }
        
        public double getSuccessRate() {
            return totalTests > 0 ? (double) successfulTests / totalTests * 100 : 0;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 批量测试摘要\n");
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
            sb.append("总测试数: ").append(totalTests).append("\n");
            sb.append("成功: ").append(successfulTests).append(" (").append(String.format("%.1f", getSuccessRate())).append("%)\n");
            sb.append("失败: ").append(failedTests).append("\n");
            sb.append("总耗时: ").append(totalDuration).append(" ms\n");
            sb.append("平均耗时: ").append(averageDuration).append(" ms\n");
            sb.append("协议类型: ").append(protocols).append("\n");
            sb.append("使用引擎: ").append(engines).append("\n");
            return sb.toString();
        }
    }
    
    /**
     * 执行批量测试
     * 
     * @param factory HTTP 客户端工厂
     * @param testCases 测试用例列表
     * @param callback 结果回调
     * @param timeoutMs 总超时时间（毫秒）
     */
    public static void runBatchTest(HttpClientFactory factory, 
                                   List<TestCase> testCases, 
                                   BatchTestCallback callback,
                                   long timeoutMs) {
        if (testCases == null || testCases.isEmpty()) {
            callback.onAllTestsComplete(Collections.emptyList(), 
                new TestSummary(Collections.emptyList()));
            return;
        }
        
        Log.d(TAG, "开始批量测试，共 " + testCases.size() + " 个测试用例");
        
        long startTime = System.currentTimeMillis();
        List<TestResult> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(testCases.size());
        AtomicInteger completedCount = new AtomicInteger(0);
        
        // 为每种引擎创建客户端
        List<HttpClient> clients = new ArrayList<>();
        try {
            for (TestCase testCase : testCases) {
                try {
                    HttpClient client = factory.createClient(testCase.engineType);
                    
                    // 执行单个测试
                    client.enqueue(testCase.request, response -> {
                        try {
                            TestResult result = new TestResult(testCase, response);
                            results.add(result);
                            
                            // 通知单个测试完成
                            callback.onTestComplete(testCase.index, testCase, response);
                            
                            // 更新进度
                            int completed = completedCount.incrementAndGet();
                            callback.onProgress(completed, testCases.size());
                            
                            Log.d(TAG, String.format("测试完成 [%d/%d]: %s -> %s (HTTP %d, %s, %dms)",
                                completed, testCases.size(), testCase.name, 
                                response.isSuccessful() ? "成功" : "失败",
                                response.code, response.protocol, response.durationMs));
                            
                        } finally {
                            latch.countDown();
                        }
                    });
                    
                    clients.add(client);
                    
                } catch (Exception e) {
                    Log.e(TAG, "创建客户端失败: " + testCase.engineType, e);
                    // 创建错误响应
                    HttpResponse errorResponse = new HttpResponse(
                        0, null, Collections.emptyMap(), null, 0, 
                        "Failed to create client: " + e.getMessage(), 0, testCase.request.url
                    );
                    TestResult errorResult = new TestResult(testCase, errorResponse);
                    results.add(errorResult);
                    callback.onTestComplete(testCase.index, testCase, errorResponse);
                    latch.countDown();
                }
            }
            
            // 等待所有测试完成或超时
            boolean finished = false;
            try {
                finished = latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.w(TAG, "批量测试被中断", e);
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
            
            if (!finished) {
                Log.w(TAG, "批量测试超时，部分测试可能未完成");
            }
            
            long totalTime = System.currentTimeMillis() - startTime;
            
            // 创建摘要并回调
            TestSummary summary = new TestSummary(results);
            Log.d(TAG, "批量测试完成，总耗时: " + totalTime + "ms");
            Log.d(TAG, summary.toString());
            
            callback.onAllTestsComplete(results, summary);
            
        } finally {
            // 清理资源
            for (HttpClient client : clients) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    Log.w(TAG, "关闭客户端失败", e);
                }
            }
        }
    }
    
    /**
     * 创建多引擎测试用例（同一URL，多个引擎）
     */
    public static List<TestCase> createMultiEngineTests(String name, String url, HttpEngineType... engines) {
        return createMultiEngineTests(name, new HttpRequest.Builder().url(url).build(), engines);
    }
    
    /**
     * 创建多引擎测试用例（同一请求，多个引擎）
     */
    public static List<TestCase> createMultiEngineTests(String name, HttpRequest request, HttpEngineType... engines) {
        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < engines.length; i++) {
            HttpEngineType engine = engines[i];
            testCases.add(new TestCase(
                name + " [" + engine + "]", 
                request, 
                engine, 
                i
            ));
        }
        return testCases;
    }
    
    /**
     * 创建多URL测试用例（多个URL，同一引擎）
     */
    public static List<TestCase> createMultiUrlTests(HttpEngineType engine, String... urls) {
        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            HttpRequest request = new HttpRequest.Builder().url(url).build();
            testCases.add(new TestCase(
                "URL-" + (i + 1) + " [" + engine + "]", 
                request, 
                engine, 
                i
            ));
        }
        return testCases;
    }
    
    /**
     * 创建 HTTP/3 强制测试用例
     */
    public static List<TestCase> createHttp3Tests(String... urls) {
        List<TestCase> testCases = new ArrayList<>();
        HttpProfile h3Profile = HttpProfile.FORCE_HTTP3;
        
        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            HttpRequest request = new HttpRequest.Builder()
                .url(url)
                .profile(h3Profile)
                .autoQuicHints()
                .build();
                
            testCases.add(new TestCase(
                "HTTP/3-" + (i + 1), 
                request, 
                HttpEngineType.CRONET, // HTTP/3 需要 Cronet
                i
            ));
        }
        return testCases;
    }
    
    /**
     * 创建综合测试套件
     */
    public static List<TestCase> createComprehensiveTests() {
        List<TestCase> allTests = new ArrayList<>();
        
        // HTTP/3 测试站点
        String[] http3Sites = {
            "https://quic.nginx.org/",
            "https://cloudflare-quic.com/",
            "https://http3check.net/"
        };
        
        // 普通 HTTPS 站点
        String[] httpsSites = {
            "https://www.google.com/",
            "https://api.github.com/users/octocat",
            "https://httpbin.org/get"
        };
        
        int index = 0;
        
        // 添加 HTTP/3 测试
        for (String url : http3Sites) {
            allTests.addAll(createMultiEngineTests("HTTP/3-" + (index + 1), 
                new HttpRequest.Builder()
                    .url(url)
                    .profile(HttpProfile.FORCE_HTTP3)
                    .autoQuicHints()
                    .build(), 
                HttpEngineType.CRONET));
            index++;
        }
        
        // 添加多引擎对比测试
        for (String url : httpsSites) {
            allTests.addAll(createMultiEngineTests("HTTPS-" + (index + 1), url, 
                HttpEngineType.CRONET, HttpEngineType.OKHTTP));
            index++;
        }
        
        // 重新分配索引
        for (int i = 0; i < allTests.size(); i++) {
            TestCase oldCase = allTests.get(i);
            allTests.set(i, new TestCase(oldCase.name, oldCase.request, oldCase.engineType, i));
        }
        
        return allTests;
    }
}
