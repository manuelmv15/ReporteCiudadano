package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class GoogleLoginRequest {
    @SerializedName("id_token")
    private String idToken;

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }
}
