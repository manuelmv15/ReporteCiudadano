package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class VoteResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;
    @SerializedName("data")
    private VoteData data;

    // La API también puede retornar el voto directamente sin envolver en "data"
    @SerializedName("type")
    private String type;
    @SerializedName("user_id")
    private int userId;
    @SerializedName("report_id")
    private int reportId;
    @SerializedName("created_at")
    private String createdAt;
    @SerializedName("votes_confirm")
    private int votesConfirm;
    @SerializedName("votes_resolve")
    private int votesResolve;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }

    public VoteData getData() {
        // Si data existe, retornarla
        if (data != null) {
            return data;
        }

        // Si no, construir desde los campos raíz
        if (type != null) {
            VoteData constructed = new VoteData();
            constructed.type = type;
            constructed.userId = userId;
            constructed.reportId = reportId;
            constructed.createdAt = createdAt;
            constructed.votesConfirm = votesConfirm;
            constructed.votesResolve = votesResolve;
            return constructed;
        }

        return null;
    }

    public static class VoteData {
        @SerializedName("type")
        public String type;
        @SerializedName("user_id")
        public int userId;
        @SerializedName("report_id")
        public int reportId;
        @SerializedName("created_at")
        public String createdAt;
        @SerializedName("votes_confirm")
        public int votesConfirm;
        @SerializedName("votes_resolve")
        public int votesResolve;
        /** Puntos que ganó el votante con este voto (0 si ya había cobrado este reporte). */
        @SerializedName("points_awarded")
        public int pointsAwarded;
        /** Progreso del votante tras el voto; null en respuestas antiguas. */
        @SerializedName("score")
        public Integer score;
        @SerializedName("coins")
        public Integer coins;
        @SerializedName("level")
        public String level;

        public VoteData() {}

        public String getType() { return type; }
        public int getUserId() { return userId; }
        public int getReportId() { return reportId; }
        public String getCreatedAt() { return createdAt; }
        public int getVotesConfirm() { return votesConfirm; }
        public int getVotesResolve() { return votesResolve; }
        public int getPointsAwarded() { return pointsAwarded; }
        public Integer getScore() { return score; }
        public Integer getCoins() { return coins; }
        public String getLevel() { return level; }
    }
}
