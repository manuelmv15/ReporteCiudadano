package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MyVotesResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("votes")
    private PaginationData votes;

    public boolean isSuccess() { return success; }
    public List<VoteData> getData() {
        return (votes != null) ? votes.data : null;
    }

    public static class PaginationData {
        @SerializedName("current_page")
        public int currentPage;
        @SerializedName("per_page")
        public int perPage;
        @SerializedName("total")
        public int total;
        @SerializedName("data")
        public List<VoteData> data;
    }

    public static class VoteData {
        @SerializedName("id")
        private int id;
        @SerializedName("report_id")
        private int reportId;
        @SerializedName("type")
        private String type; // "confirm" o "resolve"
        @SerializedName("created_at")
        private String createdAt;
        @SerializedName("report")
        private ReportInfo report;

        public int getId() { return id; }
        public int getReportId() { return reportId; }
        public String getType() { return type; }
        public String getCreatedAt() { return createdAt; }
        public ReportInfo getReport() { return report; }

        /**
         * Voto correcto si el reporte terminó en el estado que el voto predijo:
         * "confirm" -> verified/resolved, "resolve" -> resolved.
         * Devuelve null si el reporte aún no tiene un estado final (pending/archived).
         */
        public Boolean isCorrect() {
            if (report == null || report.getStatus() == null) return null;
            String status = report.getStatus();
            if ("confirm".equals(type)) {
                if ("verified".equals(status) || "resolved".equals(status)) return true;
                if ("pending".equals(status)) return null;
                return false;
            }
            if ("resolve".equals(type)) {
                if ("resolved".equals(status)) return true;
                if ("pending".equals(status) || "verified".equals(status)) return null;
                return false;
            }
            return null;
        }
    }

    public static class ReportInfo {
        @SerializedName("id")
        private int id;
        @SerializedName("description")
        private String description;
        @SerializedName("status")
        private String status;
        @SerializedName("photo_path")
        private String photoPath;
        @SerializedName("votes_confirm")
        private int votesConfirm;
        @SerializedName("votes_resolve")
        private int votesResolve;
        @SerializedName("category")
        private ReportResponse.CategoryInfo category;

        public int getId() { return id; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }
        public String getPhotoPath() { return photoPath; }
        public int getVotesConfirm() { return votesConfirm; }
        public int getVotesResolve() { return votesResolve; }
        public ReportResponse.CategoryInfo getCategory() { return category; }
    }
}
