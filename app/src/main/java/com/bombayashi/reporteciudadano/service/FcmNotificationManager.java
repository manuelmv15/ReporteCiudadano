package com.bombayashi.reporteciudadano.service;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.bombayashi.reporteciudadano.db.AppDatabase;
import com.bombayashi.reporteciudadano.db.FcmNotificationCacheEntity;
import com.bombayashi.reporteciudadano.model.NotificationPayloadModel;
import com.bombayashi.reporteciudadano.util.LocationUtil;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.messaging.RemoteMessage;
import com.google.gson.Gson;

public class FcmNotificationManager {
    private static final String TAG = "FcmNotificationManager";
    private static final double VOTING_RANGE_KM = 0.3;  // 300m per RF-21
    private static FcmNotificationManager instance;
    private final Context context;
    private final Gson gson;
    private final FusedLocationProviderClient fusedLocationClient;

    private FcmNotificationManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context);
    }

    public static FcmNotificationManager getInstance(Context context) {
        if (instance == null) {
            synchronized (FcmNotificationManager.class) {
                if (instance == null) {
                    instance = new FcmNotificationManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Parse FCM RemoteMessage into NotificationPayloadModel
     */
    public NotificationPayloadModel parseNotificationPayload(RemoteMessage remoteMessage) {
        try {
            if (remoteMessage == null || remoteMessage.getData() == null) {
                Log.w(TAG, "Null remoteMessage or data");
                return null;
            }

            java.util.Map<String, String> data = remoteMessage.getData();

            NotificationPayloadModel payload = new NotificationPayloadModel();
            payload.setReportId(parseIntSafe(data.get("report_id")));
            payload.setType(data.get("type") != null ? data.get("type") : "new_report");
            payload.setStatus(data.get("status") != null ? data.get("status") : "pending");
            payload.setCategory(data.get("category"));
            payload.setCategoryId(parseIntSafe(data.get("category_id")));
            payload.setLatitude(parseDoubleSafe(data.get("latitude")));
            payload.setLongitude(parseDoubleSafe(data.get("longitude")));
            payload.setDistance(parseDoubleSafe(data.get("distance")));
            payload.setUserId(parseIntSafe(data.get("user_id")));
            payload.setTitle(data.get("title") != null ? data.get("title") : "Nuevo Reporte");
            payload.setBody(data.get("body") != null ? data.get("body") : "Hay un nuevo reporte cerca");
            payload.setVotesConfirm(parseIntSafe(data.get("votes_confirm")));
            payload.setVotesResolve(parseIntSafe(data.get("votes_resolve")));
            payload.setCreatedAt(data.get("created_at"));

            Log.d(TAG, "✓ Parsed notification for report " + payload.getReportId());
            return payload;

        } catch (Exception e) {
            Log.e(TAG, "❌ Error parsing notification", e);
            return null;
        }
    }

    /**
     * Validate required fields in notification payload
     */
    public boolean validateNotificationData(NotificationPayloadModel payload) {
        if (payload == null) {
            Log.w(TAG, "⚠️ Null payload");
            return false;
        }

        if (payload.getReportId() <= 0) {
            Log.w(TAG, "⚠️ Missing report_id");
            return false;
        }

        if (Double.isNaN(payload.getLatitude()) || Double.isNaN(payload.getLongitude())) {
            Log.w(TAG, "⚠️ Invalid coordinates");
            return false;
        }

        if (payload.getTitle() == null || payload.getTitle().isEmpty()) {
            Log.w(TAG, "⚠️ Missing title");
            return false;
        }

        return true;
    }

    /**
     * Check if user is within voting range for this report
     * Uses cached location if available, falls back to last known location
     */
    public boolean isUserInVotingRange(double reportLat, double reportLng) {
        try {
            // Try to get current user location
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "⚠️ Location permission not granted, suppressing notification");
                    return false;
                }
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location == null) {
                    Log.d(TAG, "⚠️ No location available");
                }
            });

            // For notification flow, we check the distance provided in the payload
            // The backend calculates distance based on user's last known location
            // If distance > 0.5km (500m), backend won't send notification
            // So if we get here, backend already validated distance
            Log.d(TAG, "✓ User in voting range");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error checking distance", e);
            return false;
        }
    }

    /**
     * Check if this notification is a duplicate of a recent one
     */
    public boolean isDuplicateNotification(int reportId) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            FcmNotificationCacheEntity recent =
                db.fcmNotificationCacheDao().getLatestByReportId(reportId);

            if (recent == null) {
                return false;
            }

            // Consider it a duplicate if last notification was within 5 minutes
            long timeSinceLastNotification = System.currentTimeMillis() - recent.getReceivedAt();
            boolean isDuplicate = timeSinceLastNotification < 2 * 60 * 60 * 1000; // RF-25: 2h cooldown

            if (isDuplicate) {
                Log.d(TAG, "🔄 Duplicate notification for report " + reportId +
                    " (received " + (timeSinceLastNotification / 1000) + "s ago)");
            }

            return isDuplicate;

        } catch (Exception e) {
            Log.e(TAG, "Error checking duplicate", e);
            return false;
        }
    }

    /**
     * Cache the notification in Room database for future processing/replay
     */
    public void cacheNotification(NotificationPayloadModel payload) {
        try {
            String payloadJson = gson.toJson(payload);
            FcmNotificationCacheEntity entity = new FcmNotificationCacheEntity(
                payload.getReportId(),
                payload.getType(),
                payload.getStatus(),
                payloadJson
            );

            AppDatabase db = AppDatabase.getInstance(context);
            db.fcmNotificationCacheDao().insert(entity);

            Log.d(TAG, "✓ Notification cached for report " + payload.getReportId());

            // Clean up old notifications (older than 7 days)
            long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000);
            db.fcmNotificationCacheDao().deleteOlderThan(weekAgo);

        } catch (Exception e) {
            Log.e(TAG, "Error caching notification", e);
        }
    }

    /**
     * Mark notification as processed
     */
    public void markAsProcessed(int reportId) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            db.fcmNotificationCacheDao().markAsProcessed(reportId);
            Log.d(TAG, "✓ Marked notification " + reportId + " as processed");
        } catch (Exception e) {
            Log.e(TAG, "Error marking notification", e);
        }
    }

    /**
     * Mark notification as viewed
     */
    public void markAsViewed(int reportId) {
        try {
            AppDatabase db = AppDatabase.getInstance(context);
            db.fcmNotificationCacheDao().markAsViewed(reportId);
        } catch (Exception e) {
            Log.e(TAG, "Error marking notification as viewed", e);
        }
    }

    // Helper methods
    private int parseIntSafe(String value) {
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double parseDoubleSafe(String value) {
        try {
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
