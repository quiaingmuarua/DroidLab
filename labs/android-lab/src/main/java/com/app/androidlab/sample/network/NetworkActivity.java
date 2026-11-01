package com.app.androidlab.sample.network;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


import com.app.androidlab.sample.R;
import com.app.androidlab.sample.base.BaseProtectActivity;
import com.app.androidlab.sample.base.Item;
import com.app.androidlab.sample.base.ItemAdapter;
import com.app.androidlab.sample.base.Status;
import com.app.androidlab.sample.network.unified.*;

import java.util.ArrayList;

public class NetworkActivity extends BaseProtectActivity {
    private static final String TAG = "NetworkActivity";

    // UI 组件
    private Spinner urlSpinner;
    private TextView urlDescription;
    private RecyclerView networkLibraryRecyclerView;
    private Button testButton;
    private Button clearButton;

    // 数据
    private ArrayList<TestUrl> testUrls;
    private ArrayList<Item> networkEngines;
    private ItemAdapter networkEngineAdapter;
    
    // 选择状态
    private TestUrl selectedUrl;
    private HttpEngineType selectedEngine;
    private int selectedEnginePosition = -1;

    // 统一HTTP客户端
    private HttpClientFactory httpClientFactory;
    private HttpClient currentHttpClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network);
        
        initViews();
        initData();
        setupListeners();
    }

    private void initViews() {
        urlSpinner = findViewById(R.id.url_spinner);
        urlDescription = findViewById(R.id.url_description);
        networkLibraryRecyclerView = findViewById(R.id.network_library_recycler_view);
        testButton = findViewById(R.id.test_button);
        clearButton = findViewById(R.id.clear_button);
        
        networkLibraryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private void initData() {
        // 先初始化HTTP客户端工厂
        httpClientFactory = new HttpClientFactory(this);
        
        // 初始化测试URL数据
        initTestUrls();
        setupUrlSpinner();
        
        // 初始化网络引擎数据（需要使用httpClientFactory）
        initNetworkEngines();
        setupNetworkEngineRecyclerView();
    }

    private void initTestUrls() {
        testUrls = new ArrayList<>();
        testUrls.add(new TestUrl("HTTP/3 测试站", "https://quic.nginx.org/", "Nginx 官方 QUIC/HTTP3 测试站点，支持最新的 HTTP/3 协议", "HTTP/3 + QUIC"));
        testUrls.add(new TestUrl("Cloudflare HTTP/3", "https://cloudflare-quic.com/", "Cloudflare 提供的 HTTP/3 演示站点，展示 QUIC 协议性能", "HTTP/3 + QUIC"));
        testUrls.add(new TestUrl("HTTP/3 检测", "https://http3check.net/", "专门用于检测 HTTP/3 支持的测试站点", "HTTP/3 + QUIC"));
        testUrls.add(new TestUrl("TLS 测试站", "https://tls.peet.ws/", "TLS 协议测试站点，支持多种 TLS 版本", "HTTP/2 + TLS"));
        testUrls.add(new TestUrl("Google 首页", "https://www.google.com/", "Google 主页，支持 HTTP/2 和现代 Web 标准", "HTTP/2"));
        testUrls.add(new TestUrl("GitHub API", "https://api.github.com/users/octocat", "GitHub 用户 API，返回 JSON 格式数据", "HTTP/2"));
        testUrls.add(new TestUrl("自定义URL", "https://httpbin.org/get", "HTTPBin 测试服务，返回请求信息", "HTTP/1.1"));
    }

    private void setupUrlSpinner() {
        ArrayAdapter<TestUrl> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, testUrls);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        urlSpinner.setAdapter(adapter);
        
        // 设置初始选择为第一个项目
        if (!testUrls.isEmpty()) {
            urlSpinner.setSelection(0);
            selectedUrl = testUrls.get(0);
            urlDescription.setText(selectedUrl.getDetailedDescription());
        }
    }

    private void initNetworkEngines() {
        networkEngines = new ArrayList<>();
        
        // 添加可用的引擎
        for (HttpEngineType engineType : HttpEngineType.values()) {
            HttpClientFactory.EngineInfo info = httpClientFactory.getEngineInfo(engineType);
            Status status = info.available ? Status.TODOCheck : Status.Disable;
            networkEngines.add(new Item(info.name, info.description + " - " + info.features, status));
        }
    }

    private void setupNetworkEngineRecyclerView() {
        networkEngineAdapter = new ItemAdapter(networkEngines);
        networkLibraryRecyclerView.setAdapter(networkEngineAdapter);
    }

    private void setupListeners() {
        // URL选择监听
        urlSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedUrl = testUrls.get(position);
                urlDescription.setText(selectedUrl.getDetailedDescription());
                updateTestButtonState();
                Log.d(TAG, "选择URL: " + selectedUrl.getName() + " -> " + selectedUrl.getUrl());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                selectedUrl = null;
                urlDescription.setText("请选择一个测试URL");
                updateTestButtonState();
            }
        });

        // 网络引擎选择监听
        networkEngineAdapter.setOnItemClickListener(position -> {
            // 检查引擎是否可用
            if (networkEngines.get(position).getStatus() == Status.Disable) {
                showAlertDialog("引擎不可用", "该网络引擎不可用，请检查相关依赖库是否已添加");
                return;
            }
            
            // 清除之前的选择
            if (selectedEnginePosition != -1) {
                networkEngines.get(selectedEnginePosition).setStatus(Status.TODOCheck);
            }
            
            // 设置新选择
            selectedEnginePosition = position;
            selectedEngine = HttpEngineType.values()[position];
            networkEngines.get(position).setStatus(Status.Execute);
            networkEngineAdapter.notifyDataSetChanged();
            
            updateTestButtonState();
            Log.d(TAG, "选择网络引擎: " + selectedEngine);
        });

        // 测试按钮监听
        testButton.setOnClickListener(v -> {
            if (selectedUrl != null && selectedEngine != null) {
                performNetworkTest();
            }
        });

        // 清除按钮监听
        clearButton.setOnClickListener(v -> {
            clearSelections();
        });
    }

    private void updateTestButtonState() {
        boolean canTest = selectedUrl != null && selectedEngine != null;
        testButton.setEnabled(canTest);
        
        if (canTest) {
            testButton.setText("🚀 测试 " + selectedEngine);
        } else {
            testButton.setText("🚀 请选择 URL 和网络引擎");
        }
    }

    private void clearSelections() {
        // 重置URL选择到第一项
        if (!testUrls.isEmpty()) {
            urlSpinner.setSelection(0);
            selectedUrl = testUrls.get(0);
            urlDescription.setText(selectedUrl.getDetailedDescription());
        } else {
            selectedUrl = null;
            urlDescription.setText("请选择一个测试URL");
        }
        
        // 清除网络引擎选择
        if (selectedEnginePosition != -1) {
            networkEngines.get(selectedEnginePosition).setStatus(Status.TODOCheck);
            networkEngineAdapter.notifyDataSetChanged();
        }
        
        selectedEngine = null;
        selectedEnginePosition = -1;
        
        updateTestButtonState();
        Log.d(TAG, "已清除网络引擎选择，URL重置为默认");
    }

    private void performNetworkTest() {
        if (selectedUrl == null || selectedEngine == null) {
            showAlertDialog("错误", "请先选择URL和网络引擎");
            return;
        }

        String testTitle = selectedEngine + " @ " + selectedUrl.getName();
        String testUrl = selectedUrl.getUrl();
        
        Log.d(TAG, "开始网络测试: " + testTitle);
        Log.d(TAG, "测试URL: " + testUrl);
        
        // 显示开始测试的提示
        showAlertDialog("🚀 测试开始", 
            "正在使用 " + selectedEngine + " 请求:\n" +
            selectedUrl.getName() + "\n\n" +
            "URL: " + testUrl + "\n" +
            "协议: " + selectedUrl.getProtocol() + "\n\n" +
            "⏳ 请稍候，测试结果将自动显示...");

        // 使用统一HTTP客户端执行测试
        performUnifiedNetworkTest(testTitle, testUrl);
    }

    private void performUnifiedNetworkTest(String title, String url) {
        try {
            // 释放之前的客户端
            if (currentHttpClient != null) {
                currentHttpClient.shutdown();
            }
            
            // 创建新的HTTP客户端
            currentHttpClient = httpClientFactory.createClient(selectedEngine);
            
            // 构建请求
            HttpRequest.Builder requestBuilder = new HttpRequest.Builder()
                .url(url)
                .method("GET")
                .timeouts(10_000, 20_000)
                .maxBodyBytes(256 * 1024); // 256KB
            
            // 根据URL类型选择Profile
            if (isHttp3TestUrl(url)) {
                // 强制HTTP/3测试
                requestBuilder.profile(HttpProfile.FORCE_HTTP3)
                             .autoQuicHints();
                Log.d(TAG, "使用强制HTTP/3模式测试: " + url);
            } else {
                // 默认Profile
                requestBuilder.profile(HttpProfile.DEFAULT);
            }
            
            HttpRequest request = requestBuilder.build();
            
            // 执行请求
            currentHttpClient.enqueue(request, response -> {
                runOnUiThread(() -> {
                    String formattedResult = response.getFormattedResult();
                    Log.d(TAG, "网络测试完成: " + title + 
                              " -> " + (response.isSuccessful() ? "成功" : "失败") + 
                              " (" + response.protocol + ", " + response.durationMs + "ms)");
                    showAlertDialog(NetworkActivity.this, title, formattedResult);
                });
            });
            
        } catch (Exception e) {
            Log.e(TAG, "网络测试失败", e);
            runOnUiThread(() -> {
                showAlertDialog(this, title, "❌ 网络测试失败\n\n错误信息: " + e.getMessage());
            });
        }
    }
    
    private boolean isHttp3TestUrl(String url) {
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("quic") || 
               lowerUrl.contains("http3") ||
               lowerUrl.contains("h3");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentHttpClient != null) {
            currentHttpClient.shutdown();
        }
    }



}