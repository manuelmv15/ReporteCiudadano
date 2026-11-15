package com.bombayashi.reporteciudadano.ui;

import android.animation.ValueAnimator;

import com.mapbox.geojson.Point;
import com.mapbox.maps.plugin.locationcomponent.LocationConsumer;
import com.mapbox.maps.plugin.locationcomponent.LocationProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * Alimenta el puck del usuario con el promedio de las últimas lecturas crudas del GPS
 * en vez de dibujar cada lectura tal cual llega. El bearing que reporta Location a baja
 * velocidad es ruidoso (gira "a lo loco" aunque el teléfono esté casi quieto), así que
 * se guarda una ventana corta de lecturas y se dibuja la posición/orientación promedio
 * (media circular para el bearing, por el wrap-around de 360°→0°).
 *
 * No busca ser la forma más eficiente de hacer esto — es intencionalmente simple,
 * pensado para verse estable en una demo, no para un caso de uso de producción.
 */
class SmoothedLocationProvider implements LocationProvider {

    private static final int WINDOW = 6;
    private static final Function1<ValueAnimator, Unit> NO_OP_ANIMATOR_CONFIG = anim -> Unit.INSTANCE;

    private final List<LocationConsumer> consumers = new ArrayList<>();
    private final Deque<Point> positionWindow = new ArrayDeque<>();
    private final Deque<Float> bearingWindow = new ArrayDeque<>();

    @Override
    public void registerLocationConsumer(LocationConsumer locationConsumer) {
        consumers.add(locationConsumer);
    }

    @Override
    public void unRegisterLocationConsumer(LocationConsumer locationConsumer) {
        consumers.remove(locationConsumer);
    }

    /** Alimentar con cada lectura cruda (ya filtrada por movimiento en LocationTracker). */
    void push(Point rawPoint, float rawBearingDegrees) {
        positionWindow.addLast(rawPoint);
        if (positionWindow.size() > WINDOW) positionWindow.removeFirst();

        bearingWindow.addLast(rawBearingDegrees);
        if (bearingWindow.size() > WINDOW) bearingWindow.removeFirst();

        Point smoothedPoint = averagePoint(positionWindow);
        double smoothedBearing = circularMeanDegrees(bearingWindow);

        for (LocationConsumer consumer : consumers) {
            consumer.onLocationUpdated(new Point[]{smoothedPoint}, NO_OP_ANIMATOR_CONFIG);
            consumer.onBearingUpdated(new double[]{smoothedBearing}, NO_OP_ANIMATOR_CONFIG);
        }
    }

    private static Point averagePoint(Deque<Point> points) {
        double sumLat = 0, sumLng = 0;
        for (Point p : points) {
            sumLat += p.latitude();
            sumLng += p.longitude();
        }
        int n = points.size();
        return Point.fromLngLat(sumLng / n, sumLat / n);
    }

    private static double circularMeanDegrees(Deque<Float> degrees) {
        double sumSin = 0, sumCos = 0;
        for (float d : degrees) {
            double rad = Math.toRadians(d);
            sumSin += Math.sin(rad);
            sumCos += Math.cos(rad);
        }
        double meanDeg = Math.toDegrees(Math.atan2(sumSin, sumCos));
        return (meanDeg + 360) % 360;
    }
}
