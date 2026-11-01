package com.app.androidlab.sample;

import android.app.Application;

public class AndroidLabApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HttpServerManager.initServer(this);
    }
}
