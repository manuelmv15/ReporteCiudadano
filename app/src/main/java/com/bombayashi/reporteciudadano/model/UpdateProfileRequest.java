package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class UpdateProfileRequest {
    @SerializedName("name")
    private String name;

    public UpdateProfileRequest(String name) {
        this.name = name;
    }
}
