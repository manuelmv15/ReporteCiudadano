package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class ResetPasswordRequest {
    @SerializedName("email")
    private final String email;
    @SerializedName("token")
    private final String token;
    @SerializedName("password")
    private final String password;
    @SerializedName("password_confirmation")
    private final String passwordConfirmation;

    public ResetPasswordRequest(String email, String token, String password) {
        this.email = email;
        this.token = token;
        this.password = password;
        this.passwordConfirmation = password;
    }
}
