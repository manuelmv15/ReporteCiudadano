package com.bombayashi.reporteciudadano.util;

public final class CategoryMapper {

    private CategoryMapper() {}

    /** Slug del menú radial → ID de categoría en la API. */
    public static int toApiId(String slug) {
        return switch (slug) {
            case "vialidad"  -> 1;
            case "alumbrado" -> 2;
            case "agua"      -> 4;
            case "trafico"   -> 5;
            case "seguridad" -> 6;
            default          -> 3; // basura / otros
        };
    }

    /** Slug de la API (puede tener guiones) → slug de grupo usado en filtros. */
    public static String toFilterGroup(String apiSlug) {
        if (apiSlug == null) return "otros";
        return switch (apiSlug) {
            case "bache", "vialidad"            -> "vialidad";
            case "alumbrado-publico", "alumbrado" -> "alumbrado";
            case "fuga-de-agua", "agua"          -> "agua";
            case "semaforo-danado", "trafico"    -> "trafico";
            case "inseguridad", "seguridad"      -> "seguridad";
            case "basura-acumulada", "parques", "basura" -> "basura";
            default                              -> "otros";
        };
    }
}
