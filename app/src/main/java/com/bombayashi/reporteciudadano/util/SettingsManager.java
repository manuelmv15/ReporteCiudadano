package com.bombayashi.reporteciudadano.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public class SettingsManager {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode"; // "system", "light", "dark"
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    // RF-23: proximity alert settings
    private static final String KEY_ALERT_RADIUS = "alert_radius_meters";
    private static final String KEY_ALERT_CAT_BACHE = "alert_cat_bache";
    private static final String KEY_ALERT_CAT_ALUMBRADO = "alert_cat_alumbrado";
    private static final String KEY_ALERT_CAT_BASURA = "alert_cat_basura";
    private static final String KEY_ALERT_CAT_AGUA = "alert_cat_agua";
    private static final String KEY_ALERT_CAT_ACCIDENTE = "alert_cat_accidente";
    private static final String KEY_ALERT_CAT_VANDALO = "alert_cat_vandalo";
    private static final String KEY_ALERT_CAT_OTRO = "alert_cat_otro";
    // SeekBar: 0→100m, 1→200m, 2→300m, 3→400m, 4→500m
    private static final int[] RADIUS_VALUES = {100, 200, 300, 400, 500};

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context.getApplicationContext());
        }
        return instance;
    }

    public String getThemeMode() {
        return prefs.getString(KEY_DARK_MODE, "system");
    }

    public void setThemeMode(String mode) {
        prefs.edit().putString(KEY_DARK_MODE, mode).apply();
        applyThemeMode(mode);
    }

    public void applyCurrentTheme() {
        applyThemeMode(getThemeMode());
    }

    private void applyThemeMode(String mode) {
        switch (mode) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }

    public boolean isNotificationsEnabled() {
        return prefs.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public void setNotificationsEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply();
    }

    // RF-23: alert radius (100–500 m)
    public int getAlertRadiusMeters() {
        return prefs.getInt(KEY_ALERT_RADIUS, 300);
    }

    public void setAlertRadiusMeters(int meters) {
        prefs.edit().putInt(KEY_ALERT_RADIUS, meters).apply();
    }

    public int getAlertRadiusSeekIndex() {
        int radius = getAlertRadiusMeters();
        for (int i = 0; i < RADIUS_VALUES.length; i++) {
            if (RADIUS_VALUES[i] == radius) return i;
        }
        return 2; // default 300m
    }

    public int seekIndexToRadius(int index) {
        if (index < 0 || index >= RADIUS_VALUES.length) return 300;
        return RADIUS_VALUES[index];
    }

    // RF-23: alert categories (default all enabled)
    public boolean isAlertCategoryEnabled(String categoryKey) {
        return prefs.getBoolean(categoryKey, true);
    }

    public void setAlertCategoryEnabled(String categoryKey, boolean enabled) {
        prefs.edit().putBoolean(categoryKey, enabled).apply();
    }

    public String getAlertCatKeyBache()      { return KEY_ALERT_CAT_BACHE; }
    public String getAlertCatKeyAlumbrado()  { return KEY_ALERT_CAT_ALUMBRADO; }
    public String getAlertCatKeyBasura()     { return KEY_ALERT_CAT_BASURA; }
    public String getAlertCatKeyAgua()       { return KEY_ALERT_CAT_AGUA; }
    public String getAlertCatKeyAccidente()  { return KEY_ALERT_CAT_ACCIDENTE; }
    public String getAlertCatKeyVandalo()    { return KEY_ALERT_CAT_VANDALO; }
    public String getAlertCatKeyOtro()       { return KEY_ALERT_CAT_OTRO; }
}
