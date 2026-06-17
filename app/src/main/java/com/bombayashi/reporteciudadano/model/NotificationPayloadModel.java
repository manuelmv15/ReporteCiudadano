package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class NotificationPayloadModel {
    @SerializedName("report_id")
    private int reportId;

    @SerializedName("type")
    private String type;  // new_report | report_updated | votes_update

    @SerializedName("status")
    private String status;  // pending | verified | resolved | archived

    @SerializedName("category")
    private String category;

    @SerializedName("category_id")
    private int categoryId;

    @SerializedName("latitude")
    private double latitude;

    @SerializedName("longitude")
    private double longitude;

    @SerializedName("distance")
    private double distance;  // km from user

    @SerializedName("user_id")
    private int userId;

    @SerializedName("title")
    private String title;

    @SerializedName("body")
    private String body;

    @SerializedName("votes_confirm")
    private int votesConfirm;

    @SerializedName("votes_resolve")
    private int votesResolve;

    @SerializedName("created_at")
    private String createdAt;

    public NotificationPayloadModel() {}

    public NotificationPayloadModel(int reportId, String type, String status, String category,
                                    int categoryId, double latitude, double longitude, double distance,
                                    int userId, String title, String body, int votesConfirm,
                                    int votesResolve, String createdAt) {
        this.reportId = reportId;
        this.type = type;
        this.status = status;
        this.category = category;
        this.categoryId = categoryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distance = distance;
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.votesConfirm = votesConfirm;
        this.votesResolve = votesResolve;
        this.createdAt = createdAt;
    }

    // Getters
    public int getReportId() { return reportId; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getCategory() { return category; }
    public int getCategoryId() { return categoryId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getDistance() { return distance; }
    public int getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public int getVotesConfirm() { return votesConfirm; }
    public int getVotesResolve() { return votesResolve; }
    public String getCreatedAt() { return createdAt; }

    // Setters
    public void setReportId(int reportId) { this.reportId = reportId; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setCategory(String category) { this.category = category; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public void setDistance(double distance) { this.distance = distance; }
    public void setUserId(int userId) { this.userId = userId; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setVotesConfirm(int votesConfirm) { this.votesConfirm = votesConfirm; }
    public void setVotesResolve(int votesResolve) { this.votesResolve = votesResolve; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
