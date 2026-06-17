package com.bombayashi.reporteciudadano.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "fcm_notification_cache")
public class FcmNotificationCacheEntity {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private int reportId;
    private String type;  // new_report | report_updated | votes_update
    private String status;  // pending | verified | resolved
    private String payload;  // JSON serialized NotificationPayloadModel
    private long receivedAt;  // timestamp
    private boolean isProcessed;
    private boolean isViewed;

    public FcmNotificationCacheEntity() {}

    public FcmNotificationCacheEntity(int reportId, String type, String status, String payload) {
        this.reportId = reportId;
        this.type = type;
        this.status = status;
        this.payload = payload;
        this.receivedAt = System.currentTimeMillis();
        this.isProcessed = false;
        this.isViewed = false;
    }

    // Getters
    public int getId() { return id; }
    public int getReportId() { return reportId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getPayload() { return payload; }
    public long getReceivedAt() { return receivedAt; }
    public boolean isProcessed() { return isProcessed; }
    public boolean isViewed() { return isViewed; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setReportId(int reportId) { this.reportId = reportId; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setReceivedAt(long receivedAt) { this.receivedAt = receivedAt; }
    public void setProcessed(boolean processed) { isProcessed = processed; }
    public void setViewed(boolean viewed) { isViewed = viewed; }
}
