package com.bombayashi.reporteciudadano;

import android.app.Application;

import com.bombayashi.reporteciudadano.util.SettingsManager;

public class ReporteCiudadanoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsManager.getInstance(this).applyCurrentTheme();
    }
}
