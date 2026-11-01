# 统一HTTP客户端架构

一套"通用 + 可测试 + 可抓取 NetLog + 可切换引擎（Cronet/OkHttp）"的优化版本网络库。

## 🎯 核心特性

### 统一接口设计
- **HttpRequest**: 统一的请求对象，支持Builder模式
- **HttpResponse**: 统一的响应对象，自动处理编码解码
- **HttpClient**: 统一的客户端接口
- **HttpCallback**: 异步回调接口
- **HttpCall**: 支持取消操作

### 多引擎支持
- **AUTO**: 自动选择最佳引擎（优先Cronet，降级到OkHttp）
- **CRONET**: Google Cronet，支持HTTP/3、QUIC、NetLog
- **OKHTTP**: Square OkHttp，支持HTTP/2、连接池、拦截器

### Profile系统
- **DEFAULT**: 普通模式，允许协议回退
- **FORCE_HTTP3**: 强制HTTP/3模式，用于测试
- **自定义**: 支持NetLog配置

### 智能特性
- ✅ 动态QUIC hints（可从URL自动提取，也可外部传入）
- ✅ 正确处理ByteBuffer.flip()读取
- ✅ 按Content-Type charset自动解码
- ✅ 统一超时与最大响应体限制
- ✅ 批量并发测试工具

## 📁 架构组成

```
unified/
├── HttpModels.java          # 核心数据模型
├── HttpInterfaces.java      # 核心接口定义
├── CharsetUtils.java        # 字符集处理工具
├── HttpClientFactory.java   # 客户端工厂
├── CronetHttpClient.java    # Cronet引擎实现
├── OkHttpHttpClient.java    # OkHttp引擎实现
├── BatchTestUtils.java      # 批量测试工具
├── UnifiedHttpExample.java  # 使用示例
└── README.md               # 本文档
```

## 🚀 快速开始

### 1. 基本GET请求

```java
// 创建工厂
HttpClientFactory factory = new HttpClientFactory(context);

// 创建客户端（自动选择引擎）
HttpClient client = factory.createClient(HttpEngineType.AUTO);

// 构建请求
HttpRequest request = new HttpRequest.Builder()
    .url("https://api.github.com/users/octocat")
    .header("User-Agent", "MyApp/1.0")
    .timeouts(10_000, 20_000)
    .build();

// 执行请求
client.enqueue(request, response -> {
    if (response.isSuccessful()) {
        Log.d(TAG, "成功: " + response.getBodyAsString());
        Log.d(TAG, "协议: " + response.protocol);
        Log.d(TAG, "耗时: " + response.durationMs + "ms");
    } else {
        Log.e(TAG, "失败: " + response.error);
    }
});
```

### 2. POST请求

```java
String jsonBody = "{\"name\":\"test\",\"value\":\"123\"}";

HttpRequest request = new HttpRequest.Builder()
    .url("https://httpbin.org/post")
    .method("POST")
    .header("Content-Type", "application/json")
    .body(jsonBody)
    .build();

client.enqueue(request, response -> {
    Log.d(TAG, "POST响应: " + response.code + " " + response.protocol);
});
```

### 3. 强制HTTP/3测试

```java
HttpClient cronetClient = factory.createClient(HttpEngineType.CRONET);

HttpRequest request = new HttpRequest.Builder()
    .url("https://quic.nginx.org/")
    .profile(HttpProfile.FORCE_HTTP3)
    .autoQuicHints()  // 自动从URL提取QUIC hints
    .build();

cronetClient.enqueue(request, response -> {
    if (response.protocol != null && response.protocol.startsWith("h3")) {
        Log.d(TAG, "🎉 HTTP/3 测试成功！");
    } else {
        Log.w(TAG, "⚠️ HTTP/3 测试失败，实际协议: " + response.protocol);
    }
});
```

### 4. 批量对比测试

```java
// 创建测试用例（同一URL，多个引擎）
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
        Log.d(TAG, "批量测试摘要:");
        Log.d(TAG, summary.toString());
    }
}, 30_000); // 30秒超时
```

### 5. NetLog调试（仅Cronet）

```java
String netLogDir = context.getExternalFilesDir("netlogs").getAbsolutePath();
HttpProfile netLogProfile = HttpProfile.h3Test(netLogDir, 1024 * 1024); // 1MB

HttpRequest request = new HttpRequest.Builder()
    .url("https://quic.nginx.org/")
    .profile(netLogProfile)
    .autoQuicHints()
    .build();

client.enqueue(request, response -> {
    Log.d(TAG, "NetLog已保存到: " + netLogDir);
});
```

## 🔧 高级特性

### 自定义QUIC Hints

