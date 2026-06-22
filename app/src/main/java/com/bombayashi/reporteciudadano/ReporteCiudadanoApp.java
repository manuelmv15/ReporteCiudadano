package com.bombayashi.reporteciudadano;

import android.app.Application;

import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.service.ActivityStateManager;
import com.bombayashi.reporteciudadano.util.SettingsManager;
import com.bombayashi.reporteciudadano.work.SyncManager;

public class ReporteCiudadanoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ApiClient.init(this);
        SettingsManager.getInstance(this).applyCurrentTheme();
        SyncManager.schedulePeriodicSync(this);
        ActivityStateManager.getInstance(this).startTracking(); // RF-24
    }
}
