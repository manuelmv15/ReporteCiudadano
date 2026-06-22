package com.bombayashi.reporteciudadano.network;

import com.bombayashi.reporteciudadano.util.TokenManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private final TokenManager tokenManager;
    private volatile Runnable onUnauthorized;

    public AuthInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    public void setOnUnauthorized(Runnable callback) {
        this.onUnauthorized = callback;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        String token = tokenManager.getToken();

        Request.Builder builder = chain.request().newBuilder();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }

        Response response = chain.proceed(builder.build());

        if (response.code() == 401) {
            Runnable cb = onUnauthorized;
            if (cb != null) cb.run();
        }

        return response;
    }
}
