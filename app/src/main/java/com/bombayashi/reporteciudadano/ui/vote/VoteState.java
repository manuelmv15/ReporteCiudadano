package com.bombayashi.reporteciudadano.ui.vote;

public class VoteState {
    private String currentUserVoteType;     // "confirm", "resolve", o null
    private int confirmCount;
    private int resolveCount;
    private boolean isLoading;
    private String error;
    private long voteEditableUntil;        // timestamp en ms
    private boolean isWithinRadius;
    private double userDistance;           // en metros

    private VoteState(Builder builder) {
        this.currentUserVoteType = builder.currentUserVoteType;
        this.confirmCount = builder.confirmCount;
        this.resolveCount = builder.resolveCount;
        this.isLoading = builder.isLoading;
        this.error = builder.error;
        this.voteEditableUntil = builder.voteEditableUntil;
        this.isWithinRadius = builder.isWithinRadius;
        this.userDistance = builder.userDistance;
    }

    // Getters
    public String getCurrentUserVoteType() { return currentUserVoteType; }
    public int getConfirmCount() { return confirmCount; }
    public int getResolveCount() { return resolveCount; }
    public boolean isLoading() { return isLoading; }
    public String getError() { return error; }
    public long getVoteEditableUntil() { return voteEditableUntil; }
    public boolean isWithinRadius() { return isWithinRadius; }
    public double getUserDistance() { return userDistance; }

    // Propiedades derivadas
    public int getTotalVotes() {
        return confirmCount + resolveCount;
    }

    public boolean hasUserVoted() {
        return currentUserVoteType != null;
    }

    public boolean canEditVote() {
        return hasUserVoted() && System.currentTimeMillis() < voteEditableUntil;
    }

    public long getTimeRemainingToEdit() {
        long remaining = voteEditableUntil - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0;
    }

    public boolean isVerified() {
        return confirmCount >= 5;
    }

    public boolean isResolved() {
        int total = getTotalVotes();
        return total >= 3 && (resolveCount / (double) total) >= 0.7;
    }

    public String getFormattedVoteCount() {
        return confirmCount + " confirman, " + resolveCount + " dicen que ya se resolvió";
    }

    // Builder Pattern
    public static class Builder {
        private String currentUserVoteType = null;
        private int confirmCount = 0;
        private int resolveCount = 0;
        private boolean isLoading = false;
        private String error = null;
        private long voteEditableUntil = 0;
        private boolean isWithinRadius = false;
        private double userDistance = 0;

        public Builder currentUserVoteType(String type) {
            this.currentUserVoteType = type;
            return this;
        }

        public Builder confirmCount(int count) {
            this.confirmCount = count;
            return this;
        }

        public Builder resolveCount(int count) {
            this.resolveCount = count;
            return this;
        }

        public Builder isLoading(boolean loading) {
            this.isLoading = loading;
            return this;
        }

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder voteEditableUntil(long timestamp) {
            this.voteEditableUntil = timestamp;
            return this;
        }

        public Builder isWithinRadius(boolean within) {
            this.isWithinRadius = within;
            return this;
        }

        public Builder userDistance(double distance) {
            this.userDistance = distance;
            return this;
        }

        public VoteState build() {
            return new VoteState(this);
        }
    }

    @Override
    public String toString() {
        return "VoteState{" +
                "userVote='" + currentUserVoteType + '\'' +
                ", confirm=" + confirmCount +
                ", resolve=" + resolveCount +
                ", loading=" + isLoading +
                ", withinRadius=" + isWithinRadius +
                ", distance=" + String.format("%.1f", userDistance) + "m" +
                '}';
    }
}
