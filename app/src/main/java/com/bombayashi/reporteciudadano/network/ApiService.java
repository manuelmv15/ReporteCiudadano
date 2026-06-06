package com.bombayashi.reporteciudadano.network;

import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.LoginRequest;
import com.bombayashi.reporteciudadano.model.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    @POST("auth/google")
    Call<AuthResponse> googleLogin(@Body GoogleLoginRequest request);

    @POST("login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("logout")
    Call<AuthResponse> logout(@Header("Authorization") String bearerToken);
}
