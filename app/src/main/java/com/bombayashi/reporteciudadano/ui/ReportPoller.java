package com.bombayashi.reporteciudadano.ui;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.model.ReportStreamResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

class ReportPoller {

    interface Callback {
        void onReportUpdated(ReportResponse.ReportData report);
        boolean isActive();
    }

    private static final long INTERVAL_MS = 30_000;
    private static final int MAX_REPORTS = 200;

    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastSyncTimestamp;
    private boolean running = false;

    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            poll();
            handler.postDelayed(this, INTERVAL_MS);
        }
    };

    ReportPoller(Callback callback) {
        this.callback = callback;
    }

    void start() {
        if (running) return;
        running = true;
        handler.postDelayed(runnable, INTERVAL_MS);
    }

    void stop() {
        running = false;
        handler.removeCallbacks(runnable);
    }

    void setTimestamp(String ts) {
        lastSyncTimestamp = ts;
    }

    String getTimestamp() {
        return lastSyncTimestamp;
    }

    void pollNow() {
        poll();
    }

    private void poll() {
        if (!callback.isActive() || lastSyncTimestamp == null) return;

        android.util.Log.d("ReportPoller", "🔁 Polling since=" + lastSyncTimestamp);

        ApiClient.getInstance().getReportsStreamChanges(lastSyncTimestamp, MAX_REPORTS)
                .enqueue(new retrofit2.Callback<ReportStreamResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ReportStreamResponse> call,
                                           @NonNull Response<ReportStreamResponse> response) {
                        if (!callback.isActive()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            ReportStreamResponse body = response.body();
                            List<ReportResponse.ReportData> reports = body.getReports();
                            if (reports != null) {
                                for (ReportResponse.ReportData r : reports) callback.onReportUpdated(r);
                            }
                            if (body.getTimestamp() != null) lastSyncTimestamp = body.getTimestamp();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ReportStreamResponse> call, @NonNull Throwable t) {
                        android.util.Log.e("ReportPoller", "Error de red: " + t.getMessage());
                    }
                });
    }

    static String nowIso() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
