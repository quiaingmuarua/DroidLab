package com.app.androidlab.sample.network.unified;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * 统一HTTP客户端使用示例
 * 演示如何使用新的统一网络架构
 */
public class UnifiedHttpExample {
    private static final String TAG = "UnifiedHttpExample";
    
    private final Context context;
    private final HttpClientFactory factory;
    
    public UnifiedHttpExample(Context context) {
        this.context = context;
        this.factory = new HttpClientFactory(context);
    }
    
    /**
     * 基本GET请求示例
     */
    public void basicGetRequest() {
        Log.d(TAG, "=== 基本GET请求示例 ===");
        
        // 创建HTTP客户端（自动选择引擎）
        HttpClient client = factory.createClient(HttpEngineType.AUTO);
        
        // 构建请求
        HttpRequest request = new HttpRequest.Builder()
            .url("https://httpbin.org/get")
            .method("GET")
            .header("User-Agent", "UnifiedHttpClient/1.0")
            .timeouts(10_000, 20_000)
            .build();
        
        // 执行请求
        client.enqueue(request, response -> {
            Log.d(TAG, "响应结果: " + response.getFormattedResult());
        });
    }
    
    /**
     * POST请求示例
     */
    public void postRequest() {
        Log.d(TAG, "=== POST请求示例 ===");
        
        HttpClient client = factory.createClient(HttpEngineType.OKHTTP);
        
        String jsonBody = "{\"name\":\"test\",\"value\":\"123\"}";
        
        HttpRequest request = new HttpRequest.Builder()
            .url("https://httpbin.org/post")
            .method("POST")
            .header("Content-Type", "application/json")
            .body(jsonBody)
            .build();
        
        client.enqueue(request, response -> {
            Log.d(TAG, "POST响应: " + response.code + " " + response.protocol);
            Log.d(TAG, "响应体: " + response.getBodyAsString());
        });
    }
    
    /**
     * HTTP/3强制测试示例
     */
    public void forceHttp3Test() {
        Log.d(TAG, "=== HTTP/3强制测试示例 ===");
        
        HttpClient client = factory.createClient(HttpEngineType.CRONET);
        
        HttpRequest request = new HttpRequest.Builder()
            .url("https://quic.nginx.org/")
            .profile(HttpProfile.FORCE_HTTP3)
            .autoQuicHints()
            .header("User-Agent", "HTTP3TestClient/1.0")
            .build();
        
        client.enqueue(request, response -> {
            if (response.protocol != null && response.protocol.startsWith("h3")) {
                Log.d(TAG, "🎉 HTTP/3 测试成功！协议: " + response.protocol);
            } else {
                Log.w(TAG, "⚠️ HTTP/3 测试失败，实际协议: " + response.protocol);
            }
            Log.d(TAG, response.getFormattedResult());
        });
    }
    
    /**
     * 批量对比测试示例
     */
    public void batchComparisonTest() {
        Log.d(TAG, "=== 批量对比测试示例 ===");
        
        // 创建测试用例
        List<BatchTestUtils.TestCase> testCases = BatchTestUtils.createMultiEngineTests(
            "GitHub API测试", 
            "https://api.github.com/users/octocat",
            HttpEngineType.CRONET, 
            HttpEngineType.OKHTTP
        );
        
        // 执行批量测试
        BatchTestUtils.runBatchTest(factory, testCases, new BatchTestUtils.BatchTestCallback() {
            @Override
            public void onTestComplete(int index, BatchTestUtils.TestCase testCase, HttpResponse response) {
                Log.d(TAG, String.format("测试完成 [%d]: %s -> %s (%s, %dms)", 
                    index, testCase.name, 
                    response.isSuccessful() ? "成功" : "失败",
                    response.protocol, response.durationMs));
            }
            
            @Override
            public void onAllTestsComplete(List<BatchTestUtils.TestResult> results, BatchTestUtils.TestSummary summary) {
                Log.d(TAG, "批量测试完成:");
                Log.d(TAG, summary.toString());
                
                // 输出详细对比
                for (BatchTestUtils.TestResult result : results) {
                    Log.d(TAG, String.format("- %s: %s (HTTP %d, %s, %dms)", 
                        result.testCase.engineType,
                        result.isSuccessful() ? "✅" : "❌",
                        result.response.code,
                        result.response.protocol,
                        result.response.durationMs));
                }
            }
        }, 30_000); // 30秒超时
    }
    
