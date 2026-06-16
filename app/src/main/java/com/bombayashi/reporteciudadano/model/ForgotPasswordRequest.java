package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class ForgotPasswordRequest {
    @SerializedName("email")
    private final String email;

    public ForgotPasswordRequest(String email) { this.email = email; }
}
