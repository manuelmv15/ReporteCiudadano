package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

/** Cuerpo de POST /me/location. */
public class LocationUpdateRequest {
    @SerializedName("latitude")
    private final double latitude;
    @SerializedName("longitude")
    private final double longitude;

    public LocationUpdateRequest(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
