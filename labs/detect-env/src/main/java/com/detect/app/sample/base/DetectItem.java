package com.detect.app.sample.base;


import android.content.Intent;

import java.util.function.Supplier;

// DetectItem.java
public class DetectItem {
    private String title;
    private String description;
    private Status status;

    //activity跳转
    private Supplier<Intent> intentSupplier;

    // item 结果
    private Supplier<String> resultSupplier; // New field for result supplier


    // 私有构造方法，防止直接实例化
    public DetectItem(String title, String description, Status status) {
        this.title = title;
        this.description = description;
        this.status = status;
    }

    // 静态工厂方法
    public static DetectItem withIntent(String title, String description, Status status, Supplier<Intent> intentSupplier) {
        DetectItem item = new DetectItem(title, description, status);
        item.intentSupplier = intentSupplier;
        return item;
    }

    public static DetectItem withResult(String title, String description, Status status, Supplier<String> resultSupplier) {
        DetectItem item = new DetectItem(title, description, status);
        item.resultSupplier = resultSupplier;
        return item;
    }

    public Supplier<Intent> getIntentSupplier() {
        return intentSupplier;
    }

    public Supplier<String> getResultSupplier() {
        return resultSupplier;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Status getStatus() {
        return status;
    }
}
