package com.bombayashi.reporteciudadano.network;

import android.content.Context;

import com.bombayashi.reporteciudadano.util.TokenManager;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static final String BASE_URL = "https://api.manuelmv.net/api/";
    private static ApiService instance;
    private static AuthInterceptor authInterceptor;

    public static void init(Context context) {
        if (instance != null) return;

        authInterceptor = new AuthInterceptor(TokenManager.getInstance(context));

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(logging)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();

        instance = retrofit.create(ApiService.class);
    }

    public static ApiService getInstance() {
        if (instance == null) throw new IllegalStateException("ApiClient not initialized. Call ApiClient.init(context) first.");
        return instance;
    }

    /** Register a callback invoked on any 401 response (runs on OkHttp thread — post to main if needed). */
    public static void setOnUnauthorized(Runnable callback) {
        if (authInterceptor != null) authInterceptor.setOnUnauthorized(callback);
    }
}
