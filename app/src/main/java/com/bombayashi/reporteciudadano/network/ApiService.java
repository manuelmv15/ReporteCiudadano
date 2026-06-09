package com.bombayashi.reporteciudadano.network;

import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.CreateReportResponse;
import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.LoginRequest;
import com.bombayashi.reporteciudadano.model.RegisterRequest;
import com.bombayashi.reporteciudadano.model.ReportRequest;
import com.bombayashi.reporteciudadano.model.ReportResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {
    @POST("auth/google")
    Call<AuthResponse> googleLogin(@Body GoogleLoginRequest request);

    @POST("login")
    Call<AuthResponse> login(@Body LoginRequest request);

    @POST("register")
    Call<AuthResponse> register(@Body RegisterRequest request);

    @POST("logout")
    Call<AuthResponse> logout(@Header("Authorization") String bearerToken);

    @GET("reports")
    Call<ReportResponse> getReports(
            @Query("status") String status,
            @Query("per_page") int perPage
    );

    @POST("reports")
    Call<CreateReportResponse> createReport(
            @Header("Authorization") String token,
            @Body ReportRequest request
    );
}
