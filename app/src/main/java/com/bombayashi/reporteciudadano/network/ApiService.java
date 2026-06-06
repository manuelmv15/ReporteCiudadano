package com.bombayashi.reporteciudadano.network;

import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.AuthResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("auth/google")
    Call<AuthResponse> googleLogin(@Body GoogleLoginRequest request);
}
