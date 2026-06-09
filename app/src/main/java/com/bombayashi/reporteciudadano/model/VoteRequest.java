package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class VoteRequest {
    @SerializedName("type")
    private String type;  // "confirm" or "resolve"
    @SerializedName("latitude")
    private double latitude;
    @SerializedName("longitude")
    private double longitude;

    public VoteRequest(String type, double latitude, double longitude) {
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getType() { return type; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
