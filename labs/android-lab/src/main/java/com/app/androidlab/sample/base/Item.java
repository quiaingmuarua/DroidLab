package com.app.androidlab.sample.base;

import java.util.function.Supplier;

// Item.java
public class Item {
    private String title;
    private String info;
    private Status status;

    // item 结果
    private Supplier<String> resultSupplier; // New field for result supplier

    public Item(String title, String info, Status status) {
        this.title = title;
        this.info = info;
        this.status = status;
    }

    public Item(String title, String info) {
        this.title = title;
        this.info = info;
        this.status =  Status.Execute;
    }

    public Item(String title, Status status, Supplier<String> resultSupplier) {
        this.title = title;
        this.status = status;
        this.resultSupplier = resultSupplier;
    }

    public String getTitle() {
        return title;
    }

    public String getInfo() {
        return info;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public void setInfo(String info) {
        this.info = info;
    }



    public Supplier<String> getResultSupplier() {
        return resultSupplier;
    }
}
