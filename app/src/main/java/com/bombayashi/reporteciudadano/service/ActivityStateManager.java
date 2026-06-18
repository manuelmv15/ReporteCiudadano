package com.bombayashi.reporteciudadano.service;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionEvent;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.ActivityTransitionResult;
import com.google.android.gms.location.DetectedActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * RF-24: Suppresses proximity notifications when user is stationary (STILL) or in a vehicle.
 * Only alerts if user is WALKING, ON_BICYCLE, or RUNNING.
 */
public class ActivityStateManager {

    private static final String TAG = "ActivityStateManager";
    static final String ACTION_ACTIVITY_TRANSITION = "com.bombayashi.reporteciudadano.ACTIVITY_TRANSITION";

    // Current detected activity type (-1 = unknown, assume moving)
    private static int sCurrentActivity = -1;

    private static ActivityStateManager instance;
    private final Context context;

    private ActivityStateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static ActivityStateManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ActivityStateManager.class) {
                if (instance == null) instance = new ActivityStateManager(context);
            }
        }
        return instance;
    }

    /** Call once at app start (from ReporteCiudadanoApp or MainActivity) */
    public void startTracking() {
        try {
            if (context.checkSelfPermission("android.permission.ACTIVITY_RECOGNITION")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "ACTIVITY_RECOGNITION permission not granted — RF-24 inactive");
                return;
            }

            List<ActivityTransition> transitions = new ArrayList<>();
            int[] trackedActivities = {
                DetectedActivity.STILL,
                DetectedActivity.WALKING,
                DetectedActivity.ON_BICYCLE,
                DetectedActivity.RUNNING,
                DetectedActivity.IN_VEHICLE
            };
            for (int type : trackedActivities) {
                transitions.add(new ActivityTransition.Builder()
                        .setActivityType(type)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
            }

            ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);
            ActivityRecognitionClient client = ActivityRecognition.getClient(context);
            client.requestActivityTransitionUpdates(request, getTransitionPendingIntent())
                    .addOnSuccessListener(v -> Log.d(TAG, "✓ Activity transition updates registered"))
                    .addOnFailureListener(e -> Log.e(TAG, "✗ Failed to register activity transitions", e));

        } catch (Exception e) {
            Log.e(TAG, "Error starting activity tracking", e);
        }
    }

    public void stopTracking() {
        try {
            ActivityRecognition.getClient(context)
                    .removeActivityTransitionUpdates(getTransitionPendingIntent())
                    .addOnSuccessListener(v -> Log.d(TAG, "✓ Activity transitions unregistered"))
                    .addOnFailureListener(e -> Log.e(TAG, "✗ Failed to unregister", e));
        } catch (Exception e) {
            Log.e(TAG, "Error stopping activity tracking", e);
        }
    }

    /**
     * Returns true if user appears to be moving — safe to show proximity notification.
     * Returns true by default if activity is unknown (fail-open).
     */
    public static boolean isUserMoving() {
        if (sCurrentActivity == -1) return true; // unknown → show notification
        return sCurrentActivity == DetectedActivity.WALKING
                || sCurrentActivity == DetectedActivity.ON_BICYCLE
                || sCurrentActivity == DetectedActivity.RUNNING;
    }

    static void updateActivity(int activityType) {
        sCurrentActivity = activityType;
        Log.d(TAG, "Activity updated: " + activityType);
    }

    private PendingIntent getTransitionPendingIntent() {
        Intent intent = new Intent(ACTION_ACTIVITY_TRANSITION);
        intent.setClass(context, ActivityTransitionReceiver.class);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Inner receiver — registered in Manifest */
    public static class ActivityTransitionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ActivityTransitionResult.hasResult(intent)) return;
            ActivityTransitionResult result = ActivityTransitionResult.extractResult(intent);
            if (result == null) return;
            for (ActivityTransitionEvent event : result.getTransitionEvents()) {
                if (event.getTransitionType() == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                    ActivityStateManager.updateActivity(event.getActivityType());
                }
            }
        }
    }
}
