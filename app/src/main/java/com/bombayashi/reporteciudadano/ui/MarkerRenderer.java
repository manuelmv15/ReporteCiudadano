package com.bombayashi.reporteciudadano.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Path;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.graphics.ColorUtils;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Geometry;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.QueriedRenderedFeature;
import com.mapbox.maps.RenderedQueryGeometry;
import com.mapbox.maps.RenderedQueryOptions;
import com.mapbox.maps.ScreenBox;
import com.mapbox.maps.ScreenCoordinate;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.expressions.generated.Expression;
import com.mapbox.maps.extension.style.image.ImageExtensionImpl;
import com.mapbox.maps.extension.style.image.ImageUtils;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer;
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.maps.plugin.animation.CameraAnimationsUtils;
import com.mapbox.maps.plugin.gestures.GesturesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Dibuja los reportes como una única fuente GeoJSON con capas de estilo
 * (clustering nativo + expressions), en vez de una anotación Java por reporte.
 * La ubicación del usuario la maneja LocationComponentPlugin, no esta clase.
 */
class MarkerRenderer {

    interface ClickListener {
        void onReportClicked(ReportResponse.ReportData report);
    }

    private static final String REPORTS_SOURCE_ID = "reports-source";
    private static final String REPORTS_LAYER_ID = "reports-layer";
    private static final String CLUSTERS_CIRCLE_LAYER_ID = "reports-clusters-circle";
    private static final String CLUSTERS_COUNT_LAYER_ID = "reports-clusters-count";
    /** Zoom máximo al que se agrupan pines; por encima se dibujan sueltos. */
    private static final long CLUSTER_MAX_ZOOM = 17L;
    /** Tope de reportes en el selector de pines solapados. */
    private static final long MAX_CHOOSER_ITEMS = 50L;

    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private final Context context;
    private final Map<String, ReportResponse.ReportData> reportMarkers;
    private final Predicate<ReportResponse.ReportData> passesFilters;
    private final ClickListener clickListener;

    private MapboxMap mapboxMap;
    private Style style;
    private GeoJsonSource reportsSource;
    private final Map<Integer, Feature> reportFeatures = new HashMap<>();
    private final Set<String> registeredImageIds = new HashSet<>();

    private final android.util.LruCache<String, Bitmap> bitmapCache = new android.util.LruCache<>(100);

    MarkerRenderer(Context context,
                   Map<String, ReportResponse.ReportData> reportMarkers,
                   Predicate<ReportResponse.ReportData> passesFilters,
                   ClickListener clickListener) {
        this.context = context;
        this.reportMarkers = reportMarkers;
        this.passesFilters = passesFilters;
        this.clickListener = clickListener;
    }

