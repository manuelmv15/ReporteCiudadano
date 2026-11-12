package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

/** POST /shop/{slug}/purchase. En error (409/403/422) viene success=false y message. */
public class PurchaseResponse {
    @SerializedName("success")
    private boolean success;
    @SerializedName("message")
    private String message;
    /** Saldo después de la compra. */
    @SerializedName("coins")
    private int coins;
    @SerializedName("slug")
    private String slug;

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
    public int getCoins() { return coins; }
    public String getSlug() { return slug; }
}
