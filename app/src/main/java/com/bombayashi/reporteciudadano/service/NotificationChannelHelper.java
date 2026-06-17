package com.bombayashi.reporteciudadano.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class NotificationChannelHelper {
    private static final String TAG = "NotificationChannelHelper";

    // Channel IDs
    public static final String CHANNEL_ID_REPORTS = "report_notifications";
    public static final String CHANNEL_ID_UPDATES = "report_updates";
    public static final String CHANNEL_ID_PROXIMITY = "proximity_alerts";

    /**
     * Create all notification channels for Android 8+
     * Should be called once in Application.onCreate() or during app initialization
     */
    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);

            if (notificationManager == null) {
                Log.e(TAG, "NotificationManager is null");
                return;
            }

            // Channel 1: New Reports (HIGH priority)
            createReportNotificationsChannel(notificationManager, context);

            // Channel 2: Report Updates (DEFAULT priority)
            createReportUpdatesChannel(notificationManager, context);

            // Channel 3: Proximity Alerts (HIGH priority)
            createProximityAlertsChannel(notificationManager, context);

            Log.d(TAG, "✓ Notification channels created");
        }
    }

    /**
     * Create channel for new reports — HIGH priority
     * Shows heads-up notification
     */
    private static void createReportNotificationsChannel(NotificationManager manager, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_REPORTS,
                    "Nuevos Reportes",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Notificaciones de nuevos reportes en tu área");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            // Sound
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();

            // Default notification sound
            channel.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    audioAttributes
            );

            // Vibration pattern (200ms on, 100ms off, 200ms on)
            channel.setVibrationPattern(new long[]{200, 100, 200});

            manager.createNotificationChannel(channel);
            Log.d(TAG, "✓ Created channel: " + CHANNEL_ID_REPORTS);
        }
    }

    /**
     * Create channel for report updates — DEFAULT priority
     * Doesn't show heads-up notification
     */
    private static void createReportUpdatesChannel(NotificationManager manager, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_UPDATES,
                    "Actualizaciones de Reportes",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            channel.setDescription("Actualizaciones sobre reportes que seguís");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);

            // Quiet sound or no sound
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    audioAttributes
            );

            // Light vibration (100ms on)
            channel.setVibrationPattern(new long[]{100});

            manager.createNotificationChannel(channel);
            Log.d(TAG, "✓ Created channel: " + CHANNEL_ID_UPDATES);
        }
    }

    /**
     * Create channel for proximity alerts — HIGH priority
     * Location-based urgent notifications
     */
    private static void createProximityAlertsChannel(NotificationManager manager, Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_PROXIMITY,
                    "Alertas de Proximidad",
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Alertas urgentes de reportes muy cercanos a tu ubicación");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
            channel.setBypassDnd(false);

            // Sound with higher priority feel
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    audioAttributes
            );

            // Strong vibration pattern (300ms on, 100ms off, 300ms on, 100ms off, 300ms on)
            channel.setVibrationPattern(new long[]{300, 100, 300, 100, 300});

            manager.createNotificationChannel(channel);
            Log.d(TAG, "✓ Created channel: " + CHANNEL_ID_PROXIMITY);
        }
    }

    /**
     * Get the appropriate channel ID based on notification type and payload
     */
    public static String getChannelIdForNotification(String notificationType, double distance) {
        if (notificationType == null) {
            return CHANNEL_ID_REPORTS;
        }

        switch (notificationType) {
            case "new_report":
                // Use proximity channel if very close (< 200m)
                if (distance < 0.2) {
                    return CHANNEL_ID_PROXIMITY;
                }
                return CHANNEL_ID_REPORTS;

            case "report_updated":
            case "votes_update":
                return CHANNEL_ID_UPDATES;

            default:
                return CHANNEL_ID_REPORTS;
        }
    }

    /**
     * Delete a notification channel (only works on Android 8+)
     */
    public static void deleteNotificationChannel(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                try {
                    manager.deleteNotificationChannel(channelId);
                    Log.d(TAG, "✓ Deleted channel: " + channelId);
                } catch (Exception e) {
                    Log.e(TAG, "Error deleting channel: " + channelId, e);
                }
            }
        }
    }

    /**
     * Check if a notification channel exists
     */
    public static boolean channelExists(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                NotificationChannel channel = manager.getNotificationChannel(channelId);
                return channel != null;
            }
        }
        return false;
    }
}
