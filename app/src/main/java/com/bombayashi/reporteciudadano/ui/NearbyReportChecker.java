package com.bombayashi.reporteciudadano.ui;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.bombayashi.reporteciudadano.MainActivity;
import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.service.NotificationChannelHelper;
import com.bombayashi.reporteciudadano.util.NotifiedReports;
import com.mapbox.geojson.Point;

import java.util.Collection;

class NearbyReportChecker {

    private static final double VOTABLE_RANGE_KM = 0.5;
    private static final int NOTIFICATION_ID = 999;

    private final Context context;

    NearbyReportChecker(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Avisa solo de reportes votables (a menos de 500 m, no tuyos, no resueltos) de los que
     * todavía no se avisó nunca, ni por push ni por este aviso (ver NotifiedReports). Así
     * entrar a la app no repite el aviso de un reporte que ya conocías.
     */
    void check(Collection<ReportResponse.ReportData> reports, Point userLocation, int currentUserId) {
        if (userLocation == null || reports.isEmpty()) return;

        try {
            java.util.List<Integer> fresh = new java.util.ArrayList<>();
            for (ReportResponse.ReportData report : reports) {
                String status = report.getStatus();
                if ("archived".equals(status) || "resolved".equals(status)) continue;
                if (report.getUser() != null && report.getUser().getId() == currentUserId) continue;
                if (report.getUserVote() != null) continue;
                if (NotifiedReports.wasNotified(context, report.getId())) continue;

                double dist = haversineKm(
                        userLocation.latitude(), userLocation.longitude(),
                        report.getLatitude(), report.getLongitude()
                );
                if (dist <= VOTABLE_RANGE_KM) fresh.add(report.getId());
            }

            if (fresh.isEmpty()) return;
            for (int id : fresh) NotifiedReports.markNotified(context, id);
            notify(fresh.size());
        } catch (Exception e) {
            android.util.Log.e("NearbyReportChecker", "Error checking votable reports", e);
        }
    }

    private void notify(int count) {
        try {
            String title = "¡Puedes votar!";
            String message = count == 1
                    ? "Hay 1 reporte nuevo cerca donde puedes votar"
                    : "Hay " + count + " reportes nuevos cerca donde puedes votar";

            Intent intent = new Intent(context, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_ID_REPORTS)
                            .setSmallIcon(R.drawable.warning_24px)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setAutoCancel(true)
                            .setContentIntent(pi)
                            .setPriority(NotificationCompat.PRIORITY_HIGH);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            android.util.Log.e("NearbyReportChecker", "Error showing notification", e);
        }
    }

    static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
