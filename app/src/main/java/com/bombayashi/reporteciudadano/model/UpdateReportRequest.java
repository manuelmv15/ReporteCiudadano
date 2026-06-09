package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class UpdateReportRequest {
    @SerializedName("description")
    private String description;

    public UpdateReportRequest(String description) {
        this.description = description;
    }
}
