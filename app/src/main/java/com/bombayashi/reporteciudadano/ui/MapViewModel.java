package com.bombayashi.reporteciudadano.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.bombayashi.reporteciudadano.model.ReportResponse;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MapViewModel extends ViewModel {

    // Report data — survives rotation
    private final Map<String, ReportResponse.ReportData> reportMarkers = new HashMap<>();

    // LiveData — Fragment observes to re-draw markers after rotation
    final MutableLiveData<ReportResponse.ReportData> reportUpdated = new MutableLiveData<>();
    final MutableLiveData<Boolean> reportsCleared = new MutableLiveData<>();

    // Polling — owned here so it keeps running during rotation
    private ReportPoller poller;

    // Filter state — survives rotation
    final Set<String> filterCategories = new HashSet<>();
    String filterStatus = "all";
    String filterAge = "all";
    boolean heatmapEnabled = false;

    // Loading flag
    boolean isLoadingReports = false;
    int currentReportsPage = 1;
    String lastFetchTimestamp = null;

    Map<String, ReportResponse.ReportData> getReportMarkers() {
        return reportMarkers;
    }

    void initPoller() {
        if (poller != null) return;
        poller = new ReportPoller(new ReportPoller.Callback() {
            @Override
            public void onReportUpdated(ReportResponse.ReportData report) {
                String key = String.valueOf(report.getId());
                ReportResponse.ReportData cached = reportMarkers.get(key);

                if (cached == null) {
                    if (reportMarkers.size() < 200) {
                        reportMarkers.put(key, report);
                        reportUpdated.postValue(report);
                    }
                    return;
                }

                boolean statusChanged = cached.getStatus() != null
                        && !cached.getStatus().equals(report.getStatus());
                reportMarkers.put(key, report);
                if (statusChanged) {
                    reportUpdated.postValue(report);
                }
            }

            @Override
            public boolean isActive() {
                return reportUpdated.hasActiveObservers();
            }
        });
    }

    ReportPoller getPoller() {
        return poller;
    }

    void clearReports() {
        reportMarkers.clear();
        reportsCleared.postValue(true);
    }

    void putReport(String key, ReportResponse.ReportData report) {
        reportMarkers.put(key, report);
    }

    void removeReport(String key) {
        reportMarkers.remove(key);
    }

    @Override
    protected void onCleared() {
        if (poller != null) poller.stop();
    }
}
