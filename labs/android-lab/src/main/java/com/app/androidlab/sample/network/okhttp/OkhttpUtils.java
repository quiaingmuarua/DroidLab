package com.app.androidlab.sample.network.okhttp;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class OkhttpUtils {

    private static OkHttpClient client = new OkHttpClient();

    public static String makeHttpsRequest(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .build();

            Response response = client.newCall(request).execute();
            return String.valueOf(response.code());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
