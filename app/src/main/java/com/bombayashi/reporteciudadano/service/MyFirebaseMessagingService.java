package com.bombayashi.reporteciudadano.service;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.bombayashi.reporteciudadano.MainActivity;
import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.model.NotificationPayloadModel;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import android.app.NotificationManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_Service";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        Log.d(TAG, "📨 Mensaje recibido de: " + remoteMessage.getFrom());

        // Initialize notification channels (safe to call multiple times)
        NotificationChannelHelper.createNotificationChannels(this);

        // Get notification manager instance
        FcmNotificationManager notificationManager = FcmNotificationManager.getInstance(this);

        // Parse and validate notification payload
        NotificationPayloadModel payload = notificationManager.parseNotificationPayload(remoteMessage);

        if (!notificationManager.validateNotificationData(payload)) {
            Log.w(TAG, "❌ Invalid notification data, skipping");
            return;
        }

        // Check for duplicate notifications
        if (notificationManager.isDuplicateNotification(payload.getReportId())) {
            Log.d(TAG, "🔄 Duplicate notification detected, not showing");
            notificationManager.cacheNotification(payload);
            return;
        }

        // Check if user is in voting range (backend already validates, but double-check)
        if (!notificationManager.isUserInVotingRange(payload.getLatitude(), payload.getLongitude())) {
            Log.d(TAG, "📍 User not in voting range, suppressing notification");
            return;
        }

        // Cache notification for offline/replay scenarios
        notificationManager.cacheNotification(payload);

        // Mark as processed
        notificationManager.markAsProcessed(payload.getReportId());

        // Show notification with deep link
        showNotification(payload);
    }

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "🔑 Nuevo token de FCM: " + token);
        sendTokenToServer(token);
    }

    private void sendTokenToServer(String fcmToken) {
        String authToken = TokenManager.getInstance(this).getToken();
        if (authToken.isEmpty()) {
            Log.w(TAG, "No hay sesión activa para sincronizar el token de FCM.");
            return;
        }

        String bearerToken = "Bearer " + authToken;
        ApiClient.getInstance().updateFcmToken(bearerToken, fcmToken).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "✓ FCM Token sincronizado con éxito.");
                } else {
                    Log.e(TAG, "✗ Error al sincronizar FCM Token: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                Log.e(TAG, "✗ Error de red al sincronizar FCM Token", t);
            }
        });
    }

    /**
     * Show notification with deep link to report details
     */
    private void showNotification(NotificationPayloadModel payload) {
        try {
            // Create deep link intent to show report
            Intent deepLinkIntent = new Intent(Intent.ACTION_VIEW);
            deepLinkIntent.setData(Uri.parse("reporteciudadano://show_report?id=" + payload.getReportId()));
            deepLinkIntent.setClass(this, MainActivity.class);
            deepLinkIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            // Create PendingIntent with proper flags
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    payload.getReportId(),  // Use reportId as unique request code
                    deepLinkIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Get appropriate channel ID based on notification type
            String channelId = NotificationChannelHelper.getChannelIdForNotification(
                    payload.getType(),
                    payload.getDistance()
            );

            // Build notification
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.warning_24px)
                    .setContentTitle(payload.getTitle())
                    .setContentText(payload.getBody())
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent);

            // Add action intent for notification tap
            notificationBuilder.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(payload.getBody()));

            // Show notification
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.notify(payload.getReportId(), notificationBuilder.build());
                Log.d(TAG, "✓ Notification shown for report " + payload.getReportId());
            }

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }
}
