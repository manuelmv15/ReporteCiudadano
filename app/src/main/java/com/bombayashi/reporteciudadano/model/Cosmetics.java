package com.bombayashi.reporteciudadano.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Cosméticos equipados de un usuario, tal como los devuelve la API en `user.cosmetics`.
 * Cada campo es null si no hay nada equipado de ese tipo. El tema de la barra solo viene
 * en /me (es personal); en los reportes de otros llegan marco y título.
 */
public class Cosmetics {
    @SerializedName("frame")
    private Value frame;
    @SerializedName("title")
    private Value title;
    @SerializedName("theme")
    private Value theme;

    public Value getFrame() { return frame; }
    public Value getTitle() { return title; }
    public Value getTheme() { return theme; }

    /**
     * Valor visual de un cosmético. Según el tipo se usan unos campos u otros:
     * marco → colors (1 o 2 = degradado); título → text + color; tema → color.
     */
    public static class Value {
        @SerializedName("slug")
        private String slug;
        @SerializedName("name")
        private String name;
        @SerializedName("colors")
        private List<String> colors;
        @SerializedName("text")
        private String text;
        @SerializedName("color")
        private String color;

        public String getSlug() { return slug; }
        public String getName() { return name; }
        public List<String> getColors() { return colors; }
        public String getText() { return text; }
        public String getColor() { return color; }
    }
}