```java
List<QuicHint> hints = Arrays.asList(
    new QuicHint("quic.nginx.org", 443),
    new QuicHint("cloudflare-quic.com", 443),
    QuicHint.fromUrl("https://http3check.net/")  // 自动提取
);

HttpRequest request = new HttpRequest.Builder()
    .url("https://quic.nginx.org/")
    .profile(HttpProfile.FORCE_HTTP3)
    .quicHints(hints)
    .build();
```

### 自定义Profile

```java
HttpProfile customProfile = new HttpProfile(
    true,  // forceHttp3
    true,  // enableNetLog
    2048 * 1024,  // netLogMaxSizeBytes (2MB)
    "/path/to/netlog"  // netLogDir
);

HttpRequest request = new HttpRequest.Builder()
    .url("https://example.com/")
    .profile(customProfile)
    .build();
```

### 引擎信息查询

```java
HttpClientFactory factory = new HttpClientFactory(context);

// 检查引擎可用性
for (HttpEngineType engine : HttpEngineType.values()) {
    boolean available = factory.isEngineAvailable(engine);
    Log.d(TAG, engine + " 可用: " + available);
    
    if (available) {
        HttpClientFactory.EngineInfo info = factory.getEngineInfo(engine);
        Log.d(TAG, info.toString());
    }
}

// 获取推荐引擎
HttpEngineType recommended = factory.getRecommendedEngine();
Log.d(TAG, "推荐引擎: " + recommended);
```

## 📊 测试工具

### 创建综合测试套件

```java
// 自动创建包含HTTP/3和HTTPS站点的综合测试
List<BatchTestUtils.TestCase> testCases = BatchTestUtils.createComprehensiveTests();

BatchTestUtils.runBatchTest(factory, testCases, callback, 60_000);
```

### 创建HTTP/3专项测试

```java
List<BatchTestUtils.TestCase> h3Tests = BatchTestUtils.createHttp3Tests(
    "https://quic.nginx.org/",
    "https://cloudflare-quic.com/",
    "https://http3check.net/"
);

BatchTestUtils.runBatchTest(factory, h3Tests, callback, 30_000);
```

## 💡 最佳实践

### 1. 引擎选择建议
- **生产环境**: 使用`HttpEngineType.AUTO`，让系统自动选择最佳引擎
- **HTTP/3测试**: 使用`HttpEngineType.CRONET` + `HttpProfile.FORCE_HTTP3`
- **兼容性优先**: 使用`HttpEngineType.OKHTTP`

### 2. 超时配置
```java
HttpRequest request = new HttpRequest.Builder()
    .url("https://example.com/")
    .timeouts(5_000, 10_000)  // 连接5s，读取10s
    .maxBodyBytes(512 * 1024)  // 最大512KB响应体
    .build();
```

### 3. 错误处理
```java
client.enqueue(request, response -> {
    if (response.error != null) {
        // 网络错误或异常
        Log.e(TAG, "请求失败: " + response.error);
    } else if (!response.isSuccessful()) {
        // HTTP错误状态码
        Log.w(TAG, "HTTP错误: " + response.code);
    } else {
        // 成功
        String body = response.getBodyAsString();
        // 处理响应...
    }
});
```

### 4. 资源管理
```java
HttpClient client = factory.createClient(HttpEngineType.AUTO);

// 使用完毕后释放资源
try {
    // 执行网络请求...
} finally {
    client.shutdown();
}
```

## 🔍 调试技巧

### 启用详细日志
在LogCat中过滤以下标签：
- `CronetHttpClient`
- `OkHttpHttpClient`
- `HttpClientFactory`
- `BatchTestUtils`

### NetLog分析
1. 启用NetLog: `HttpProfile.h3Test(netLogDir, maxSize)`
2. 使用Chrome的 `chrome://net-internals/#import` 导入NetLog文件
3. 分析QUIC连接、HTTP/3协商等详细信息

### 协议验证
```java
client.enqueue(request, response -> {
    String protocol = response.protocol;
    if (protocol != null) {
        if (protocol.startsWith("h3")) {
            Log.d(TAG, "✅ 使用HTTP/3: " + protocol);
        } else if (protocol.startsWith("h2")) {
            Log.d(TAG, "✅ 使用HTTP/2: " + protocol);
        } else {
            Log.d(TAG, "ℹ️ 使用HTTP/1.1: " + protocol);
        }
    }
});
```

## 📦 依赖要求

### Cronet引擎
```gradle
implementation 'org.chromium.net:cronet-api:119.6045.31'
implementation 'com.google.android.gms:play-services-cronet:18.0.1'
```

### OkHttp引擎
```gradle
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

## 🤝 贡献指南

1. Fork项目
2. 创建特性分支
3. 提交更改
4. 推送到分支  
5. 创建Pull Request

## 📄 许可证

本项目采用MIT许可证 - 查看LICENSE文件了解详情。
