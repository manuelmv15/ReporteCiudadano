package com.bombayashi.reporteciudadano.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cache local de reportes para mostrar el mapa sin conexión.
 * El reporte completo (ReportResponse.ReportData) se guarda serializado en {@code json}.
 */
@Entity(tableName = "report_cache")
public class ReportCacheEntity {

    @PrimaryKey
    @NonNull
    public String reportId;

    public double latitude;
    public double longitude;
    public String status;

    /** ReportResponse.ReportData serializado con Gson. */
    public String json;

    public long cachedAt;

    public ReportCacheEntity(@NonNull String reportId, double latitude, double longitude,
                              String status, String json, long cachedAt) {
        this.reportId = reportId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.status = status;
        this.json = json;
        this.cachedAt = cachedAt;
    }
}
