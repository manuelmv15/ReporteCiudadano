package com.bombayashi.reporteciudadano.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.mapbox.geojson.Point;

import java.util.Timer;
import java.util.TimerTask;

class LocationTracker {

    interface Callback {
        void onLocationReady(Point location, float accuracy);
        void onLocationUpdate(Point location);
        void onPermissionDenied();
    }

    static final int PERMISSION_REQUEST_CODE = 100;
    private static final float MOVEMENT_THRESHOLD_METERS = 2f;

    private final Callback callback;
    private FusedLocationProviderClient fusedClient;
    private LocationCallback continuousCallback;
    private Location lastTrackedLocation;

    LocationTracker(Callback callback) {
        this.callback = callback;
    }

    void init(Fragment fragment) {
        fusedClient = LocationServices.getFusedLocationProviderClient(fragment.requireActivity());
    }

    void requestLocation(Fragment fragment) {
        if (noPermission(fragment)) {
            ActivityCompat.requestPermissions(
                    fragment.requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSION_REQUEST_CODE
            );
        } else {
            getLastKnown(fragment);
        }
    }

    void onPermissionResult(Fragment fragment, int requestCode, int[] grantResults) {
        if (requestCode != PERMISSION_REQUEST_CODE) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastKnown(fragment);
            startContinuous(fragment);
        } else {
            callback.onPermissionDenied();
        }
    }

    void startContinuous(Fragment fragment) {
        if (noPermission(fragment) || continuousCallback != null) return;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        continuousCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc == null) return;
                updateIfMoved(loc);
            }
        };
        fusedClient.requestLocationUpdates(req, continuousCallback, null);
    }

    void stopContinuous(Fragment fragment) {
        if (continuousCallback == null) return;
        if (!noPermission(fragment)) {
            fusedClient.removeLocationUpdates(continuousCallback);
        }
        continuousCallback = null;
    }

    private void getLastKnown(Fragment fragment) {
        if (noPermission(fragment)) { callback.onPermissionDenied(); return; }

        fusedClient.getLastLocation().addOnSuccessListener(last -> {
            if (!fragment.isAdded() || fragment.getView() == null) return;
            if (last != null) {
                long ageMs = System.currentTimeMillis() - last.getTime();
                if (ageMs < 60_000 && last.getAccuracy() < 50) {
                    fireReady(last);
                } else {
                    requestOneShotUpdate(fragment);
                }
            } else {
                requestOneShotUpdate(fragment);
            }
        }).addOnFailureListener(e -> callback.onPermissionDenied());
    }

    private void requestOneShotUpdate(Fragment fragment) {
        if (noPermission(fragment)) return;

        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
                .setMinUpdateIntervalMillis(250)
                .build();

        final boolean[] done = {false};
        LocationCallback cb = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null || result.getLocations().isEmpty()) return;
                Location best = result.getLocations().get(0);
                for (Location loc : result.getLocations()) {
                    if (loc.getAccuracy() < best.getAccuracy()) best = loc;
                }
                done[0] = true;
                fireReady(best);
                if (!noPermission(fragment)) fusedClient.removeLocationUpdates(this);
            }
        };

        fusedClient.requestLocationUpdates(req, cb, null);

        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!noPermission(fragment)) fusedClient.removeLocationUpdates(cb);
            }
        }, 5000);
    }

    private void updateIfMoved(Location location) {
        if (lastTrackedLocation != null
                && lastTrackedLocation.distanceTo(location) < MOVEMENT_THRESHOLD_METERS) return;

        lastTrackedLocation = location;
        callback.onLocationUpdate(Point.fromLngLat(location.getLongitude(), location.getLatitude()));
    }

    private void fireReady(Location location) {
        lastTrackedLocation = location;
        callback.onLocationReady(
                Point.fromLngLat(location.getLongitude(), location.getLatitude()),
                location.getAccuracy()
        );
    }

    private boolean noPermission(Fragment fragment) {
        return ActivityCompat.checkSelfPermission(fragment.requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }
}
