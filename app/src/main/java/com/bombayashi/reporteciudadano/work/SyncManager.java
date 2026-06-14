package com.bombayashi.reporteciudadano.work;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

/** Encola {@link ReportSyncWorker} para vaciar la cola de acciones pendientes (RF-05). */
public class SyncManager {

    private static final String UNIQUE_WORK_NAME = "report_sync";
    private static final String PERIODIC_WORK_NAME = "report_sync_periodic";

    private static final Constraints CONNECTED_CONSTRAINT = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

    /** Llamar al encolar una nueva acción pendiente o al detectar reconexión. */
    public static void syncNow(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(ReportSyncWorker.class)
                .setConstraints(CONNECTED_CONSTRAINT)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL,
                        30, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request);
    }

    /** Llamar una vez (ej. Application.onCreate) para reintentos periódicos de respaldo. */
    public static void schedulePeriodicSync(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ReportSyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(CONNECTED_CONSTRAINT)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
