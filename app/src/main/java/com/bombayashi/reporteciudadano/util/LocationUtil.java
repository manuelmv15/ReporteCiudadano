package com.bombayashi.reporteciudadano.util;

public class LocationUtil {

    // Radio de la Tierra en metros
    private static final double EARTH_RADIUS_M = 6371000;

    // Distancia máxima para poder votar (500 metros)
    public static final double VOTE_RADIUS_METERS = 500.0;

    /**
     * Calcula la distancia entre dos puntos usando la fórmula Haversine
     * @param lat1 Latitud del punto 1
     * @param lng1 Longitud del punto 1
     * @param lat2 Latitud del punto 2
     * @param lng2 Longitud del punto 2
     * @return Distancia en metros
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_M * c;
    }

    /**
     * Verifica si un usuario está dentro del radio permitido para votar
     * @param userLat Latitud del usuario
     * @param userLng Longitud del usuario
     * @param reportLat Latitud del reporte
     * @param reportLng Longitud del reporte
     * @return true si está dentro de 500m, false en caso contrario
     */
    public static boolean isWithinVoteRadius(double userLat, double userLng,
                                            double reportLat, double reportLng) {
        double distance = calculateDistance(userLat, userLng, reportLat, reportLng);
        return distance <= VOTE_RADIUS_METERS;
    }
}
