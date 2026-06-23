package com.bombayashi.reporteciudadano.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bombayashi.reporteciudadano.LoginActivity;
import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.util.TokenManager;
import com.bombayashi.reporteciudadano.databinding.FragmentMapBinding;
import com.bombayashi.reporteciudadano.model.CreateReportResponse;
import com.bombayashi.reporteciudadano.model.ReportRequest;
import com.bombayashi.reporteciudadano.model.ReportResponse;
import com.bombayashi.reporteciudadano.model.ReportStreamResponse;
import com.bombayashi.reporteciudadano.network.ApiClient;
import com.bombayashi.reporteciudadano.db.AppDatabase;
import com.bombayashi.reporteciudadano.db.PendingActionEntity;
import com.bombayashi.reporteciudadano.db.ReportCacheEntity;
import com.bombayashi.reporteciudadano.util.ConnectivityHelper;
import com.bombayashi.reporteciudadano.work.SyncManager;
import com.bombayashi.reporteciudadano.service.NotificationChannelHelper;
import com.google.gson.Gson;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.common.MapboxOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.plugin.animation.CameraAnimationsUtils;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat;

import com.mapbox.maps.extension.style.layers.LayerUtils;
import com.mapbox.maps.extension.style.layers.generated.CircleLayer;
import com.mapbox.maps.extension.style.layers.generated.ModelLayer;
import com.mapbox.maps.extension.style.sources.SourceUtils;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.maps.plugin.gestures.GesturesUtils;

