package com.app.androidlab.sample.network.okhttp;

public class AsyncTaskResult {
    private String result;
    private String url;

    private String title;

    public AsyncTaskResult(String result, String url) {
        this.result = result;
        this.url = url;
    }

    public AsyncTaskResult(String result, String url,String title) {
        this.result = result;
        this.url = url;
        this.title=title;
    }

    public String getResult() {
        return result;
    }


    public String getTitle() {
        return title;
    }
    public String getUrl() {
        return url;
    }
}

