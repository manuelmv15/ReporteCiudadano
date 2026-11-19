package com.bombayashi.reporteciudadano.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.bombayashi.reporteciudadano.model.LocationUpdateRequest;
import com.bombayashi.reporteciudadano.model.SimpleResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Envía la ubicación del usuario a POST /me/location para que el servidor solo le mande
 * el push de "reporte nuevo cerca" si de verdad está cerca. Limita los envíos: solo si se
 * movió más de MIN_DISTANCE_METERS o pasaron MIN_INTERVAL_MS desde el último.
 */
public final class LocationReporter {

    private static final String PREFS = "location_reporter";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LNG = "lng";
    private static final String KEY_AT = "at";

    private static final double MIN_DISTANCE_METERS = 150;
    private static final long MIN_INTERVAL_MS = 10 * 60 * 1000;

    private static boolean inFlight = false;

    private LocationReporter() {}

    public static void report(Context context, double lat, double lng) {
        if (context == null || inFlight) return;
        Context app = context.getApplicationContext();
        if (!TokenManager.getInstance(app).isLoggedIn()) return;

        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastAt = prefs.getLong(KEY_AT, 0);
        if (lastAt > 0) {
            double lastLat = Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0));
            double lastLng = Double.longBitsToDouble(prefs.getLong(KEY_LNG, 0));
            double moved = LocationUtil.calculateDistance(lastLat, lastLng, lat, lng);
            boolean recent = System.currentTimeMillis() - lastAt < MIN_INTERVAL_MS;
            if (recent && moved < MIN_DISTANCE_METERS) return;
        }

        inFlight = true;
        ApiClient.getInstance().updateLocation(new LocationUpdateRequest(lat, lng))
                .enqueue(new Callback<SimpleResponse>() {
                    @Override
                    public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                        inFlight = false;
                        if (response.isSuccessful()) {
                            prefs.edit()
                                    .putLong(KEY_LAT, Double.doubleToRawLongBits(lat))
                                    .putLong(KEY_LNG, Double.doubleToRawLongBits(lng))
                                    .putLong(KEY_AT, System.currentTimeMillis())
                                    .apply();
                        }
                    }

                    @Override
                    public void onFailure(Call<SimpleResponse> call, Throwable t) {
                        inFlight = false;
                    }
                });
    }
}