    /**
     * 综合性能测试
     */
    public void comprehensivePerformanceTest() {
        Log.d(TAG, "=== 综合性能测试 ===");
        
        List<BatchTestUtils.TestCase> testCases = BatchTestUtils.createComprehensiveTests();
        
        BatchTestUtils.runBatchTest(factory, testCases, new BatchTestUtils.BatchTestCallback() {
            @Override
            public void onTestComplete(int index, BatchTestUtils.TestCase testCase, HttpResponse response) {
                // 实时显示进度
            }
            
            @Override
            public void onProgress(int completed, int total) {
                Log.d(TAG, String.format("测试进度: %d/%d (%.1f%%)", 
                    completed, total, (double)completed / total * 100));
            }
            
            @Override
            public void onAllTestsComplete(List<BatchTestUtils.TestResult> results, BatchTestUtils.TestSummary summary) {
                Log.d(TAG, "📊 综合性能测试报告:");
                Log.d(TAG, summary.toString());
                
                // 统计协议分布
                long http3Count = results.stream()
                    .filter(r -> r.response.protocol != null && r.response.protocol.startsWith("h3"))
                    .count();
                long http2Count = results.stream()
                    .filter(r -> r.response.protocol != null && r.response.protocol.startsWith("h2"))
                    .count();
                
                Log.d(TAG, String.format("协议分布: HTTP/3=%d, HTTP/2=%d, 其他=%d", 
                    http3Count, http2Count, results.size() - http3Count - http2Count));
                
                // 引擎性能对比
                for (HttpEngineType engine : summary.engines) {
                    List<BatchTestUtils.TestResult> engineResults = results.stream()
                        .filter(r -> r.testCase.engineType == engine)
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (!engineResults.isEmpty()) {
                        double avgTime = engineResults.stream()
                            .mapToLong(r -> r.response.durationMs)
                            .average()
                            .orElse(0.0);
                        double successRate = engineResults.stream()
                            .mapToInt(r -> r.isSuccessful() ? 1 : 0)
                            .average()
                            .orElse(0.0) * 100;
                        
                        Log.d(TAG, String.format("%s: 平均耗时=%.1fms, 成功率=%.1f%%", 
                            engine, avgTime, successRate));
                    }
                }
            }
        }, 60_000); // 60秒超时
    }
    
    /**
     * 获取引擎信息示例
     */
    public void showEngineInfo() {
        Log.d(TAG, "=== 引擎信息 ===");
        
        for (HttpEngineType engineType : HttpEngineType.values()) {
            HttpClientFactory.EngineInfo info = factory.getEngineInfo(engineType);
            Log.d(TAG, info.toString());
        }
        
        Log.d(TAG, "推荐引擎: " + factory.getRecommendedEngine());
    }
    
    /**
     * NetLog测试示例（仅Cronet）
     */
    public void netLogTest(String netLogDir) {
        Log.d(TAG, "=== NetLog测试示例 ===");
        
        if (!factory.isEngineAvailable(HttpEngineType.CRONET)) {
            Log.w(TAG, "Cronet不可用，跳过NetLog测试");
            return;
        }
        
        HttpClient client = factory.createClient(HttpEngineType.CRONET);
        
        HttpProfile netLogProfile = HttpProfile.h3Test(netLogDir, 1024 * 1024); // 1MB NetLog
        
        HttpRequest request = new HttpRequest.Builder()
            .url("https://quic.nginx.org/")
            .profile(netLogProfile)
            .autoQuicHints()
            .build();
        
        client.enqueue(request, response -> {
            Log.d(TAG, "NetLog测试完成: " + response.protocol);
            Log.d(TAG, "NetLog文件应保存在: " + netLogDir);
        });
    }
}
