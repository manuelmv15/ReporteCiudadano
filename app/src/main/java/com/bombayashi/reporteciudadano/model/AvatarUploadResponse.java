package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class AvatarUploadResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;
    @SerializedName("avatar_url")
    private String avatarUrl;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public String getAvatarUrl() { return avatarUrl; }
}
