package com.bombayashi.reporteciudadano.network;

import com.bombayashi.reporteciudadano.model.AuthResponse;
import com.bombayashi.reporteciudadano.model.AvatarUploadResponse;
import com.bombayashi.reporteciudadano.model.CreateReportResponse;
import com.bombayashi.reporteciudadano.model.GoogleLoginRequest;
import com.bombayashi.reporteciudadano.model.LoginRequest;
import com.bombayashi.reporteciudadano.model.MyVotesResponse;
import com.bombayashi.reporteciudadano.model.RegisterRequest;
import com.bombayashi.reporteciudadano.model.ReportDetailResponse;
import com.bombayashi.reporteciudadano.model.ReportRequest;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.model.ReportStreamResponse;
import com.bombayashi.reporteciudadano.model.SimpleResponse;
import com.bombayashi.reporteciudadano.model.UpdateProfileRequest;
import com.bombayashi.reporteciudadano.model.VoteRequest;
import com.bombayashi.reporteciudadano.model.VoteResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.PUT;
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

    @GET("reports/stream/changes")
    Call<ReportStreamResponse> getReportsStreamChanges(
            @Query("since") String since,
            @Query("limit") int limit
    );

    @GET("reports/{id}")
    Call<ReportDetailResponse> getReportDetail(
            @Path("id") int reportId
    );

    @POST("reports")
    Call<CreateReportResponse> createReport(
            @Header("Authorization") String token,
            @Body ReportRequest request
    );

    @POST("reports/{id}/votes")
    Call<VoteResponse> submitVote(
            @Path("id") int reportId,
            @Header("Authorization") String token,
            @Body VoteRequest request
    );

    @Multipart
    @PUT("reports/{id}")
    Call<CreateReportResponse> updateReport(
            @Path("id") int reportId,
            @Header("Authorization") String token,
            @Part("description") RequestBody description,
            @Part MultipartBody.Part photo
    );

    @DELETE("reports/{id}")
    Call<SimpleResponse> deleteReport(
            @Path("id") int reportId,
            @Header("Authorization") String token
    );

    @DELETE("reports/{id}/votes/{type}")
    Call<VoteResponse> deleteVote(
            @Path("id") int reportId,
            @Path("type") String voteType,
            @Header("Authorization") String token
    );

    @GET("me")
    Call<AuthResponse> getMe(
            @Header("Authorization") String token
    );

    @PUT("me")
    Call<AuthResponse> updateProfile(
            @Header("Authorization") String token,
            @Body UpdateProfileRequest request
    );

    @Multipart
    @POST("me/avatar")
    Call<AvatarUploadResponse> uploadAvatar(
            @Header("Authorization") String token,
            @Part MultipartBody.Part avatar
    );

    @GET("me/reports")
    Call<ReportResponse> getMyReports(
            @Header("Authorization") String token,
            @Query("status") String status,
            @Query("per_page") int perPage
    );

    @GET("me/votes")
    Call<MyVotesResponse> getMyVotes(
            @Header("Authorization") String token,
            @Query("per_page") int perPage
    );
}
