package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ReportResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("reports")
    private PaginationData reports;

    public boolean isSuccess() { return success; }
    public List<ReportData> getData() {
        return (reports != null) ? reports.data : null;
    }
    public int getCurrentPage() {
        return (reports != null) ? reports.currentPage : 0;
    }
    public int getTotal() {
        return (reports != null) ? reports.total : 0;
    }

    public static class PaginationData {
        @SerializedName("current_page")
        public int currentPage;
        @SerializedName("per_page")
        public int perPage;
        @SerializedName("total")
        public int total;
        @SerializedName("data")
        public List<ReportData> data;
    }

    public static class ReportData {
        @SerializedName("id")
        private int id;
        @SerializedName("category_id")
        private int categoryId;
        @SerializedName("user_id")
        private int userId;
        @SerializedName("latitude")
        private double latitude;
        @SerializedName("longitude")
        private double longitude;
        @SerializedName("description")
        private String description;
        @SerializedName("status")
        private String status; // pending, verified, resolved, archived
        @SerializedName("photo_path")
        private String photo;
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("user")
        private UserInfo user;
        @SerializedName("category")
        private CategoryInfo category;
        @SerializedName("votes")
        private VotesInfo votes;
        @SerializedName("votes_confirm")
        private int votesConfirm;
        @SerializedName("votes_resolve")
        private int votesResolve;
        @SerializedName("user_vote")
        private String userVote;  // "confirm", "resolve", o null si no ha votado
        @SerializedName("user_voted_at")
        private String userVotedAt;  // timestamp cuando el usuario votó

        public int getId() { return id; }
        public int getCategoryId() { return categoryId; }
        public int getUserId() { return userId; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStatus() { return status; }
        public String getPhoto() { return photo; }
        public void setPhoto(String photo) { this.photo = photo; }
        public String getPhotoUrl() {
            if (photo == null || photo.isEmpty()) return null;
            if (photo.startsWith("http")) return photo;
            return "https://api.manuelmv.net/storage/" + photo;
        }
        public String getCreatedAt() { return createdAt; }
        public UserInfo getUser() { return user; }
        public CategoryInfo getCategory() { return category; }
        public VotesInfo getVotes() { return votes; }
        public int getVotesConfirm() { return votes != null ? votes.getConfirm() : votesConfirm; }
        public int getVotesResolve() { return votes != null ? votes.getResolve() : votesResolve; }
        public String getUserVote() { return userVote; }
        public String getUserVotedAt() { return userVotedAt; }
    }

    public static class UserInfo {
        @SerializedName("id")
        private int id;
        @SerializedName("name")
        private String name;

        public int getId() { return id; }
        public String getName() { return name; }
    }

    public static class CategoryInfo {
        @SerializedName("id")
        private int id;
        @SerializedName("name")
        private String name;
        @SerializedName("slug")
        private String slug;

        public int getId() { return id; }
        public String getName() { return name; }
        public String getSlug() { return slug; }
    }

    public static class VotesInfo {
        @SerializedName("confirm")
        private int confirm;
        @SerializedName("resolve")
        private int resolve;

        public int getConfirm() { return confirm; }
        public int getResolve() { return resolve; }
    }
}
