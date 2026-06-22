package com.bombayashi.reporteciudadano.service;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import com.bombayashi.reporteciudadano.model.VoteRequest;
import com.bombayashi.reporteciudadano.model.VoteResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VoteActionReceiver extends BroadcastReceiver {

    private static final String TAG = "VoteActionReceiver";

    public static final String ACTION_VOTE_CONFIRM = "com.bombayashi.reporteciudadano.ACTION_VOTE_CONFIRM";
    public static final String ACTION_VOTE_RESOLVE = "com.bombayashi.reporteciudadano.ACTION_VOTE_RESOLVE";
    public static final String EXTRA_REPORT_ID = "report_id";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        int reportId = intent.getIntExtra(EXTRA_REPORT_ID, -1);
        double lat = intent.getDoubleExtra(EXTRA_LAT, 0.0);
        double lng = intent.getDoubleExtra(EXTRA_LNG, 0.0);

        if (reportId <= 0) {
            Log.w(TAG, "Invalid report_id in vote action");
            return;
        }

        String voteType = ACTION_VOTE_CONFIRM.equals(intent.getAction()) ? "confirm" : "resolve";

        // Dismiss the notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(reportId);

        String token = TokenManager.getInstance(context).getToken();
        if (token.isEmpty()) {
            Log.w(TAG, "No auth token — cannot vote from notification");
            return;
        }

        // Try to use fresh location if available, fallback to payload coords
        FusedLocationProviderClient fusedClient = LocationServices.getFusedLocationProviderClient(context);
        try {
            if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                fusedClient.getLastLocation().addOnSuccessListener(location -> {
                    double useLat = location != null ? location.getLatitude() : lat;
                    double useLng = location != null ? location.getLongitude() : lng;
                    submitVote(context, reportId, voteType, useLat, useLng, token);
                }).addOnFailureListener(e -> submitVote(context, reportId, voteType, lat, lng, token));
            } else {
                submitVote(context, reportId, voteType, lat, lng, token);
            }
        } catch (SecurityException e) {
            submitVote(context, reportId, voteType, lat, lng, token);
        }
    }

    private void submitVote(Context context, int reportId, String voteType,
                            double lat, double lng, String token) {
        VoteRequest request = new VoteRequest(voteType, lat, lng);
        ApiClient.getInstance().submitVote(reportId, request)
                .enqueue(new Callback<VoteResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<VoteResponse> call,
                                          @NonNull Response<VoteResponse> response) {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "✓ Vote '" + voteType + "' submitted for report " + reportId);
                            FcmNotificationManager.getInstance(context).markAsProcessed(reportId);
                        } else {
                            Log.e(TAG, "✗ Vote failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<VoteResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "✗ Vote network error", t);
                    }
                });
    }
}
