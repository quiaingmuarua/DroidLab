package com.app.androidlab.sample.network.retrofit;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {

    //https://tls.peet.ws/api/all
    @GET("api/all")
    Call<String> fetchHtmlContent();
}
