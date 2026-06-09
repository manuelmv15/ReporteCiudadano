package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class ReportRequest {
    @SerializedName("category_id")
    private int categoryId;
    @SerializedName("latitude")
    private double latitude;
    @SerializedName("longitude")
    private double longitude;
    @SerializedName("description")
    private String description;

    public ReportRequest(int categoryId, double latitude, double longitude, String description) {
        this.categoryId = categoryId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
    }

    // Getters and Setters
    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
