package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** GET /shop: catálogo con el estado de cada artículo para el usuario y su saldo. */
public class ShopResponse {
    @SerializedName("success")
    private boolean success;
    /** Saldo gastable. */
    @SerializedName("coins")
    private int coins;
    /** Puntaje acumulado (define el nivel; no baja al comprar). */
    @SerializedName("score")
    private int score;
    @SerializedName("level")
    private String level;
    @SerializedName("items")
    private List<Item> items;

    public boolean isSuccess() { return success; }
    public int getCoins() { return coins; }
    public int getScore() { return score; }
    public String getLevel() { return level; }
    public List<Item> getItems() { return items; }

    public static class Item {
        @SerializedName("slug")
        private String slug;
        /** "frame" | "title" | "theme" */
        @SerializedName("type")
        private String type;
        @SerializedName("name")
        private String name;
        @SerializedName("description")
        private String description;
        @SerializedName("price")
        private int price;
        @SerializedName("min_level")
        private String minLevel;
        @SerializedName("value")
        private Cosmetics.Value value;
        @SerializedName("owned")
        private boolean owned;
        @SerializedName("equipped")
        private boolean equipped;
        @SerializedName("locked")
        private boolean locked;

        public String getSlug() { return slug; }
        public String getType() { return type; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getPrice() { return price; }
        public String getMinLevel() { return minLevel; }
        public Cosmetics.Value getValue() { return value; }
        public boolean isOwned() { return owned; }
        public boolean isEquipped() { return equipped; }
        public boolean isLocked() { return locked; }
    }
}
