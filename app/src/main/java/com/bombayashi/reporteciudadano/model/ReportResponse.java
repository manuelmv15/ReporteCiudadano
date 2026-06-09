package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ReportResponse {
    @SerializedName("current_page")
    private int currentPage;
    @SerializedName("per_page")
    private int perPage;
    @SerializedName("total")
    private int total;
    @SerializedName("data")
    private List<ReportData> data;

    public int getCurrentPage() { return currentPage; }
    public int getPerPage() { return perPage; }
    public int getTotal() { return total; }
    public List<ReportData> getData() { return data; }

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
        @SerializedName("photo")
        private String photo;
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("user")
        private UserInfo user;
        @SerializedName("category")
        private CategoryInfo category;
        @SerializedName("votes")
        private VotesInfo votes;

        public int getId() { return id; }
        public int getCategoryId() { return categoryId; }
        public int getUserId() { return userId; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public String getPhoto() { return photo; }
        public String getCreatedAt() { return createdAt; }
        public UserInfo getUser() { return user; }
        public CategoryInfo getCategory() { return category; }
        public VotesInfo getVotes() { return votes; }
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
