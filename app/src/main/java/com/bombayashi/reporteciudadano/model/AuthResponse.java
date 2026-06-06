package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("token")
    private String token;

    @SerializedName("message")
    private String message;

    @SerializedName("user")
    private User user;

    public boolean isSuccess() { return success; }
    public String getToken() { return token; }
    public String getMessage() { return message; }
    public User getUser() { return user; }

    public static class User {
        @SerializedName("id")
        private int id;
        @SerializedName("name")
        private String name;
        @SerializedName("email")
        private String email;

        public int getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
    }
}
