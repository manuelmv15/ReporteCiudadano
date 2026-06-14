package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class SimpleResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