    /** Último punto de pantalla donde se levantó el dedo (px, relativo al MapView). */
    private ScreenCoordinate lastTouch;
    /** Margen alrededor del toque para la consulta, así no hace falta acertarle al pixel exacto. */
    private static final float TAP_SLOP_DP = 10f;

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    void init(MapView mapView, Style style) {
        this.mapboxMap = mapView.getMapboxMap();
        this.style = style;
        registeredImageIds.clear();
        reportFeatures.clear();

        setupReportsSourceAndLayers(style);

        // El click de Mapbox solo entrega la coordenada geográfica. Volver a pasarla a pantalla
        // (pixelForCoordinate) no da el mismo pixel con el mapa inclinado y zoom alto (a zoom 22
        // con pitch 45° se desfasaba ~350 px y el toque no encontraba el pin), así que se guarda
        // el punto real del dedo. Devuelve false: no consume el evento, Mapbox lo sigue procesando.
        mapView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
                lastTouch = new ScreenCoordinate(event.getX(), event.getY());
            }
            return false;
        });

        GesturesUtils.getGestures(mapView).addOnMapClickListener(point -> {
            handleMapClick(point);
            return true;
        });
    }

    // ---------------------------------------------------------------------
    // Fuente GeoJSON + capas: categoría/estado vienen "horneados" en el
    // bitmap registrado (mismo diseño que antes), pero el clustering, el
    // escalado por zoom y el pintado corren nativamente en el motor de
    // estilos de Mapbox — no en un loop de Java por cada marcador.
    // ---------------------------------------------------------------------

    private void setupReportsSourceAndLayers(Style style) {
        if (style.styleLayerExists(CLUSTERS_COUNT_LAYER_ID)) style.removeStyleLayer(CLUSTERS_COUNT_LAYER_ID);
        if (style.styleLayerExists(CLUSTERS_CIRCLE_LAYER_ID)) style.removeStyleLayer(CLUSTERS_CIRCLE_LAYER_ID);
        if (style.styleLayerExists(REPORTS_LAYER_ID)) style.removeStyleLayer(REPORTS_LAYER_ID);
        if (style.styleSourceExists(REPORTS_SOURCE_ID)) style.removeStyleSource(REPORTS_SOURCE_ID);

        reportsSource = new GeoJsonSource.Builder(REPORTS_SOURCE_ID)
                .featureCollection(FeatureCollection.fromFeatures(new ArrayList<>()))
                .cluster(true)
                .clusterRadius(55L)
                .clusterMaxZoom(CLUSTER_MAX_ZOOM)
                .build();
        reportsSource.bindTo(style);

        // Mismo factor de escala por zoom que antes (1.1 * 1.06^(zoom-15)),
        // ahora expresado de forma declarativa en vez de recorrer anotaciones.
        Expression zoomSize = Expression.fromRaw(
                "[\"interpolate\", [\"exponential\", 1.06], [\"zoom\"], 13, 0.979, 20, 1.472]");

        SymbolLayer reportsLayer = new SymbolLayer(REPORTS_LAYER_ID, REPORTS_SOURCE_ID);
        reportsLayer.filter(Expression.fromRaw("[\"!\", [\"has\", \"point_count\"]]"));
        reportsLayer.iconImage(Expression.fromRaw("[\"get\", \"iconId\"]"));
        reportsLayer.iconSize(zoomSize);
        reportsLayer.iconAnchor(IconAnchor.BOTTOM);
        reportsLayer.iconAllowOverlap(true);
        reportsLayer.iconIgnorePlacement(true);
        reportsLayer.bindTo(style);

        CircleLayer clustersCircle = new CircleLayer(CLUSTERS_CIRCLE_LAYER_ID, REPORTS_SOURCE_ID);
        clustersCircle.filter(Expression.fromRaw("[\"has\", \"point_count\"]"));
        clustersCircle.circleColor("#4A5D8D");
        clustersCircle.circleRadius(Expression.fromRaw(
                "[\"step\", [\"get\", \"point_count\"], 16.0, 10, 20.0, 30, 26.0]"));
        clustersCircle.circleStrokeWidth(3.0);
        clustersCircle.circleStrokeColor("#FFFFFF");
        clustersCircle.circleOpacity(0.92);
        clustersCircle.bindTo(style);

        SymbolLayer clustersCount = new SymbolLayer(CLUSTERS_COUNT_LAYER_ID, REPORTS_SOURCE_ID);
        clustersCount.filter(Expression.fromRaw("[\"has\", \"point_count\"]"));
        clustersCount.textField(Expression.fromRaw("[\"get\", \"point_count_abbreviated\"]"));
        clustersCount.textColor("#FFFFFF");
        clustersCount.textSize(13.0);
        clustersCount.textAllowOverlap(true);
        clustersCount.textIgnorePlacement(true);
        clustersCount.bindTo(style);
    }

    private void pushFeatures() {
        if (reportsSource == null) return;
        reportsSource.featureCollection(FeatureCollection.fromFeatures(new ArrayList<>(reportFeatures.values())));
    }

    private void ensureImageRegistered(String imageId, Bitmap bitmap) {
        if (style == null || registeredImageIds.contains(imageId)) return;
        ImageExtensionImpl image = new ImageExtensionImpl.Builder(imageId, bitmap).build();
        ImageUtils.addImage(style, image);
        registeredImageIds.add(imageId);
    }

    // ---------------------------------------------------------------------
    // Click: no hay un listener por anotación, así que se consulta qué
    // feature(s) hay bajo el dedo en las capas de reportes/clusters.
    // ---------------------------------------------------------------------

    private void handleMapClick(Point point) {
        if (mapboxMap == null) return;
        ScreenCoordinate screen = (lastTouch != null) ? lastTouch : mapboxMap.pixelForCoordinate(point);
        double slop = TAP_SLOP_DP * context.getResources().getDisplayMetrics().density;
        ScreenBox box = new ScreenBox(
                new ScreenCoordinate(screen.getX() - slop, screen.getY() - slop),
                new ScreenCoordinate(screen.getX() + slop, screen.getY() + slop));
        RenderedQueryOptions opts = new RenderedQueryOptions(
                Arrays.asList(REPORTS_LAYER_ID, CLUSTERS_CIRCLE_LAYER_ID, CLUSTERS_COUNT_LAYER_ID), null);

        mapboxMap.queryRenderedFeatures(new RenderedQueryGeometry(box), opts, expected -> {
            if (!expected.isValue()) return;
            List<QueriedRenderedFeature> results = expected.getValue();
            if (results == null || results.isEmpty()) return;

            // Pines solapados: todos los reportes bajo el dedo, no solo el de arriba.
            List<ReportResponse.ReportData> tapped = new ArrayList<>();
            Feature cluster = null;
            for (QueriedRenderedFeature result : results) {
                Feature feature = result.getQueriedFeature().getFeature();
                if (feature == null) continue;
                if (feature.hasProperty("point_count")) {
                    if (cluster == null) cluster = feature;
                } else {
                    ReportResponse.ReportData report = reportFromFeature(feature);
                    if (report != null && !tapped.contains(report)) tapped.add(report);
                }
            }

            if (!tapped.isEmpty()) {
                openReports(tapped);
            } else if (cluster != null) {
                expandCluster(cluster);
            }
        });
    }

    private void expandCluster(Feature clusterFeature) {
        if (mapboxMap == null) return;
        Geometry geometry = clusterFeature.geometry();
        if (!(geometry instanceof Point)) return;
        Point center = (Point) geometry;

        mapboxMap.getGeoJsonClusterExpansionZoom(REPORTS_SOURCE_ID, clusterFeature, expected -> {
            if (!expected.isValue() || mapboxMap == null) return;
            Object contents = expected.getValue().getValue().getContents();

            // Si el cluster solo se separa pasando CLUSTER_MAX_ZOOM, sus reportes están
            // (casi) en el mismo punto: hacer zoom no los separa, así que se elige de una lista.
            if (contents instanceof Number && ((Number) contents).doubleValue() > CLUSTER_MAX_ZOOM) {
                showClusterLeaves(clusterFeature);
                return;
            }

            double targetZoom = (contents instanceof Number)
                    ? ((Number) contents).doubleValue() + 0.3
                    : mapboxMap.getCameraState().getZoom() + 2.0;

            CameraOptions cam = new CameraOptions.Builder().center(center).zoom(targetZoom).build();
            CameraAnimationsUtils.easeTo(mapboxMap, cam, null);
        });
    }

    private void showClusterLeaves(Feature clusterFeature) {
        if (mapboxMap == null) return;
        mapboxMap.getGeoJsonClusterLeaves(REPORTS_SOURCE_ID, clusterFeature, MAX_CHOOSER_ITEMS, 0L, expected -> {
            if (!expected.isValue()) return;
            List<Feature> leaves = expected.getValue().getFeatureCollection();
            if (leaves == null) return;

            List<ReportResponse.ReportData> reports = new ArrayList<>();
            for (Feature leaf : leaves) {
                ReportResponse.ReportData report = reportFromFeature(leaf);
                if (report != null && !reports.contains(report)) reports.add(report);
            }
            openReports(reports);
        });
    }

    private ReportResponse.ReportData reportFromFeature(Feature feature) {
        if (!feature.hasProperty("reportId")) return null;
        int reportId = feature.getNumberProperty("reportId").intValue();
        return reportMarkers.get(String.valueOf(reportId));
    }

    /** Un solo reporte se abre directo; si hay varios en el mismo lugar, se elige de una lista. */
    private void openReports(List<ReportResponse.ReportData> reports) {
        if (reports.isEmpty()) return;
        mainHandler.post(() -> {
            if (reports.size() == 1) {
                clickListener.onReportClicked(reports.get(0));
            } else {
                showReportChooser(reports);
            }
        });
    }

    private void showReportChooser(List<ReportResponse.ReportData> reports) {
        try {
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(context);
            float density = context.getResources().getDisplayMetrics().density;

            android.widget.LinearLayout list = new android.widget.LinearLayout(context);
            list.setOrientation(android.widget.LinearLayout.VERTICAL);
            int padH = (int) (20 * density);
            list.setPadding(padH, (int) (8 * density), padH, 0);

            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.addView(list);

            androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.map_overlapping_reports_title, reports.size()))
                    .setView(scroll)
                    .setNegativeButton("Cancelar", null)
                    .create();

            for (ReportResponse.ReportData report : reports) {
                android.view.View row = inflater.inflate(R.layout.item_overlapping_report, list, false);
                bindChooserRow(row, report);
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    clickListener.onReportClicked(report);
                });
                list.addView(row);
            }

            dialog.show();
        } catch (RuntimeException e) {
            // Contexto sin ventana (fragment ya desmontado): no hay dónde mostrar el diálogo.
            android.util.Log.w("MarkerRenderer", "No se pudo mostrar el selector de reportes", e);
        }
    }

    private void bindChooserRow(android.view.View row, ReportResponse.ReportData report) {
        String slug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
        String name = (report.getCategory() != null && report.getCategory().getName() != null)
                ? report.getCategory().getName() : "Reporte";

        android.widget.ImageView icon = row.findViewById(R.id.ivCategoryIcon);
        icon.setImageResource(getCategoryDrawableId(slug));
        icon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(context, getCategoryColor(slug))));

        ((android.widget.TextView) row.findViewById(R.id.tvCategory)).setText(name);

        android.widget.TextView description = row.findViewById(R.id.tvDescription);
        String text = report.getDescription();
        boolean hasText = text != null && !text.isBlank() && !"Reporte desde app".equals(text);
        description.setVisibility(hasText ? android.view.View.VISIBLE : android.view.View.GONE);
        if (hasText) description.setText(text);

        ((android.widget.TextView) row.findViewById(R.id.tvConfirmCount)).setText(String.valueOf(report.getVotesConfirm()));
        ((android.widget.TextView) row.findViewById(R.id.tvResolveCount)).setText(String.valueOf(report.getVotesResolve()));

        // Píldora tonal: texto en el color del estado y fondo del mismo color al ~15%, como en Mis reportes.
        String status = report.getStatus() != null ? report.getStatus() : "pending";
        int statusColor = androidx.core.content.ContextCompat.getColor(context, statusColorRes(status));
        android.widget.TextView pill = row.findViewById(R.id.tvStatus);
        pill.setText(statusLabel(status));
        pill.setTextColor(statusColor);
        pill.setBackgroundTintList(android.content.res.ColorStateList.valueOf((statusColor & 0x00FFFFFF) | 0x26000000));

        row.setContentDescription(name + ", " + statusLabel(status));
    }

    private static int statusColorRes(String status) {
        switch (status) {
            case "pending": return R.color.status_pending;
            case "verified": return R.color.status_verified;
            case "resolved": return R.color.status_resolved;
            default: return R.color.status_archived;
        }
    }

    private static String statusLabel(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending": return "Pendiente";
            case "verified": return "Verificado";
            case "resolved": return "Resuelto";
            case "archived": return "Archivado";
            default: return status;
        }
    }

    // ---------------------------------------------------------------------
    // API usada por MapFragment — misma firma que antes salvo init().
    // ---------------------------------------------------------------------

    void addReport(ReportResponse.ReportData report, int currentUserId) {
        reportMarkers.put(String.valueOf(report.getId()), report);

        if (!passesFilters.test(report)) {
            reportFeatures.remove(report.getId());
            pushFeatures();
            return;
        }

        String categorySlug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
        int drawableId = getCategoryDrawableId(categorySlug);
        int colorRes = getCategoryColor(categorySlug);
        boolean isMine = (currentUserId != -1 && currentUserId == report.getUserId());
        String userVote = report.getUserVote();
        String status = report.getStatus();

        String imageId = iconKey(drawableId, colorRes, status, isMine, userVote);
        if (!registeredImageIds.contains(imageId)) {
            Bitmap bitmap = bitmapFromDrawable(drawableId, colorRes, status, isMine, userVote);
            if (bitmap == null) return;
            ensureImageRegistered(imageId, bitmap);
        }

        Point point = Point.fromLngLat(report.getLongitude(), report.getLatitude());
        Feature feature = Feature.fromGeometry(point);
        feature.addNumberProperty("reportId", report.getId());
        feature.addStringProperty("iconId", imageId);

        reportFeatures.put(report.getId(), feature);
        pushFeatures();
    }

    void removeReport(int reportId) {
        reportFeatures.remove(reportId);
        pushFeatures();
    }

    void updateReport(int reportId, String newStatus, int confirmCount, int resolveCount, int currentUserId) {
        ReportResponse.ReportData report = reportMarkers.get(String.valueOf(reportId));
        if (report == null) return;
        report.setStatus(newStatus.toLowerCase());
        if (report.getVotes() != null) {
            report.getVotes().setConfirm(confirmCount);
            report.getVotes().setResolve(resolveCount);
        }
        addReport(report, currentUserId);
    }

    void removeOutsideBounds(double latMin, double latMax, double lngMin, double lngMax) {
        boolean changed = false;
        for (Map.Entry<String, ReportResponse.ReportData> entry : new ArrayList<>(reportMarkers.entrySet())) {
            ReportResponse.ReportData r = entry.getValue();
            boolean inBounds = r.getLatitude() >= latMin && r.getLatitude() <= latMax
                    && r.getLongitude() >= lngMin && r.getLongitude() <= lngMax;
            if (!inBounds) {
                reportFeatures.remove(r.getId());
                reportMarkers.remove(entry.getKey());
                changed = true;
            }
        }
        if (changed) pushFeatures();
    }

    void clearAll() {
        reportFeatures.clear();
        pushFeatures();
    }

    /** El escalado por zoom ahora lo resuelve la expression de icon-size en la capa. */
    void updateScale(double zoom) {
        // no-op: antes recorría cada anotación en Java, ahora es declarativo en el estilo.
    }

    // ---------------------------------------------------------------------
    // Dibujo del bitmap de cada pin: forma "teardrop" (cabeza + punta = ancla
    // exacta de la coordenada), categoría como color/ícono, estado como
    // badge separado, halo blanco para reportes propios y sombra con blur.
    // Este bitmap se registra una sola vez por combinación (categoría,
    // estado, propio, voto) como imagen de estilo — no por cada reporte.
    // ---------------------------------------------------------------------

    private static String iconKey(int drawableId, int colorRes, String status, boolean isMine, String userVote) {
        return "pin_" + drawableId + "_" + colorRes + "_" + status + "_" + isMine + "_" + userVote;
    }

    /** La punta del teardrop llega hasta cy + r*TAIL_LENGTH_FACTOR — ver H en bitmapFromDrawable. */
    static final float TAIL_LENGTH_FACTOR = 1.3f;

    private static Path buildPinPath(float cx, float cy, float r) {
        Path head = new Path();
        head.addCircle(cx, cy, r, Path.Direction.CW);

        // Cola corta y angosta, con los costados curvos (en vez de rectos) para que
        // se sienta "cute"/redondeada y no un carámbano exagerado.
        float tailHalfWidth = r * 0.32f;
        float tailBaseY = cy + r * 0.6f;
        float tipY = cy + r * TAIL_LENGTH_FACTOR;
        float curveY = tailBaseY + (tipY - tailBaseY) * 0.6f;

        Path tail = new Path();
        tail.moveTo(cx - tailHalfWidth, tailBaseY);
        tail.quadTo(cx - tailHalfWidth * 0.2f, curveY, cx, tipY);
        tail.quadTo(cx + tailHalfWidth * 0.2f, curveY, cx + tailHalfWidth, tailBaseY);
        tail.close();

        Path pin = new Path(head);
        pin.op(tail, Path.Op.UNION);
        return pin;
    }

    private Bitmap bitmapFromDrawable(@DrawableRes int drawableId, @ColorRes int colorRes, String status, boolean isMine, String userVote) {
        String key = iconKey(drawableId, colorRes, status, isMine, userVote);
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;

        try {
            android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(context, drawableId);
            if (d == null) return null;

            final float CX = 78f;
            final float CY = 66f;
            final float R_OUTER = 42f;
            final int W = 156;
            final int H = (int) (CY + R_OUTER * TAIL_LENGTH_FACTOR); // la punta toca el borde inferior = ancla exacta

            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);

            float grayBlend = statusGrayBlend(status);
            int baseAlpha = statusAlpha(status);

            int categoryColor = androidx.core.content.ContextCompat.getColor(context, colorRes);
            if (grayBlend > 0f) {
                categoryColor = ColorUtils.blendARGB(categoryColor, android.graphics.Color.rgb(150, 150, 150), grayBlend);
            }
            int fillAlpha = baseAlpha;
            if (userVote != null && !isMine) fillAlpha = (int) (fillAlpha * 0.72f);

            // Sombra con blur real (en vez del óvalo plano anterior)
            Path shadowPath = buildPinPath(CX, CY + 2f, R_OUTER);
            android.graphics.Paint shadowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(android.graphics.Color.BLACK);
            shadowPaint.setAlpha(70);
            shadowPaint.setMaskFilter(new BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL));
            canvas.drawPath(shadowPath, shadowPaint);

            float rActual = R_OUTER;

            Path pin = buildPinPath(CX, CY, rActual);
            android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            fill.setColor(categoryColor);
            fill.setAlpha(fillAlpha);
            canvas.drawPath(pin, fill);

            try { d.setTint(android.graphics.Color.WHITE); } catch (Exception ignored) {}
            int iconSize = (int) (rActual * 1.15f);
            int offX = (int) (CX - iconSize / 2f);
            int offY = (int) (CY - iconSize / 2f);
            d.setBounds(offX, offY, offX + iconSize, offY + iconSize);
            d.draw(canvas);

            // "Mío" ya no es un halo aparte: es un acabado vidrioso sobre el mismo pin
            // (brillo diagonal + filo sutil), los ajenos quedan mate. Es una diferencia
            // de material, no una forma extra.
            if (isMine) {
                canvas.save();
                canvas.clipPath(pin);
                android.graphics.RadialGradient sheenShader = new android.graphics.RadialGradient(
                        CX - rActual * 0.32f, CY - rActual * 0.42f, rActual * 1.35f,
                        new int[]{0x59FFFFFF, 0x14FFFFFF, 0x00FFFFFF},
                        new float[]{0f, 0.55f, 1f},
                        android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Paint sheenPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                sheenPaint.setShader(sheenShader);
                canvas.drawPath(pin, sheenPaint);
                canvas.restore();

                android.graphics.Paint rim = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                rim.setStyle(android.graphics.Paint.Style.STROKE);
                rim.setStrokeWidth(2.2f);
                rim.setColor(0x80FFFFFF);
                canvas.drawPath(pin, rim);
            }

            // El estado (pending/verified/resolved/archived) ya no tiene su propio badge
            // en el pin: un punto de color solo no dice nada sin un texto al lado, y el
            // estado real se ve clarito al tocar el reporte. Acá solo sigue afectando el
            // atenuado (resolved/archived) que ya se calculó arriba.

            // Badge de "ya voté" (esquina inferior derecha), si corresponde
            if (userVote != null && !isMine) {
                float vr = 12f;
                float vx = CX + rActual * 0.5f;
                float vy = CY + rActual * 0.5f;

                drawGlossyBadge(canvas, vx, vy, vr, android.graphics.Color.parseColor("#4CAF50"));

                android.graphics.Paint check = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                check.setColor(android.graphics.Color.WHITE);
                check.setStyle(android.graphics.Paint.Style.STROKE);
                check.setStrokeWidth(3.2f);
                check.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                check.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                Path checkPath = new Path();
                checkPath.moveTo(vx - 5.6f, vy);
                checkPath.lineTo(vx - 1.6f, vy + 4.4f);
                checkPath.lineTo(vx + 6f, vy - 5.2f);
                canvas.drawPath(checkPath, check);
            }

            bitmapCache.put(key, bmp);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** Insignia tipo "esmalte": sombra propia + relleno con gradiente en vez de color plano. */
    private static void drawGlossyBadge(Canvas canvas, float cx, float cy, float radius, int baseColor) {
        android.graphics.Paint shadow = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(android.graphics.Color.BLACK);
        shadow.setAlpha(55);
        shadow.setMaskFilter(new BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawCircle(cx, cy + 1.5f, radius, shadow);

        android.graphics.Paint border = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        border.setColor(android.graphics.Color.WHITE);
        canvas.drawCircle(cx, cy, radius + 1.8f, border);

        int highlight = ColorUtils.blendARGB(baseColor, android.graphics.Color.WHITE, 0.5f);
        android.graphics.RadialGradient shader = new android.graphics.RadialGradient(
                cx - radius * 0.35f, cy - radius * 0.35f, radius * 1.6f,
                new int[]{highlight, baseColor},
                new float[]{0f, 1f},
                android.graphics.Shader.TileMode.CLAMP);
        android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        fill.setShader(shader);
        canvas.drawCircle(cx, cy, radius, fill);
    }

    private static float statusGrayBlend(String status) {
        if ("resolved".equals(status)) return 0.35f;
        if ("archived".equals(status)) return 0.55f;
        return 0f;
    }

    private static int statusAlpha(String status) {
        if ("resolved".equals(status)) return 210;
        if ("archived".equals(status)) return 165;
        return 255;
    }

    static @DrawableRes int getCategoryDrawableId(String slug) {
        return switch (slug) {
            case "bache", "vialidad"              -> R.drawable.ic_category_vialidad;
            case "alumbrado-publico", "alumbrado" -> R.drawable.ic_category_alumbrado;
            case "fuga-de-agua", "agua"            -> R.drawable.ic_category_agua;
            case "senalizacion-vial", "senalizacion", "semaforo-danado" -> R.drawable.ic_category_senalizacion;
            case "inseguridad", "seguridad"        -> R.drawable.ic_category_seguridad;
            case "basura-acumulada", "parques", "basura" -> R.drawable.ic_category_basura;
            default                                -> R.drawable.ic_category_otros;
        };
    }

    static @ColorRes int getCategoryColor(String slug) {
        return switch (slug) {
            case "vialidad", "bache"                    -> R.color.category_vialidad;
            case "alumbrado", "alumbrado-publico"       -> R.color.category_alumbrado;
            case "agua", "fuga-de-agua"                 -> R.color.category_agua;
            case "senalizacion", "senalizacion-vial", "semaforo-danado" -> R.color.category_senalizacion;
            case "seguridad", "inseguridad"             -> R.color.category_seguridad;
            case "parques"                              -> R.color.category_parques;
            case "basura", "basura-acumulada"           -> R.color.category_basura;
            default                                     -> R.color.category_otros;
        };
    }
}
