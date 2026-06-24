package com.bombayashi.reporteciudadano.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import androidx.annotation.DrawableRes;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.mapbox.geojson.Point;
import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.AnnotationsUtils;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotation;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

class MarkerRenderer {

    interface ClickListener {
        void onReportClicked(ReportResponse.ReportData report);
    }

    private final Context context;
    private final Map<String, ReportResponse.ReportData> reportMarkers;
    private final Predicate<ReportResponse.ReportData> passesFilters;
    private final ClickListener clickListener;

    private PointAnnotationManager pointManager;
    private CircleAnnotationManager circleManager;
    private PointAnnotation userLocationMarker;

    private final Map<Integer, PointAnnotation> reportIconMarkers = new HashMap<>();
    private final Map<Integer, CircleAnnotation> reportStrokeMarkers = new HashMap<>();
    private final Map<String, Integer> annotationToReportId = new HashMap<>();
    private final Map<String, Integer> coordOccupancy = new HashMap<>();
    private final android.util.LruCache<String, Bitmap> bitmapCache = new android.util.LruCache<>(100);
    private double lastZoomLevel = -1;

    MarkerRenderer(Context context,
                   Map<String, ReportResponse.ReportData> reportMarkers,
                   Predicate<ReportResponse.ReportData> passesFilters,
                   ClickListener clickListener) {
        this.context = context;
        this.reportMarkers = reportMarkers;
        this.passesFilters = passesFilters;
        this.clickListener = clickListener;
    }

    void init(com.mapbox.maps.MapView mapView) {
        AnnotationPlugin plugin = AnnotationsUtils.getAnnotations(mapView);

        circleManager = CircleAnnotationManagerKt.createCircleAnnotationManager(
                plugin, new AnnotationConfig());

        pointManager = PointAnnotationManagerKt.createPointAnnotationManager(
                plugin, new AnnotationConfig());

        pointManager.addClickListener(annotation -> {
            Integer reportId = annotationToReportId.get(annotation.getId());
            if (reportId != null) {
                ReportResponse.ReportData report = reportMarkers.get(String.valueOf(reportId));
                if (report != null) clickListener.onReportClicked(report);
            }
            return true;
        });
    }

    PointAnnotationManager getPointAnnotationManager() { return pointManager; }
    CircleAnnotationManager getCircleAnnotationManager() { return circleManager; }
    Map<String, Integer> getAnnotationToReportId() { return annotationToReportId; }

    void addReport(ReportResponse.ReportData report, int currentUserId) {
        if (pointManager == null || circleManager == null) return;

        removeReport(report.getId());

        if (!passesFilters.test(report)) {
            reportMarkers.put(String.valueOf(report.getId()), report);
            return;
        }

        double lat = report.getLatitude();
        double lng = report.getLongitude();

        String coordKey = String.format(Locale.US, "%.6f,%.6f", lat, lng);
        int slot = coordOccupancy.getOrDefault(coordKey, 0);
        coordOccupancy.put(coordKey, slot + 1);
        if (slot > 0) {
            double angle = (slot - 1) * (2 * Math.PI / 6);
            double offset = 0.00003;
            lat += offset * Math.cos(angle);
            lng += offset * Math.sin(angle);
        }

        String categorySlug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
        Point point = Point.fromLngLat(lng, lat);

        reportMarkers.put(String.valueOf(report.getId()), report);

        boolean isMine = (currentUserId != -1 && currentUserId == report.getUserId());
        String userVote = report.getUserVote();
        Bitmap icon = bitmapFromDrawable(
                getCategoryDrawableId(categorySlug),
                getCategoryColor(categorySlug),
                report.getStatus(),
                isMine,
                userVote
        );
        if (icon == null) return;

        double size = 1.1 * getUserSizeMultiplier(report);
        PointAnnotationOptions opts = new PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(icon)
                .withIconSize(size);

        PointAnnotation pa = pointManager.create(opts);
        pa.setDraggable(false);
        annotationToReportId.put(pa.getId(), report.getId());
        reportIconMarkers.put(report.getId(), pa);
    }

    void removeReport(int reportId) {
        PointAnnotation icon = reportIconMarkers.remove(reportId);
        if (icon != null && pointManager != null) pointManager.delete(icon);
        CircleAnnotation stroke = reportStrokeMarkers.remove(reportId);
        if (stroke != null && circleManager != null) circleManager.delete(stroke);
    }

