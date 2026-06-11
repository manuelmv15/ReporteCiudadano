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
        @SerializedName("avatar_url")
        private String avatarUrl;
        @SerializedName("score")
        private int score;
        @SerializedName("level")
        private String level;

        public int getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getAvatarUrl() { return avatarUrl; }
        public int getScore() { return score; }
        public String getLevel() { return level; }
    }
}
