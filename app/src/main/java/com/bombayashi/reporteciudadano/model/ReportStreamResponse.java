package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class ReportStreamResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("timestamp")
    private String timestamp;
    @SerializedName("count")
    private int count;
    @SerializedName("reports")
    private List<ReportResponse.ReportData> reports;

    public boolean isSuccess() { return success; }
    public String getTimestamp() { return timestamp; }
    public int getCount() { return count; }
    public List<ReportResponse.ReportData> getReports() { return reports; }
}