    void updateReport(int reportId, String newStatus, int confirmCount, int resolveCount, int currentUserId) {
        ReportResponse.ReportData report = reportMarkers.get(String.valueOf(reportId));
        if (report == null) return;
        report.setStatus(newStatus.toLowerCase());
        if (report.getVotes() != null) {
            report.getVotes().setConfirm(confirmCount);
            report.getVotes().setResolve(resolveCount);
        }
        bitmapCache.evictAll();
        addReport(report, currentUserId);
    }

    void addUserLocation(Point point) {
        if (pointManager == null) return;
        
        if (userLocationMarker != null) {
            userLocationMarker.setPoint(point);
            pointManager.update(userLocationMarker);
            return;
        }

        Bitmap arrowBitmap = bitmapCache.get("user_arrow");
        if (arrowBitmap == null) {
            arrowBitmap = drawableToBitmap(context, R.drawable.ic_user_arrow);
            if (arrowBitmap != null) bitmapCache.put("user_arrow", arrowBitmap);
        }
            
        if (arrowBitmap == null) return;

        PointAnnotationOptions opts = new PointAnnotationOptions()
                .withPoint(point)
                .withIconImage(arrowBitmap)
                .withIconSize(1.0)
                .withIconRotate(0f);

        userLocationMarker = pointManager.create(opts);
    }

