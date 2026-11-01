package com.app.androidlab.sample.network;

public class TestUrl {
    private String name;
    private String url;
    private String description;
    private String protocol;

    public TestUrl(String name, String url, String description, String protocol) {
        this.name = name;
        this.url = url;
        this.description = description;
        this.protocol = protocol;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public String getProtocol() {
        return protocol;
    }

    @Override
    public String toString() {
        return name + " (" + protocol + ")";
    }

    public String getDetailedDescription() {
        return description + "\n协议: " + protocol + "\nURL: " + url;
    }
}
