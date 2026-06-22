package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class HeatmapResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("points")
    private List<Point> points;

    public boolean isSuccess() { return success; }
    public List<Point> getPoints() { return points; }

    public static class Point {
        @SerializedName("latitude")
        private double latitude;
        @SerializedName("longitude")
        private double longitude;

        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
    }
}
