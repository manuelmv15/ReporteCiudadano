package com.bombayashi.reporteciudadano.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/**
 * Reportes de los que ya se avisó "puedes votar" (push o aviso local), guardados en disco
 * para que entrar y salir de la app no repita el aviso. Un reporte se avisa una sola vez;
 * las entradas de más de RETENTION_MS se borran (para entonces el reporte ya se archivó).
 */
public final class NotifiedReports {

    private static final String PREFS = "notified_reports";
    private static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000;

    private NotifiedReports() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized boolean wasNotified(Context context, int reportId) {
        return prefs(context).contains(String.valueOf(reportId));
    }

    public static synchronized void markNotified(Context context, int reportId) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor editor = p.edit();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : p.getAll().entrySet()) {
            Object at = entry.getValue();
            if (!(at instanceof Long) || now - (Long) at > RETENTION_MS) editor.remove(entry.getKey());
        }
        editor.putLong(String.valueOf(reportId), now).apply();
    }
}
