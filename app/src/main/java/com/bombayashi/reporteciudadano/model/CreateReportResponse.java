package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

public class CreateReportResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;
    @SerializedName("report")
    private ReportResponse.ReportData report;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public ReportResponse.ReportData getReport() { return report; }
}