import java.util.Arrays;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapFragment extends Fragment implements ReportDetailBottomSheet.OnReportStatusChangeListener {

    private FragmentMapBinding binding;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private boolean mapReady = false;
    private Runnable onMapReadyCallback;
    private Point userLocation;

    private MapViewModel vm;
    private MarkerRenderer markerRenderer;
    private LocationTracker locationTracker;
    private NearbyReportChecker nearbyChecker;
    
    private GeoJsonSource user3DSource;
    private ModelLayer user3DLayer;
    private com.bombayashi.reporteciudadano.util.HeadingManager headingManager;

    // Convenience accessors — delegate to ViewModel
    private java.util.Map<String, ReportResponse.ReportData> reportMarkers() { return vm.getReportMarkers(); }

    private static final double DEFAULT_LATITUDE = -34.6037;
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;
    private static final int MAX_REPORTS = 200;
    private static final double MIN_ZOOM_TO_LOAD = 13.0;
    private static final int VIEWPORT_PAGE_SIZE = 50;
    private static final long CAMERA_IDLE_DEBOUNCE_MS = 600;

    // RF-19
    private static final String HEATMAP_SOURCE_ID = "reports-heatmap-source";
    private static final String HEATMAP_LAYER_ID = "reports-heatmap-layer";

    private static final String PREFS_FILTERS = "map_filters";
    private static final String PREF_FILTER_STATUS = "filter_status";
    private static final String PREF_FILTER_AGE = "filter_age";
    private static final String PREF_FILTER_CATEGORIES = "filter_categories";


    private final android.os.Handler cameraIdleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable cameraIdleRunnable;

    private static final long VOTABLE_REPORTS_CHECK_INTERVAL_MS = 30_000;
    private final android.os.Handler votableReportsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable votableReportsRunnable = new Runnable() {
        @Override
        public void run() {
            if (nearbyChecker != null && userLocation != null) {
                int uid = TokenManager.getInstance(requireContext()).getUserId();
                nearbyChecker.check(reportMarkers().values(), userLocation, uid);
            }
            votableReportsHandler.postDelayed(this, VOTABLE_REPORTS_CHECK_INTERVAL_MS);
        }
    };

    private AppDatabase appDatabase;
    private final java.util.concurrent.ExecutorService dbExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Gson gson = new Gson();
    private android.net.ConnectivityManager connectivityManager;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String token = getString(R.string.mapbox_access_token);
        if (!token.isEmpty()) {
            MapboxOptions.setAccessToken(token);
        }
        binding = FragmentMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @com.mapbox.maps.MapboxExperimental
    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.util.Log.d("MapFragment", "=== onViewCreated ===");

        vm = new androidx.lifecycle.ViewModelProvider(this).get(MapViewModel.class);
        appDatabase = AppDatabase.getInstance(requireContext());

        vm.initPoller();

        markerRenderer = new MarkerRenderer(requireContext(), vm.getReportMarkers(), this::passesFilters, this::showReportDetails);
        locationTracker = new LocationTracker(new LocationTracker.Callback() {
            @Override public void onLocationReady(Point location, float accuracy, float bearing) {
                if (!isAdded() || getView() == null) return;
                userLocation = location;
                // addUserLocationMarker(location); // Removido para dejar solo 3D
                updateUser3DMarker(location, bearing);
                animateCameraTo(location);
                SnackbarHelper.show(getView(),
                        String.format(java.util.Locale.getDefault(), "Ubicación obtenida (precisión ±%.0fm)", accuracy),
                        SnackbarHelper.Variant.SUCCESS);
                
                // Onboarding contextual tras obtener ubicación
                checkContextualOnboarding();
            }
            @Override public void onLocationUpdate(Point location, float bearing) {
                if (!isAdded() || getView() == null) return;
                userLocation = location;
                // addUserLocationMarker(location); // Removido para dejar solo 3D
                updateUser3DMarker(location, bearing);
            }
            @Override public void onPermissionDenied() { centerOnDefaultLocation(); }
        });
        locationTracker.init(this);

        nearbyChecker = new NearbyReportChecker(requireContext());

        headingManager = new com.bombayashi.reporteciudadano.util.HeadingManager(requireContext(),
            heading -> {
                if (markerRenderer != null) {
                    markerRenderer.updateUserHeading(heading);
                }
            });

        // Observe report updates from ViewModel (poller runs during rotation)
        vm.reportUpdated.observe(getViewLifecycleOwner(), report -> {
            if (report == null || markerRenderer == null) return;
            int uid = TokenManager.getInstance(requireContext()).getUserId();
            String key = String.valueOf(report.getId());
            ReportResponse.ReportData cached = vm.getReportMarkers().get(key);
            if (cached == null || cached == report) {
                markerRenderer.addReport(report, uid);
            } else {
                markerRenderer.updateReport(report.getId(), report.getStatus(),
                        report.getVotesConfirm(), report.getVotesResolve(), uid);
            }
        });

        if (ConnectivityHelper.isOnline(requireContext())) SyncManager.syncNow(requireContext());
        registerConnectivityCallback();
        observeSyncWork();
        loadFilterPrefs();
        com.bombayashi.reporteciudadano.network.ApiClient.setOnUnauthorized(() ->
            requireActivity().runOnUiThread(this::handleExpiredSession));

        mapView = binding.mapView;

        mapView.getMapboxMap().loadStyleUri(Style.STANDARD, style -> {
            mapboxMap = mapView.getMapboxMap();
            android.util.Log.d("MapFragment", "1. Estilo cargado");

            // Topografía 3D
            com.mapbox.maps.extension.style.sources.generated.RasterDemSource demSource =
                    new com.mapbox.maps.extension.style.sources.generated.RasterDemSource.Builder("mapbox-dem")
                            .url("mapbox://mapbox.mapbox-terrain-dem-v1")
                            .tileSize(512)
                            .maxzoom(14L)
                            .build();
            demSource.bindTo(style);

            com.mapbox.maps.extension.style.terrain.generated.Terrain terrain =
                    new com.mapbox.maps.extension.style.terrain.generated.Terrain("mapbox-dem")
                            .exaggeration(1.5);
            terrain.bindTo(style);


            // Luz dinámica según hora del día
            applyDynamicLighting();

            // Tarea 1 & 2: Configuración del Marcador 3D del Usuario
            setupUser3DLayer(style);

            android.util.Log.d("MapFragment", "2. Inicializando components...");
            setupAnnotationManager();
            // if (userLocation != null) addUserLocationMarker(userLocation); // Removido para dejar solo 3D
            setupMapListeners();
            setupFAB();
            requestUserLocation();
            showLongPressHintIfFirstTime();
            prefetchHeatmapPoints();
            mapReady = true;
            if (onMapReadyCallback != null) {
                onMapReadyCallback.run();
                onMapReadyCallback = null;
            }
            if (binding != null) binding.pbMapLoading.setVisibility(View.GONE);

            android.util.Log.d("MapFragment", "3. Cargando reportes...");
            if (!vm.getReportMarkers().isEmpty()) {
                // Data survived rotation — re-draw from ViewModel, no API call needed
                repopulateMarkersFromViewModel();
            } else {
                loadReportsFromAPI();
            }
        });
    }

    private void repopulateMarkersFromViewModel() {
        int uid = TokenManager.getInstance(requireContext()).getUserId();
        for (ReportResponse.ReportData report : vm.getReportMarkers().values()) {
            markerRenderer.addReport(report, uid);
        }
        android.util.Log.d("MapFragment", "♻️ " + vm.getReportMarkers().size() + " marcadores re-dibujados tras rotación");
    }

    private void setupAnnotationManager() {
        markerRenderer.init(mapView);

        mapboxMap.subscribeCameraChanged(event -> {
            double zoom = mapboxMap.getCameraState().getZoom();
            markerRenderer.updateScale(zoom);


            if (cameraIdleRunnable != null) cameraIdleHandler.removeCallbacks(cameraIdleRunnable);
            cameraIdleRunnable = this::onViewportChanged;
            cameraIdleHandler.postDelayed(cameraIdleRunnable, CAMERA_IDLE_DEBOUNCE_MS);
        });
    }

    private void requestUserLocation() {
        locationTracker.requestLocation(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        locationTracker.onPermissionResult(this, requestCode, grantResults);
    }

    private void startContinuousLocationTracking() {
        locationTracker.startContinuous(this);
    }

    private void stopContinuousLocationTracking() {
        locationTracker.stopContinuous(this);
    }

    private void addUserLocationMarker(Point point) {
        if (markerRenderer != null) markerRenderer.addUserLocation(point);
    }

    /** Llamado al inicio (carga inicial) y desde onViewportChanged. */
    private void loadReportsFromAPI() {
        if (mapboxMap == null) return;
        onViewportChanged();
    }

    /**
     * Triggered ~600ms after camera stops moving.
     * If zoom < MIN_ZOOM_TO_LOAD: clear markers and show hint.
     * Otherwise: fetch reports within current bounding box.
     */
    @com.mapbox.maps.MapboxExperimental
    private void onViewportChanged() {
        if (!isAdded() || getView() == null || mapboxMap == null) return;

        double zoom = mapboxMap.getCameraState().getZoom();

        if (zoom < MIN_ZOOM_TO_LOAD) {
            // Cámara ya quieta a zoom bajo — mostrar heatmap
            if (markerRenderer != null) markerRenderer.clearAll();
            if (!vm.heatmapEnabled) {
                if (!reportMarkers().isEmpty()) {
                    toggleHeatmap(true);
                } else {
                    fetchAndShowHeatmap();
                }
            }
            return;
        }

        // Cámara quieta a zoom suficiente — apagar heatmap si estaba activo
        if (vm.heatmapEnabled) toggleHeatmap(false);

        if (vm.isLoadingReports) return;

        if (!ConnectivityHelper.isOnline(requireContext())) {
            loadReportsFromCache();
            return;
        }

        com.mapbox.maps.CameraState cs = mapboxMap.getCameraState();
        CameraOptions cameraOptions = new CameraOptions.Builder()
                .center(cs.getCenter())
                .zoom(cs.getZoom())
                .bearing(cs.getBearing())
                .pitch(cs.getPitch())
                .padding(cs.getPadding())
                .build();
        com.mapbox.maps.CoordinateBounds bounds = mapboxMap.coordinateBoundsForCamera(cameraOptions);
        double latMin = bounds.getSouthwest().latitude();
        double latMax = bounds.getNortheast().latitude();
        double lngMin = bounds.getSouthwest().longitude();
        double lngMax = bounds.getNortheast().longitude();

        android.util.Log.d("MapFragment", String.format(Locale.US,
                "📥 Fetch viewport [%.4f,%.4f / %.4f,%.4f] zoom=%.1f",
                latMin, latMax, lngMin, lngMax, zoom));

        vm.isLoadingReports = true;
        if (vm.getPoller().getTimestamp() == null) vm.getPoller().setTimestamp(ReportPoller.nowIso());

        ApiClient.getInstance().getReportsByBounds(latMin, latMax, lngMin, lngMax, VIEWPORT_PAGE_SIZE)
                .enqueue(new Callback<ReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportResponse> call, @NonNull Response<ReportResponse> response) {
                vm.isLoadingReports = false;
                if (!isAdded() || getView() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    java.util.List<ReportResponse.ReportData> reports = response.body().getData();
                    android.util.Log.d("MapFragment", "✓ Viewport reports: " + (reports != null ? reports.size() : 0));

                    if (markerRenderer != null) markerRenderer.removeOutsideBounds(latMin, latMax, lngMin, lngMax);

                    if (reports != null) {
                        int addedCount = 0;
                        boolean limitReached = false;
                        int uid = TokenManager.getInstance(requireContext()).getUserId();
                        for (ReportResponse.ReportData report : reports) {
                            String key = String.valueOf(report.getId());
                            boolean isNew = !reportMarkers().containsKey(key);
                            if (reportMarkers().size() < MAX_REPORTS || !isNew) {
                                if (markerRenderer != null) markerRenderer.addReport(report, uid);
                                cacheReport(report);
                                if (isNew) addedCount++;
                            } else {
                                limitReached = true;
                            }
                        }
                        if (addedCount > 0) {
                            android.util.Log.d("MapFragment", "📍 " + addedCount + " nuevos marcadores en viewport");
                        }
                        if (limitReached) {
                            SnackbarHelper.show(getView(), "Mostrando " + MAX_REPORTS + " reportes más cercanos", SnackbarHelper.Variant.INFO);
                        }
                    }
                } else {
                    android.util.Log.e("MapFragment", "✗ Error HTTP: " + response.code());
                    SnackbarHelper.show(getView(), "Error: " + response.code(), SnackbarHelper.Variant.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReportResponse> call, @NonNull Throwable t) {
                vm.isLoadingReports = false;
                if (!isAdded() || getView() == null) return;
                android.util.Log.e("MapFragment", "❌ Error de red: " + t.getMessage(), t);
                SnackbarHelper.show(getView(), "Error de conexión", SnackbarHelper.Variant.ERROR);
            }
        });
    }


    /** RF-05: dispara sync apenas vuelve la conexión, sin esperar el periodic de 15min. */
    private void registerConnectivityCallback() {
        connectivityManager = (android.net.ConnectivityManager)
                requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull android.net.Network network) {
                android.util.Log.d("MapFragment", "🌐 Conexión recuperada, disparando sync de pendientes");
                SyncManager.syncNow(requireContext().getApplicationContext());
            }
        };
        connectivityManager.registerDefaultNetworkCallback(networkCallback);
    }

    /** RF-05: cuando el worker termina, refresca el mapa y notifica al usuario. */
    private void observeSyncWork() {
        final int[] pendingCountBefore = {0};

        dbExecutor.execute(() -> {
            pendingCountBefore[0] = appDatabase.pendingActionDao().getPending().size();
        });

        androidx.work.WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData("report_sync")
                .observe(getViewLifecycleOwner(), workInfos -> {
                    if (workInfos == null) return;
                    for (androidx.work.WorkInfo info : workInfos) {
                        if (info.getState() == androidx.work.WorkInfo.State.SUCCEEDED) {
                            android.util.Log.d("MapFragment", "✓ Sync de pendientes completado, refrescando mapa");
                            pollForUpdates();

                            dbExecutor.execute(() -> {
                                int remaining = appDatabase.pendingActionDao().getPending().size();
                                int synced = pendingCountBefore[0] - remaining;
                                if (synced > 0 && isAdded() && getView() != null) {
                                    requireActivity().runOnUiThread(() -> {
                                        String msg = synced == 1
                                            ? "1 acción sincronizada"
                                            : synced + " acciones sincronizadas";
                                        SnackbarHelper.show(getView(), msg, SnackbarHelper.Variant.SUCCESS);
                                    });
                                }
                                pendingCountBefore[0] = remaining;
                            });
                        }
                    }
                });
    }

    /** RF-05: guarda el reporte en Room para poder mostrarlo sin conexión. */
    private void cacheReport(ReportResponse.ReportData report) {
        dbExecutor.execute(() -> appDatabase.reportCacheDao().upsert(new ReportCacheEntity(
                String.valueOf(report.getId()),
                report.getLatitude(),
                report.getLongitude(),
                report.getStatus(),
                gson.toJson(report),
                System.currentTimeMillis()
        )));
    }

    /** RF-05: sin conexión, pinta el mapa con la última caché guardada en Room. */
    private void loadReportsFromCache() {
        dbExecutor.execute(() -> {
            java.util.List<ReportCacheEntity> cached = appDatabase.reportCacheDao().getAll();
            if (!isAdded()) return;

            requireActivity().runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                int addedCount = 0;
                for (ReportCacheEntity entity : cached) {
                    if (reportMarkers().size() >= MAX_REPORTS) break;
                    try {
                        ReportResponse.ReportData report = gson.fromJson(entity.json, ReportResponse.ReportData.class);
                        addReportMarker(report);
                        addedCount++;
                    } catch (Exception e) {
                        android.util.Log.e("MapFragment", "Error parseando reporte cacheado: " + e.getMessage());
                    }
                }
                android.util.Log.d("MapFragment", "📦 " + addedCount + " reportes cargados desde caché offline");
                if (addedCount > 0) {
                    SnackbarHelper.show(getView(), "Sin conexión: mostrando " + addedCount + " reportes guardados",
                            SnackbarHelper.Variant.INFO);
                }
            });
        });
    }

    private void pollForUpdates() {
        if (vm.getPoller() != null) vm.getPoller().pollNow();
    }

    /** RF-18: reportes archivados nunca se pintan en el mapa. RF-19: filtros de categoría/estado/antigüedad. */
    private boolean passesFilters(ReportResponse.ReportData report) {
        String status = report.getStatus();
        if ("archived".equalsIgnoreCase(status)) return false;

        if (!vm.filterStatus.equals("all") && !vm.filterStatus.equalsIgnoreCase(status)) return false;

        if (!vm.filterCategories.isEmpty()) {
            String slug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
            if (!vm.filterCategories.contains(normalizeCategoryGroup(slug))) return false;
        }

        if (!vm.filterAge.equals("all")) {
            long ageHours = reportAgeHours(report.getCreatedAt());
            int maxHours = switch (vm.filterAge) {
                case "1h" -> 1;
                case "6h" -> 6;
                case "24h" -> 24;
                default -> Integer.MAX_VALUE;
            };
            if (ageHours > maxHours) return false;
        }

        return true;
    }

    /** Agrupa los distintos slugs de categoría en los 7 grupos usados por el filtro. */
    private String normalizeCategoryGroup(String categorySlug) {
        return com.bombayashi.reporteciudadano.util.CategoryMapper.toFilterGroup(categorySlug);
    }

    /** Horas transcurridas desde created_at (ISO-8601). Devuelve 0 si no se puede parsear. */
    private long reportAgeHours(String createdAtIso) {
        if (createdAtIso == null) return 0;
        try {
            // Reemplazo de java.time.Instant para compatibilidad con API 24
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = sdf.parse(createdAtIso);
            if (date == null) return 0;
            return Math.max(0, (System.currentTimeMillis() - date.getTime()) / (60 * 60 * 1000));
        } catch (Exception e) {
            android.util.Log.w("MapFragment", "No se pudo parsear created_at: " + createdAtIso);
            return 0;
        }
    }

    /** Fetch en background al arrancar — guarda puntos en ViewModel para uso inmediato. */
    private void prefetchHeatmapPoints() {
        if (vm.heatmapPoints != null) return; // ya tenemos datos del ciclo anterior (rotación)
        if (!ConnectivityHelper.isOnline(requireContext())) return;

        com.bombayashi.reporteciudadano.network.ApiClient.getInstance().getHeatmapPoints()
            .enqueue(new Callback<com.bombayashi.reporteciudadano.model.HeatmapResponse>() {
                @Override
                public void onResponse(@NonNull Call<com.bombayashi.reporteciudadano.model.HeatmapResponse> call,
                                       @NonNull Response<com.bombayashi.reporteciudadano.model.HeatmapResponse> response) {
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getPoints() != null) {
                        vm.heatmapPoints = response.body().getPoints();
                        android.util.Log.d("MapFragment", "🗺 Heatmap precargado: " + vm.heatmapPoints.size() + " puntos");
                        // Si el usuario ya estaba a zoom bajo cuando llegó la respuesta, pintar ya
                        if (isAdded() && mapboxMap != null
                                && mapboxMap.getCameraState().getZoom() < MIN_ZOOM_TO_LOAD) {
                            buildHeatmapLayer(vm.heatmapPoints);
                        }
                    }
                }
                @Override
                public void onFailure(@NonNull Call<com.bombayashi.reporteciudadano.model.HeatmapResponse> call,
                                      @NonNull Throwable t) {
                    android.util.Log.w("MapFragment", "Heatmap prefetch failed: " + t.getMessage());
                }
            });
    }

    /** Usa puntos ya en memoria o lanza fetch si todavía no llegaron. */
    private void fetchAndShowHeatmap() {
        vm.heatmapEnabled = true;
        if (vm.heatmapPoints != null && !vm.heatmapPoints.isEmpty()) {
            buildHeatmapLayer(vm.heatmapPoints);
        }
        // Si heatmapPoints es null, prefetchHeatmapPoints() ya está corriendo en background
        // y al terminar detectará que estamos a zoom bajo y llamará buildHeatmapLayer()
    }

    private void buildHeatmapLayer(java.util.List<com.bombayashi.reporteciudadano.model.HeatmapResponse.Point> pts) {
        if (mapboxMap == null) return;
        mapboxMap.getStyle(style -> {
            if (!vm.heatmapEnabled) return;
            java.util.List<com.mapbox.geojson.Feature> features = new java.util.ArrayList<>();
            for (com.bombayashi.reporteciudadano.model.HeatmapResponse.Point p : pts) {
                features.add(com.mapbox.geojson.Feature.fromGeometry(
                    com.mapbox.geojson.Point.fromLngLat(p.getLongitude(), p.getLatitude())));
            }
            buildHeatmapOnStyle(style, com.mapbox.geojson.FeatureCollection.fromFeatures(features));
        });
    }

    /** RF-19: re-evalúa filtros sobre los reportes ya conocidos, sin re-fetch. */
    private void toggleHeatmap(boolean enable) {
        if (mapboxMap == null) return;
        vm.heatmapEnabled = enable;
        if (enable) {
            if (vm.heatmapPoints != null && !vm.heatmapPoints.isEmpty()) {
                buildHeatmapLayer(vm.heatmapPoints);
            } else {
                buildHeatmapFromReportMarkers();
            }
        } else {
            mapboxMap.getStyle(style -> {
                if (style.styleLayerExists(HEATMAP_LAYER_ID)) style.removeStyleLayer(HEATMAP_LAYER_ID);
                if (style.styleSourceExists(HEATMAP_SOURCE_ID)) style.removeStyleSource(HEATMAP_SOURCE_ID);
            });
        }
    }

    private void buildHeatmapFromReportMarkers() {
        if (mapboxMap == null) return;
        mapboxMap.getStyle(style -> {
            java.util.List<com.mapbox.geojson.Feature> features = new java.util.ArrayList<>();
            for (ReportResponse.ReportData r : reportMarkers().values()) {
                if ("archived".equalsIgnoreCase(r.getStatus())) continue;
                features.add(com.mapbox.geojson.Feature.fromGeometry(
                    com.mapbox.geojson.Point.fromLngLat(r.getLongitude(), r.getLatitude())));
            }
            buildHeatmapOnStyle(style, com.mapbox.geojson.FeatureCollection.fromFeatures(features));
        });
    }

    private void buildHeatmapOnStyle(com.mapbox.maps.Style style,
                                     com.mapbox.geojson.FeatureCollection fc) {
        if (style.styleLayerExists(HEATMAP_LAYER_ID)) style.removeStyleLayer(HEATMAP_LAYER_ID);
        if (style.styleSourceExists(HEATMAP_SOURCE_ID)) style.removeStyleSource(HEATMAP_SOURCE_ID);

        com.mapbox.maps.extension.style.sources.generated.GeoJsonSource src =
            new com.mapbox.maps.extension.style.sources.generated.GeoJsonSource.Builder(HEATMAP_SOURCE_ID)
                .featureCollection(fc).build();
        src.bindTo(style);

        com.mapbox.maps.extension.style.layers.generated.HeatmapLayer layer =
            new com.mapbox.maps.extension.style.layers.generated.HeatmapLayer(HEATMAP_LAYER_ID, HEATMAP_SOURCE_ID);
        layer.heatmapOpacity(0.7);
        layer.heatmapRadius(20.0);
        layer.bindTo(style);
    }

    private void loadFilterPrefs() {
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences(PREFS_FILTERS, android.content.Context.MODE_PRIVATE);
        vm.filterStatus = prefs.getString(PREF_FILTER_STATUS, "all");
        vm.filterAge = prefs.getString(PREF_FILTER_AGE, "all");
        java.util.Set<String> saved = prefs.getStringSet(PREF_FILTER_CATEGORIES, new java.util.HashSet<>());
        vm.filterCategories.clear();
        vm.filterCategories.addAll(saved);
    }

    private void saveFilterPrefs() {
        android.content.SharedPreferences.Editor editor = requireContext()
            .getSharedPreferences(PREFS_FILTERS, android.content.Context.MODE_PRIVATE).edit();
        editor.putString(PREF_FILTER_STATUS, vm.filterStatus);
        editor.putString(PREF_FILTER_AGE, vm.filterAge);
        editor.putStringSet(PREF_FILTER_CATEGORIES, new java.util.HashSet<>(vm.filterCategories));
        editor.apply();
    }

    private void applyFilters() {
        if (markerRenderer == null) return;
        int uid = TokenManager.getInstance(requireContext()).getUserId();
        for (ReportResponse.ReportData report : new java.util.ArrayList<>(reportMarkers().values())) {
            if (passesFilters(report)) {
                markerRenderer.addReport(report, uid);
            } else {
                markerRenderer.removeReport(report.getId());
            }
        }
    }

    /** RF-19: muestra diálogo de filtros por categoría, estado y antigüedad. */
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_map_filters, null);

        java.util.Map<String, Integer> categoryCheckboxIds = new java.util.LinkedHashMap<>();
        categoryCheckboxIds.put("vialidad", R.id.cb_filter_vialidad);
        categoryCheckboxIds.put("alumbrado", R.id.cb_filter_alumbrado);
        categoryCheckboxIds.put("agua", R.id.cb_filter_agua);
        categoryCheckboxIds.put("trafico", R.id.cb_filter_trafico);
        categoryCheckboxIds.put("seguridad", R.id.cb_filter_seguridad);
        categoryCheckboxIds.put("basura", R.id.cb_filter_basura);
        categoryCheckboxIds.put("otros", R.id.cb_filter_otros);

        for (java.util.Map.Entry<String, Integer> entry : categoryCheckboxIds.entrySet()) {
            android.widget.CheckBox cb = dialogView.findViewById(entry.getValue());
            cb.setChecked(vm.filterCategories.isEmpty() || vm.filterCategories.contains(entry.getKey()));
        }

        android.widget.RadioGroup rgStatus = dialogView.findViewById(R.id.rg_filter_status);
        int statusCheckedId = switch (vm.filterStatus) {
            case "pending" -> R.id.rb_status_pending;
            case "verified" -> R.id.rb_status_verified;
            case "resolved" -> R.id.rb_status_resolved;
            default -> R.id.rb_status_all;
        };
        rgStatus.check(statusCheckedId);

        android.widget.RadioGroup rgAge = dialogView.findViewById(R.id.rg_filter_age);
        int ageCheckedId = switch (vm.filterAge) {
            case "1h" -> R.id.rb_age_1h;
            case "6h" -> R.id.rb_age_6h;
            case "24h" -> R.id.rb_age_24h;
            default -> R.id.rb_age_all;
        };
        rgAge.check(ageCheckedId);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Filtrar reportes")
                .setView(dialogView)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    java.util.Set<String> selected = new java.util.HashSet<>();
                    for (java.util.Map.Entry<String, Integer> entry : categoryCheckboxIds.entrySet()) {
                        android.widget.CheckBox cb = dialogView.findViewById(entry.getValue());
                        if (cb.isChecked()) selected.add(entry.getKey());
                    }
                    // Si están todas marcadas (o ninguna), equivale a "sin filtro"
                    vm.filterCategories.clear();
                    if (!selected.isEmpty() && selected.size() < categoryCheckboxIds.size()) {
                        vm.filterCategories.addAll(selected);
                    }

                    int checkedStatus = rgStatus.getCheckedRadioButtonId();
                    if (checkedStatus == R.id.rb_status_pending) vm.filterStatus = "pending";
                    else if (checkedStatus == R.id.rb_status_verified) vm.filterStatus = "verified";
                    else if (checkedStatus == R.id.rb_status_resolved) vm.filterStatus = "resolved";
                    else vm.filterStatus = "all";

                    int checkedAge = rgAge.getCheckedRadioButtonId();
                    if (checkedAge == R.id.rb_age_1h) vm.filterAge = "1h";
                    else if (checkedAge == R.id.rb_age_6h) vm.filterAge = "6h";
                    else if (checkedAge == R.id.rb_age_24h) vm.filterAge = "24h";
                    else vm.filterAge = "all";

                    saveFilterPrefs();
                    applyFilters();
                })
                .setNeutralButton("Limpiar filtros", (dialog, which) -> {
                    vm.filterCategories.clear();
                    vm.filterStatus = "all";
                    vm.filterAge = "all";
                    saveFilterPrefs();
                    applyFilters();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addReportMarker(ReportResponse.ReportData report) {
        if (markerRenderer != null) {
            int uid = TokenManager.getInstance(requireContext()).getUserId();
            markerRenderer.addReport(report, uid);
        }
    }

    private void setupMapListeners() {
        GesturesUtils.getGestures(mapView).addOnMapLongClickListener(point -> {
            if (userLocation == null) {
                SnackbarHelper.show(getView(), "Obteniendo tu ubicación...", SnackbarHelper.Variant.INFO);
                return true;
            }

            // RF-07+: Validar geovalla para creación de reportes (500m)
            double distanceMeters = com.bombayashi.reporteciudadano.util.LocationUtil.calculateDistance(
                    userLocation.latitude(), userLocation.longitude(),
                    point.latitude(), point.longitude()
            );

            if (distanceMeters > com.bombayashi.reporteciudadano.util.LocationUtil.VOTE_RADIUS_METERS) {
                SnackbarHelper.show(getView(), 
                    String.format(java.util.Locale.getDefault(), 
                        "Estás muy lejos (%.0fm). Acércate a menos de 500m para reportar.", distanceMeters), 
                    SnackbarHelper.Variant.WARNING);
                android.util.Log.w("MapFragment", "Intento de reporte fuera de rango: " + distanceMeters + "m");
                return true;
            }

            // Si está en rango, abrir menú
            showRadialMenu(point);
            return true;
        });
    }

    private void showRadialMenu(Point location) {
        // Pasamos el Point directamente, RadialMenuDialogFragment usará .latitude() y .longitude()
        RadialMenuDialogFragment dialog = RadialMenuDialogFragment.newInstance(location, (category, lat, lng) -> {
            onReportCategorySelected(category, lat, lng);
        });
        dialog.show(getChildFragmentManager(), "radial_menu");
    }

    private void onReportCategorySelected(String category, double latitude, double longitude) {
        if (getView() == null) return;

        // Mapbox: point.latitude() -> 13.6..., point.longitude() -> -87.9...
        // La API espera: latitude -> 13.6, longitude -> -87.9
        android.util.Log.d("MapFragment", "Creando reporte: " + category + " en Lat:" + latitude + ", Lng:" + longitude);

        // Bloquear si ya existe reporte de misma categoría a menos de ~50m
        int categoryId = getCategoryIdBySlug(category);
        if (hasSameCategoryNearby(categoryId, latitude, longitude, 50)) {
            SnackbarHelper.show(getView(), "Ya existe un reporte de esta categoría cerca", SnackbarHelper.Variant.WARNING);
            android.util.Log.w("MapFragment", "Reporte duplicado bloqueado: cat=" + category + " en " + latitude + "," + longitude);
            return;
        }

        if (!TokenManager.getInstance(requireContext()).isLoggedIn()) {
            SnackbarHelper.show(getView(), "Iniciá sesión para reportar", SnackbarHelper.Variant.WARNING);
            return;
        }

        ReportRequest request = new ReportRequest(categoryId, latitude, longitude, "Reporte desde app");

        if (!ConnectivityHelper.isOnline(requireContext())) {
            queueOfflineReport(categoryId, latitude, longitude, "Reporte desde app");
            return;
        }

        ApiClient.getInstance().createReport(request).enqueue(new Callback<CreateReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<CreateReportResponse> call, @NonNull Response<CreateReportResponse> response) {
                if (!isAdded() || getView() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    CreateReportResponse apiResponse = response.body();
                    if (apiResponse.getReport() != null) {
                        ReportResponse.ReportData report = apiResponse.getReport();
                        android.util.Log.i("MapFragment", "✓ Reporte creado. ID: " + report.getId() +
                                " | Coords: " + report.getLatitude() + ", " + report.getLongitude() +
                                " | Category: " + (report.getCategory() != null ? report.getCategory().getSlug() : "null"));
                        addReportMarker(report);
                        SnackbarHelper.show(getView(), "Reporte creado exitosamente", SnackbarHelper.Variant.SUCCESS);
                    } else {
                        android.util.Log.e("MapFragment", "✗ API retornó report null");
                        SnackbarHelper.show(getView(), "Error: respuesta vacía de API", SnackbarHelper.Variant.ERROR);
                    }
                } else if (response.code() == 401) {
                    handleExpiredSession();
                } else {
                    String errorMsg = "Error al crear reporte";
                    try {
                        if (response.errorBody() != null) {
                            errorMsg = response.errorBody().string();
                        }
                    } catch (Exception e) {
                        android.util.Log.e("MapFragment", "Error leyendo errorBody", e);
                    }
                    android.util.Log.e("MapFragment", "✗ Error en API (Código " + response.code() + "): " + errorMsg);
                    SnackbarHelper.show(getView(), "Error: " + response.code(), SnackbarHelper.Variant.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Call<CreateReportResponse> call, @NonNull Throwable t) {
                if (!isAdded() || getView() == null) return;
                android.util.Log.e("MapFragment", "✗ Falla en la red: " + t.getMessage(), t);
                SnackbarHelper.show(getView(), "Error de red: " + t.getMessage(), SnackbarHelper.Variant.ERROR);
            }
        });
    }

    /** RF-05: sin conexión, encola la creación del reporte para enviarla cuando vuelva la red. */
    private void queueOfflineReport(int categoryId, double latitude, double longitude, String description) {
        org.json.JSONObject payload = new org.json.JSONObject();
        try {
            payload.put("category_id", categoryId);
            payload.put("latitude", latitude);
            payload.put("longitude", longitude);
            payload.put("description", description);
        } catch (org.json.JSONException e) {
            android.util.Log.e("MapFragment", "Error armando payload offline: " + e.getMessage());
            return;
        }

        dbExecutor.execute(() -> appDatabase.pendingActionDao().insert(
                new PendingActionEntity(PendingActionEntity.TYPE_CREATE_REPORT, payload.toString(), System.currentTimeMillis())
        ));

        SnackbarHelper.show(getView(), "Sin conexión: reporte guardado, se enviará cuando vuelva la conexión",
                SnackbarHelper.Variant.INFO);
    }

    private boolean hasSameCategoryNearby(int categoryId, double lat, double lng, double radiusMeters) {
        for (ReportResponse.ReportData report : reportMarkers().values()) {
            // Archivados y resueltos no bloquean nuevos reportes
            String status = report.getStatus();
            if ("archived".equalsIgnoreCase(status) || "resolved".equalsIgnoreCase(status)) continue;
            if (report.getCategory() == null) continue;
            if (report.getCategory().getId() != categoryId) continue;
            double distanceKm = NearbyReportChecker.haversineKm(lat, lng, report.getLatitude(), report.getLongitude());
            if (distanceKm * 1000 <= radiusMeters) return true;
        }
        return false;
    }

    private int getCategoryIdBySlug(String slug) {
        return com.bombayashi.reporteciudadano.util.CategoryMapper.toApiId(slug);
    }

    private void showReportDetails(ReportResponse.ReportData report) {
        if (report == null) return;
        // Pasar ubicación real del usuario para validar distancia de votación
        ReportDetailBottomSheet bottomSheet = ReportDetailBottomSheet.newInstance(report, userLocation);
        // Pasar listener para cambios de estado
        bottomSheet.setStatusChangeListener(this);
        bottomSheet.show(getChildFragmentManager(), "report_detail");
    }

    @com.mapbox.maps.MapboxExperimental
    @Override
    public void onReportStatusChanged(int reportId, String newStatus, int confirmCount, int resolveCount) {
        android.util.Log.d("MapFragment", "═══════════════════════════════════════════");
        android.util.Log.d("MapFragment", "📊 onReportStatusChanged() - ReportID: " + reportId);
        
        updateReportMarker(reportId, newStatus, confirmCount, resolveCount);
    }

    @com.mapbox.maps.MapboxExperimental
    @Override
    public void onReportDataUpdated(ReportResponse.ReportData updatedReport) {
        if (updatedReport != null) {
            android.util.Log.d("MapFragment", "💾 Sincronizando caché: Reporte " + updatedReport.getId() + " actualizado.");
            reportMarkers().put(String.valueOf(updatedReport.getId()), updatedReport);
        }
    }

    @com.mapbox.maps.MapboxExperimental
    @Override
    public void onReportRetracted(int reportId) {
        android.util.Log.d("MapFragment", "🗑️ Reporte retirado: ID=" + reportId);
        reportMarkers().remove(String.valueOf(reportId));
        if (markerRenderer != null) markerRenderer.removeReport(reportId);
    }

    private void updateReportMarker(int reportId, String newStatus, int confirmCount, int resolveCount) {
        if (markerRenderer != null) {
            int uid = TokenManager.getInstance(requireContext()).getUserId();
            markerRenderer.updateReport(reportId, newStatus, confirmCount, resolveCount, uid);
        }
    }

    private void showLongPressHintIfFirstTime() {
        android.content.SharedPreferences prefs = requireContext()
            .getSharedPreferences("map_onboarding", android.content.Context.MODE_PRIVATE);
        if (prefs.getBoolean("hint_shown", false)) return;
        prefs.edit().putBoolean("hint_shown", true).apply();

        binding.tvLongPressHint.setVisibility(View.VISIBLE);
        binding.tvLongPressHint.postDelayed(() -> {
            if (binding == null) return;
            binding.tvLongPressHint.animate().alpha(0f).setDuration(600).withEndAction(() -> {
                if (binding != null) binding.tvLongPressHint.setVisibility(View.GONE);
            }).start();
        }, 4000);
    }

    private void setupFAB() {
        // Botón: Mi ubicación
        binding.fabMyLocation.setOnClickListener(v -> {
            if (userLocation != null) {
                animateCameraTo(userLocation);
            } else {
                locationTracker.requestLocation(this);
            }
        });

        // Botón: Agregar reporte
        binding.fabAddReport.setOnClickListener(v -> {
            if (userLocation != null) {
                showRadialMenu(userLocation);
            } else {
                SnackbarHelper.show(getView(), "Obteniendo ubicación...", SnackbarHelper.Variant.INFO);
                locationTracker.requestLocation(this);
            }
        });

        // Botón: Filtros (RF-19)
        binding.fabFilter.setOnClickListener(v -> showFilterDialog());

        // Botón: Perfil de usuario
        binding.fabProfile.setOnClickListener(v -> {
            if (!TokenManager.getInstance(requireContext()).isLoggedIn()) {
                startActivity(new android.content.Intent(requireContext(), LoginActivity.class));
            } else {
                showUserProfile();
            }
        });

        updateFabVisibility();
    }

    private void updateFabVisibility() {
        if (binding == null) return;
        boolean loggedIn = TokenManager.getInstance(requireContext()).isLoggedIn();
        binding.fabAddReport.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private void handleExpiredSession() {
        // No borramos el token automáticamente — podría ser error temporal del servidor.
        // El usuario cierra sesión explícitamente desde su perfil.
        updateFabVisibility();
        if (getView() != null) {
            SnackbarHelper.show(getView(), "Sesión inválida. Iniciá sesión de nuevo.", SnackbarHelper.Variant.WARNING);
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (isAdded()) startActivity(new android.content.Intent(requireContext(), LoginActivity.class));
            }, 1500);
        }
    }

    private void showUserProfile() {
        UserProfileBottomSheet profileSheet = new UserProfileBottomSheet();
        profileSheet.setLogoutListener(this::handleLogout);
        profileSheet.show(getChildFragmentManager(), "user_profile");
    }

    private void handleLogout() {
        android.util.Log.d("MapFragment", "🔓 Usuario cerró sesión, volviendo a login...");
        // Ir a LoginActivity
        requireActivity().startActivity(new android.content.Intent(requireContext(), LoginActivity.class));
        requireActivity().finish();
    }

    private void checkContextualOnboarding() {
        com.bombayashi.reporteciudadano.util.SettingsManager sm = com.bombayashi.reporteciudadano.util.SettingsManager.getInstance(requireContext());
        if (!sm.isOnboardingCompleted() && com.bombayashi.reporteciudadano.util.TokenManager.getInstance(requireContext()).isLoggedIn()) {
            showOnboardingPrompt();
        }
    }

    private void showOnboardingPrompt() {
        // RF-34+: Onboarding contextual visual (Material Tap Target)
        new uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.Builder(this)
                .setTarget(binding.fabAddReport)
                .setPrimaryText("Reporte Rápido")
                .setSecondaryText("Pulsa aquí para crear un reporte instantáneo en tu ubicación actual. Ideal para baches o peligros que tienes justo enfrente.")
                .setBackButtonDismissEnabled(true)
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED 
                        || state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        showLocationPrompt();
                    }
                })
                .show();
    }

    private void showLocationPrompt() {
        new uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.Builder(this)
                .setTarget(binding.fabMyLocation)
                .setPrimaryText("Tu Ubicación")
                .setSecondaryText("¿Te perdiste explorando el mapa? Toca este botón para volver rápidamente a tu posición real.")
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED
                        || state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        showFilterPrompt();
                    }
                })
                .show();
    }

    private void showFilterPrompt() {
        new uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.Builder(this)
                .setTarget(binding.fabFilter)
                .setPrimaryText("Limpia el Mapa")
                .setSecondaryText("Usa los filtros para ver solo las categorías que te interesan o reportes recientes.")
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED
                        || state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        showProfilePrompt();
                    }
                })
                .show();
    }

    private void showProfilePrompt() {
        new uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.Builder(this)
                .setTarget(binding.fabProfile)
                .setPrimaryText("Gestiona tu Perfil")
                .setSecondaryText("Revisa tus puntos de ciudadano, configura tus alertas y mira el historial de tus reportes.")
                .setPromptStateChangeListener((prompt, state) -> {
                    if (state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_DISMISSED
                        || state == uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt.STATE_FOCAL_PRESSED) {
                        com.bombayashi.reporteciudadano.util.SettingsManager.getInstance(requireContext()).setOnboardingCompleted(true);
                    }
                })
                .show();
    }

    @com.mapbox.maps.MapboxExperimental
    private void setupUser3DLayer(@NonNull com.mapbox.maps.Style style) {
        // Tarea 1: Registro del Modelo 3D (Usando el archivo correcto)
        try {
            style.addStyleModel("user-arrow-model", "asset://arrow_direction.glb");
        } catch (Exception e) {
            android.util.Log.e("MapFragment", "Error cargando modelo 3D: " + e.getMessage());
        }

        // Tarea 2: Creación de la Fuente de Datos GeoJSON y la Capa 3D
        user3DSource = new GeoJsonSource.Builder("user-3d-source")
                .geometry(Point.fromLngLat(0, 0))
                .build();
        user3DSource.bindTo(style);

        // Capa de Círculo (Halo) para visibilidad en zoom lejano
        CircleLayer userHaloLayer = new CircleLayer("user-halo-layer", "user-3d-source")
                .circleRadius(10.0) // Tamaño fijo en píxeles para que se vea siempre igual de lejos
                .circleColor("#2196F3")
                .circleStrokeWidth(2.0)
                .circleStrokeColor("#FFFFFF")
                .circleOpacity(0.8);
        userHaloLayer.bindTo(style);

        user3DLayer = new ModelLayer("user-3d-layer", "user-3d-source")
                .modelId("user-arrow-model")
                .modelScale(java.util.Arrays.asList(40.0, 40.0, 40.0)) 
                .modelRotation(java.util.Arrays.asList(90.0, 0.0, 0.0)) // Rotación 90° en X para que apunte hacia adelante
                .modelTranslation(java.util.Arrays.asList(0.0, 0.0, 2.0)); 
        user3DLayer.bindTo(style);
    }

    @com.mapbox.maps.MapboxExperimental
    private void updateUser3DMarker(Point point, float bearing) {
        if (mapboxMap == null) return;
        com.mapbox.maps.Style style = mapboxMap.getStyle();
        if (style == null) return;

        // Tarea 3: Actualización Dinámica
        style.setStyleGeoJSONSourceData("user-3d-source", "", 
            new com.mapbox.maps.GeoJSONSourceData(point));

        if (user3DLayer != null) {
            // Ajustamos la rotación: 90° en X (fijo) y 'bearing' en Z (dinámico)
            user3DLayer.modelRotation(java.util.Arrays.asList(90.0, 0.0, (double) bearing));
        }
    }

    private void centerOnDefaultLocation() {
        userLocation = Point.fromLngLat(DEFAULT_LONGITUDE, DEFAULT_LATITUDE);
        addUserLocationMarker(userLocation);
        animateCameraTo(userLocation);
    }

    private void animateCameraTo(Point point) {
        if (mapboxMap != null) {
            CameraOptions camera = new CameraOptions.Builder()
                    .center(point)
                    .zoom(DEFAULT_ZOOM)
                    .pitch(45.0)
                    .build();
            CameraAnimationsUtils.easeTo(mapboxMap, camera, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateFabVisibility();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mapView != null) mapView.onStart();
        if (vm.getPoller() != null) vm.getPoller().start();
        votableReportsHandler.postDelayed(votableReportsRunnable, VOTABLE_REPORTS_CHECK_INTERVAL_MS);
        startContinuousLocationTracking();
        if (headingManager != null) headingManager.startListening();
    }

    @Override
    public void onStop() {
        if (vm.getPoller() != null) vm.getPoller().stop();
        votableReportsHandler.removeCallbacks(votableReportsRunnable);
        stopContinuousLocationTracking();
        if (headingManager != null) headingManager.stopListening();
        if (mapView != null) mapView.onStop();
        super.onStop();
    }

    /**
     * Check for nearby votable reports and notify user (Option B - periodic local notifications)
     * Called every 30 seconds while map is visible
     */

    public boolean isMapReady() { return mapReady; }

    public void setOnMapReadyCallback(Runnable callback) { onMapReadyCallback = callback; }

    /**
     * Called from MainActivity when user taps notification (Phase 2 - FCM integration)
     */
    public void showReportFromNotification(int reportId) {
        android.util.Log.d("MapFragment", "🔔 showReportFromNotification: reportId=" + reportId);

        if (!isAdded() || getView() == null) {
            android.util.Log.w("MapFragment", "Fragment not attached yet");
            return;
        }

        // Check if report is already in cache
        ReportResponse.ReportData cachedReport = findReportByIdInCache(reportId);

        if (cachedReport != null) {
            android.util.Log.d("MapFragment", "✓ Report found in cache: " + reportId);
            showReportDetails(cachedReport);
        } else {
            android.util.Log.d("MapFragment", "📥 Loading report from API: " + reportId);
            loadReportFromAPI(reportId);
        }
    }

    /**
     * Search for report in local cache by ID
     */
    private ReportResponse.ReportData findReportByIdInCache(int reportId) {
        return reportMarkers().get(String.valueOf(reportId));
    }

    /**
     * Load single report from API (GET /reports/{id})
     */
    private void loadReportFromAPI(int reportId) {
        if (!ConnectivityHelper.isOnline(requireContext())) {
            android.util.Log.w("MapFragment", "📴 Offline: cannot load report " + reportId);
            SnackbarHelper.show(getView(), "Sin conexión para cargar reporte", SnackbarHelper.Variant.ERROR);
            return;
        }

        ApiClient.getInstance().getReportDetail(reportId).enqueue(new Callback<com.bombayashi.reporteciudadano.model.ReportDetailResponse>() {
            @Override
            public void onResponse(@NonNull Call<com.bombayashi.reporteciudadano.model.ReportDetailResponse> call,
                                   @NonNull Response<com.bombayashi.reporteciudadano.model.ReportDetailResponse> response) {

                if (!isAdded() || getView() == null) {
                    android.util.Log.w("MapFragment", "Fragment detached after API call");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    ReportResponse.ReportData report = response.body().getReport();

                    if (report != null) {
                        android.util.Log.d("MapFragment", "✓ Report loaded from API: " + reportId);
                        // Cache the report for future use
                        reportMarkers().put(String.valueOf(reportId), report);
                        cacheReport(report);
                        // Show it if passes filters
                        if (passesFilters(report)) {
                            addReportMarker(report);
                        }
                        showReportDetails(report);
                    } else {
                        android.util.Log.e("MapFragment", "✗ Report is null in response");
                        SnackbarHelper.show(getView(), "Reporte no encontrado", SnackbarHelper.Variant.ERROR);
                    }
                } else {
                    android.util.Log.e("MapFragment", "✗ Error loading report: " + response.code());
                    SnackbarHelper.show(getView(), "Error al cargar reporte: " + response.code(), SnackbarHelper.Variant.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Call<com.bombayashi.reporteciudadano.model.ReportDetailResponse> call,
                                  @NonNull Throwable t) {

                if (!isAdded() || getView() == null) return;

                android.util.Log.e("MapFragment", "❌ Network error loading report: " + t.getMessage(), t);
                SnackbarHelper.show(getView(), "Error de conexión", SnackbarHelper.Variant.ERROR);
            }
        });
    }

    private void applyDynamicLighting() {
        if (mapboxMap == null) return;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String preset;
        if (hour >= 6 && hour < 12) {
            preset = "dawn";
        } else if (hour >= 12 && hour < 18) {
            preset = "day";
        } else if (hour >= 18 && hour < 21) {
            preset = "dusk";
        } else {
            preset = "night";
        }
        mapboxMap.setStyleImportConfigProperty("basemap", "lightPreset",
                com.mapbox.bindgen.Value.valueOf(preset));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraIdleRunnable != null) cameraIdleHandler.removeCallbacks(cameraIdleRunnable);
        dbExecutor.shutdown();
        if (mapView != null) {
            mapView.onDestroy();
        }
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
        }
        binding = null;
    }
}