    private Bitmap drawableToBitmap(Context context, @DrawableRes int drawableId) {
        android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
        if (d == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        d.draw(canvas);
        return bitmap;
    }

    void updateUserHeading(float heading) {
        if (userLocationMarker != null) {
            userLocationMarker.setIconRotate((double) heading);
        }
    }

    void removeOutsideBounds(double latMin, double latMax, double lngMin, double lngMax) {
        for (Map.Entry<String, ReportResponse.ReportData> entry : new java.util.ArrayList<>(reportMarkers.entrySet())) {
            ReportResponse.ReportData r = entry.getValue();
            boolean inBounds = r.getLatitude() >= latMin && r.getLatitude() <= latMax
                    && r.getLongitude() >= lngMin && r.getLongitude() <= lngMax;
            if (!inBounds) {
                removeReport(r.getId());
                reportMarkers.remove(entry.getKey());
                String coordKey = String.format(Locale.US, "%.6f,%.6f", r.getLatitude(), r.getLongitude());
                Integer count = coordOccupancy.get(coordKey);
                if (count != null && count > 1) coordOccupancy.put(coordKey, count - 1);
                else coordOccupancy.remove(coordKey);
            }
        }
    }

    void clearAll() {
        if (pointManager != null) pointManager.deleteAll();
        if (circleManager != null) circleManager.deleteAll();
        reportIconMarkers.clear();
        reportStrokeMarkers.clear();
        annotationToReportId.clear();
        coordOccupancy.clear();
        userLocationMarker = null;
    }

    void updateScale(double zoom) {
        if (zoom == lastZoomLevel) return;
        lastZoomLevel = zoom;
        float scale = (float) Math.max(0.6, Math.min(1.4, Math.pow(1.06, zoom - 15)));
        double base = 1.1 * scale;
        for (Map.Entry<Integer, PointAnnotation> e : reportIconMarkers.entrySet()) {
            ReportResponse.ReportData r = reportMarkers.get(String.valueOf(e.getKey()));
            double mult = (r != null) ? getUserSizeMultiplier(r) : 1.0;
            e.getValue().setIconSize(base * mult);
        }
        if (!reportIconMarkers.isEmpty()) pointManager.update(pointManager.getAnnotations());
    }

    void animateBounce(PointAnnotation annotation) {
        float base = 1.6f;
        android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofFloat(base, base * 1.4f, base);
        anim.setDuration(400);
        anim.setInterpolator(new android.view.animation.OvershootInterpolator());
        anim.addUpdateListener(a -> {
            annotation.setIconSize(((Float) a.getAnimatedValue()).doubleValue());
            pointManager.update(annotation);
        });
        anim.start();
    }

    private Bitmap bitmapFromDrawable(@DrawableRes int drawableId, String colorHex, String status, boolean isMine, String userVote) {
        String key = drawableId + "_" + colorHex + "_" + status + "_" + isMine + "_" + userVote;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;

        try {
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
            if (d == null) return null;

            final int SIZE = 130;
            Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            float center = SIZE / 2f;
            float radius = SIZE / 2.8f;

            android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            fill.setColor(android.graphics.Color.parseColor(colorHex));
            if (userVote != null && !isMine) fill.setAlpha(160);

            android.graphics.Paint border = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            border.setStyle(android.graphics.Paint.Style.STROKE);
            border.setStrokeWidth(6f);
            border.setColor(android.graphics.Color.parseColor(statusStrokeColor(status)));

            android.graphics.Paint shadow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(android.graphics.Color.BLACK);
            shadow.setAlpha(40);

            if (isMine) {
                // Forma diamante para reportes propios
                android.graphics.Path diamond = new android.graphics.Path();
                diamond.moveTo(center, center - radius);         // top
                diamond.lineTo(center + radius, center);         // right
                diamond.lineTo(center, center + radius);         // bottom
                diamond.lineTo(center - radius, center);         // left
                diamond.close();

                android.graphics.Path shadowDiamond = new android.graphics.Path();
                shadowDiamond.moveTo(center, center - radius + 4);
                shadowDiamond.lineTo(center + radius, center + 4);
                shadowDiamond.lineTo(center, center + radius + 4);
                shadowDiamond.lineTo(center - radius, center + 4);
                shadowDiamond.close();
                canvas.drawPath(shadowDiamond, shadow);
                canvas.drawPath(diamond, fill);
                canvas.drawPath(diamond, border);
            } else {
                // Forma círculo para reportes de otros
                canvas.drawCircle(center, center + 4, radius, shadow);
                canvas.drawCircle(center, center, radius, fill);
                canvas.drawCircle(center, center, radius, border);
            }

            try { d.setTint(android.graphics.Color.WHITE); } catch (Exception ignored) {}
            int iconSize = (int) (radius * 1.1f);
            int off = (int) (center - iconSize / 2f);
            d.setBounds(off, off, off + iconSize, off + iconSize);
            d.draw(canvas);

            // Badge: checkmark verde si ya votó (y no es suyo)
            if (userVote != null && !isMine) {
                float badgeRadius = 18f;
                float bx = center + radius * 0.65f;
                float by = center - radius * 0.65f;

                android.graphics.Paint badgeBg = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                badgeBg.setColor(android.graphics.Color.parseColor("#4CAF50"));
                canvas.drawCircle(bx, by, badgeRadius, badgeBg);

                android.graphics.Paint badgeBorder = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                badgeBorder.setStyle(android.graphics.Paint.Style.STROKE);
                badgeBorder.setStrokeWidth(3f);
                badgeBorder.setColor(android.graphics.Color.WHITE);
                canvas.drawCircle(bx, by, badgeRadius, badgeBorder);

                // Dibujar checkmark ✓
                android.graphics.Paint check = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                check.setColor(android.graphics.Color.WHITE);
                check.setStyle(android.graphics.Paint.Style.STROKE);
                check.setStrokeWidth(4f);
                check.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                check.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(bx - 8f, by);
                path.lineTo(bx - 2f, by + 6f);
                path.lineTo(bx + 8f, by - 7f);
                canvas.drawPath(path, check);
            }

            bitmapCache.put(key, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private static String statusStrokeColor(String status) {
        if (status == null) return "#757575";
        return switch (status) {
            case "pending"  -> "#FF9800";
            case "verified" -> "#4CAF50";
            case "resolved" -> "#2196F3";
            case "archived" -> "#9E9E9E";
            default         -> "#757575";
        };
    }

    private static @DrawableRes int getCategoryDrawableId(String slug) {
        return switch (slug) {
            case "bache", "vialidad"              -> R.drawable.remove_road_24px;
            case "alumbrado-publico", "alumbrado" -> R.drawable.backlight_high_off_24px;
            case "fuga-de-agua", "agua"            -> R.drawable.agua;
            case "semaforo-danado", "trafico"      -> R.drawable.traffic_jam_24px;
            case "inseguridad", "seguridad"        -> R.drawable.warning_24px;
            case "basura-acumulada", "parques", "basura" -> R.drawable.trash;
            default                                -> R.drawable.ic_category_otros;
        };
    }

    private static String getCategoryColor(String slug) {
        return switch (slug) {
            case "vialidad", "bache"                    -> "#FF6B6B";
            case "alumbrado", "alumbrado-publico"       -> "#FFD93D";
            case "agua", "fuga-de-agua"                 -> "#6BCB77";
            case "trafico", "semaforo-danado"           -> "#4D96FF";
            case "seguridad", "inseguridad"             -> "#9D4EDD";
            case "parques"                              -> "#06D6A0";
            case "basura", "basura-acumulada"           -> "#8B5A3C";
            default                                     -> "#808080";
        };
    }

    private static double getUserSizeMultiplier(ReportResponse.ReportData report) {
        ReportResponse.UserInfo user = report.getUser();
        if (user == null || user.getLevel() == null) return 1.0;
        return switch (user.getLevel()) {
            case "experto"     -> 1.3;
            case "guardian"    -> 1.2;
            case "colaborador" -> 1.1;
            default            -> 1.0;
        };
    }
}
