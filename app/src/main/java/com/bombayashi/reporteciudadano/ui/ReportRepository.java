package com.bombayashi.reporteciudadano.ui;

import androidx.annotation.NonNull;

import com.bombayashi.reporteciudadano.db.AppDatabase;
import com.bombayashi.reporteciudadano.db.ReportCacheEntity;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;

import com.google.gson.Gson;

import java.util.List;
import java.util.concurrent.ExecutorService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

class ReportRepository {

    interface BoundsCallback {
        void onSuccess(List<ReportResponse.ReportData> reports);
        void onError(String msg);
    }

    private static final Gson gson = new Gson();

    void fetchByBounds(double latMin, double latMax, double lngMin, double lngMax,
                       int pageSize, BoundsCallback cb) {
        ApiClient.getInstance()
                .getReportsByBounds(latMin, latMax, lngMin, lngMax, pageSize)
                .enqueue(new Callback<ReportResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ReportResponse> call,
                                           @NonNull Response<ReportResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            cb.onSuccess(response.body().getData());
                        } else {
                            cb.onError("HTTP " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ReportResponse> call, @NonNull Throwable t) {
                        cb.onError(t.getMessage());
                    }
                });
    }

    List<ReportCacheEntity> getCached(AppDatabase db) {
        return db.reportCacheDao().getAll();
    }

    void saveToCache(AppDatabase db, ExecutorService executor, ReportResponse.ReportData report) {
        executor.execute(() -> db.reportCacheDao().upsert(new ReportCacheEntity(
                String.valueOf(report.getId()),
                report.getLatitude(),
                report.getLongitude(),
                report.getStatus(),
                gson.toJson(report),
                System.currentTimeMillis()
        )));
    }
}
