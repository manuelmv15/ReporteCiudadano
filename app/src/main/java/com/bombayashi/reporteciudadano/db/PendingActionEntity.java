package com.bombayashi.reporteciudadano.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cola de acciones pendientes de sincronizar con la API cuando no hay conexión.
 * Procesada por {@link com.bombayashi.reporteciudadano.work.ReportSyncWorker}.
 */
@Entity(tableName = "pending_action")
public class PendingActionEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** CREATE_REPORT, VOTE o RETRACT_REPORT */
    public String type;

    /** Payload JSON especifico del tipo de accion. */
    public String payload;

    /** PENDING, SYNCING o FAILED */
    public String status;

    public int retryCount;

    public long createdAt;

    public static final String TYPE_CREATE_REPORT = "CREATE_REPORT";
    public static final String TYPE_VOTE = "VOTE";
    public static final String TYPE_RETRACT_REPORT = "RETRACT_REPORT";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SYNCING = "SYNCING";
    public static final String STATUS_FAILED = "FAILED";

    public PendingActionEntity(String type, String payload, long createdAt) {
        this.type = type;
        this.payload = payload;
        this.status = STATUS_PENDING;
        this.retryCount = 0;
        this.createdAt = createdAt;
    }
}
