package com.bombayashi.reporteciudadano;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bombayashi.reporteciudadano.databinding.ActivityMainBinding;
import com.bombayashi.reporteciudadano.service.NotificationChannelHelper;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ActivityMainBinding binding;
    private int pendingNotificationReportId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize notification channels
        NotificationChannelHelper.createNotificationChannels(this);

        // Handle notification intent if app was launched from notification
        handleNotificationIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle notification intent if app was already running
        handleNotificationIntent(intent);
    }

    /**
     * Handle deep link intent from notification tap
     * Format: reporteciudadano://show_report?id=<reportId>
     */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) return;

        Uri data = intent.getData();
        if (data == null || !"reporteciudadano".equals(data.getScheme())) return;
        if (!"show_report".equals(data.getHost())) return;

        String reportIdStr = data.getQueryParameter("id");
        if (reportIdStr == null || reportIdStr.isEmpty()) return;

        try {
            int id = Integer.parseInt(reportIdStr);
            Log.d(TAG, "📍 Opening report from notification: " + id);

            androidx.fragment.app.Fragment fragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_container);

            if (fragment instanceof com.bombayashi.reporteciudadano.ui.MapFragment) {
                com.bombayashi.reporteciudadano.ui.MapFragment mapFragment =
                        (com.bombayashi.reporteciudadano.ui.MapFragment) fragment;
                if (mapFragment.isMapReady()) {
                    mapFragment.showReportFromNotification(id);
                    Log.d(TAG, "✓ Navigated to report " + id);
                } else {
                    pendingNotificationReportId = id;
                    mapFragment.setOnMapReadyCallback(() -> {
                        mapFragment.showReportFromNotification(pendingNotificationReportId);
                        pendingNotificationReportId = -1;
                    });
                }
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "Invalid report ID from notification", e);
        }
    }
}
