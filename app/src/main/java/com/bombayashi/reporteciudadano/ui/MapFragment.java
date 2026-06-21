package com.bombayashi.reporteciudadano.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.mapbox.maps.plugin.gestures.GesturesUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapFragment extends Fragment implements ReportDetailBottomSheet.OnReportStatusChangeListener {

    private FragmentMapBinding binding;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Point userLocation; // Posición real del usuario
    private FusedLocationProviderClient fusedLocationClient;
    private CircleAnnotationManager circleAnnotationManager;
    private PointAnnotationManager pointAnnotationManager;
    private CircleAnnotation userLocationMarker;
    private com.mapbox.maps.viewannotation.ViewAnnotationManager viewAnnotationManager;
    private com.google.android.gms.location.LocationCallback continuousLocationCallback;
    private android.location.Location lastTrackedLocation;
    private static final float MOVEMENT_THRESHOLD_METERS = 2f;

    private static final double DEFAULT_LATITUDE = -34.6037;
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int INITIAL_REPORTS_PAGE_SIZE = 25;  // Pagination: cargar 25 iniciales
    private static final int MAX_REPORTS = 200;  // Máximo de reportes en caché local
    private static final double MIN_ZOOM_TO_LOAD = 13.0;  // Zoom mínimo para fetchear (~2km viewport)
    private static final int VIEWPORT_PAGE_SIZE = 50;  // Reportes por fetch de viewport
    private static final long CAMERA_IDLE_DEBOUNCE_MS = 600;  // ms espera tras mover cámara
    private java.util.HashMap<String, ReportResponse.ReportData> reportMarkers = new java.util.HashMap<>();
    private java.util.HashMap<String, Integer> annotationToReportId = new java.util.HashMap<>();  // UUID → ReportID
    private java.util.HashMap<Integer, PointAnnotation> reportIconMarkers = new java.util.HashMap<>();  // ReportID → PointAnnotation
    private java.util.HashMap<Integer, CircleAnnotation> reportStrokeMarkers = new java.util.HashMap<>();  // ReportID → CircleAnnotation (stroke)
    private java.util.HashMap<String, Integer> coordOccupancy = new java.util.HashMap<>();  // "lat,lng" → count, para offset de duplicados
    private android.util.LruCache<String, Bitmap> bitmapCache = new android.util.LruCache<>(100);
    private double lastZoomLevel = -1; // Para optimizar escala
    private int currentReportsPage = 1;  // Para pagination
    private boolean isLoadingReports = false;  // Flag para evitar duplicar requests

    // RF-19: filtros de mapa (categoría / estado / antigüedad)
    private final java.util.Set<String> filterCategories = new java.util.HashSet<>();  // vacío = todas
    private String filterStatus = "all";  // all | pending | verified | resolved
    private String filterAge = "all";     // all | 1h | 6h | 24h

    private final android.os.Handler cameraIdleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable cameraIdleRunnable;

    private static final long POLL_INTERVAL_MS = 30_000;  // Polling de cambios cada 30s
    private final android.os.Handler pollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            pollForUpdates();
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };
    private String lastSyncTimestamp;  // ISO8601 UTC del último sync exitoso

    // Notificación de reportes votables cercanos
    private static final long VOTABLE_REPORTS_CHECK_INTERVAL_MS = 30_000;  // Cada 30s
    private static final double VOTABLE_RANGE_KM = 0.5;  // 500m
    private final android.os.Handler votableReportsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable votableReportsRunnable = new Runnable() {
        @Override
        public void run() {
            checkAndNotifyNearbyVotableReports();
            votableReportsHandler.postDelayed(this, VOTABLE_REPORTS_CHECK_INTERVAL_MS);
        }
    };
    private int lastNotifiedVotableCount = -1;  // Para evitar notificar repetidamente el mismo número

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.util.Log.d("MapFragment", "=== onViewCreated ===");

        appDatabase = AppDatabase.getInstance(requireContext());
        if (ConnectivityHelper.isOnline(requireContext())) {
            SyncManager.syncNow(requireContext());
        }
        registerConnectivityCallback();
        observeSyncWork();

        mapView = binding.mapView;
        viewAnnotationManager = mapView.getViewAnnotationManager();

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
            com.mapbox.maps.extension.style.sources.SourceUtils.addSource(style, demSource);
            com.mapbox.maps.extension.style.terrain.generated.Terrain terrain =
                    new com.mapbox.maps.extension.style.terrain.generated.Terrain("mapbox-dem")
                            .exaggeration(1.5);
            com.mapbox.maps.extension.style.terrain.generated.TerrainUtils.setTerrain(style, terrain);

            // Luz dinámica según hora del día
            applyDynamicLighting();

            android.util.Log.d("MapFragment", "2. Inicializando components...");
            setupAnnotationManager();
            setupMapListeners();
            setupFAB();
            requestUserLocation();

            android.util.Log.d("MapFragment", "3. Cargando reportes iniciales...");
            loadReportsFromAPI();
        });
    }

    private void setupAnnotationManager() {
        android.util.Log.d("MapFragment", "Inicializando AnnotationManager...");

        AnnotationPlugin annotationPlugin = AnnotationsUtils.getAnnotations(mapView);
        if (annotationPlugin != null) {
            // CircleAnnotationManager para marcador de ubicación del usuario
            circleAnnotationManager = CircleAnnotationManagerKt.createCircleAnnotationManager(
                    annotationPlugin,
                    new AnnotationConfig()
            );

            // PointAnnotationManager para iconos de reportes
            pointAnnotationManager = PointAnnotationManagerKt.createPointAnnotationManager(
                    annotationPlugin,
                    new AnnotationConfig()
            );

            if (circleAnnotationManager != null && pointAnnotationManager != null) {
                android.util.Log.d("MapFragment", "✓ CircleAnnotationManager y PointAnnotationManager inicializados");

                mapboxMap.subscribeCameraChanged(event -> {
                    double currentZoom = mapboxMap.getCameraState().getZoom();
                    if (Math.abs(currentZoom - lastZoomLevel) > 0.1) {
                        lastZoomLevel = currentZoom;
                        updateMarkersScale(currentZoom);
                    }
                    // Debounce: esperar que el usuario pare de mover antes de fetchear
                    if (cameraIdleRunnable != null) cameraIdleHandler.removeCallbacks(cameraIdleRunnable);
                    cameraIdleRunnable = this::onViewportChanged;
                    cameraIdleHandler.postDelayed(cameraIdleRunnable, CAMERA_IDLE_DEBOUNCE_MS);
                });

                pointAnnotationManager.addClickListener(annotation -> {
                    String annotationUUID = annotation.getId();
                    Integer reportId = annotationToReportId.get(annotationUUID);

                    if (reportId != null) {
                        // 1. Efecto Bounce (Feedback visual)
                        animateBounce(annotation);

                        String reportKey = String.valueOf(reportId);
                        ReportResponse.ReportData report = reportMarkers.get(reportKey);

                        if (report != null) {
                            android.util.Log.d("MapFragment", "✓ Abriendo reporte ID:" + reportId);
                            showReportDetails(report);
                            return true;
                        } else {
                            android.util.Log.w("MapFragment", "✗ Reporte ID:" + reportId + " no existe en cache");
                        }
                    } else {
                        android.util.Log.w("MapFragment", "✗ Anotación UUID no mapeada a reporte");
                    }
                    return false;
                });
            } else {
                android.util.Log.e("MapFragment", "✗ Error inicializando managers");
            }
        } else {
            android.util.Log.e("MapFragment", "✗ AnnotationPlugin es null");
        }
    }

    private void requestUserLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getCurrentUserLocation();
        } else {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentUserLocation();
                startContinuousLocationTracking();
            } else {
                centerOnDefaultLocation();
            }
        }
    }

    private void getCurrentUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            centerOnDefaultLocation();
            return;
        }

        android.util.Log.d("MapFragment", "🔍 Obteniendo ubicación actual (FINE)...");

        // Primero, intenta obtener la última ubicación conocida
        fusedLocationClient.getLastLocation().addOnSuccessListener(lastLocation -> {
            if (!isAdded() || getView() == null) return;

            if (lastLocation != null) {
                android.util.Log.d("MapFragment", "📍 Última ubicación obtenida (±" +
                    String.format("%.0f", lastLocation.getAccuracy()) + "m)");

                // Si la ubicación es reciente y precisa, usarla
                long ageMs = System.currentTimeMillis() - lastLocation.getTime();
                if (ageMs < 60000 && lastLocation.getAccuracy() < 50) {  // < 1min y < 50m
                    useLocation(lastLocation);
                } else {
                    // Si es vieja o imprecisa, solicitar actualización
                    android.util.Log.d("MapFragment", "📍 Ubicación vieja/imprecisa, solicitando actualización...");
                    requestLocationUpdates();
                }
            } else {
                // Sin última ubicación, solicitar actualización
                android.util.Log.d("MapFragment", "📍 Sin ubicación previa, solicitando actualización...");
                requestLocationUpdates();
            }
        }).addOnFailureListener(e -> {
            android.util.Log.e("MapFragment", "✗ Error en getLastLocation: " + e.getMessage());
            centerOnDefaultLocation();
        });
    }

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            centerOnDefaultLocation();
            return;
        }

        // Crear LocationRequest compatible con versiones antiguas
        com.google.android.gms.location.LocationRequest locationRequest =
            com.google.android.gms.location.LocationRequest.create()
                .setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(500)
                .setFastestInterval(250);

        final boolean[] locationUpdated = {false};  // Flag para rastrear si ya obtuvimos una actualización

        com.google.android.gms.location.LocationCallback locationCallback =
            new com.google.android.gms.location.LocationCallback() {
                @Override
                public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                    if (locationResult == null || locationResult.getLocations().isEmpty()) return;

                    // Obtener la ubicación más precisa de la lista
                    android.location.Location bestLocation = locationResult.getLocations().get(0);
                    for (android.location.Location loc : locationResult.getLocations()) {
                        if (loc.getAccuracy() < bestLocation.getAccuracy()) {
                            bestLocation = loc;
                        }
                    }

                    locationUpdated[0] = true;
                    useLocation(bestLocation);

                    // Detener actualizaciones después de obtener una ubicación precisa
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.removeLocationUpdates(this);
                    }
                }
            };

        // Solicitar una única actualización (timeout 5 segundos - más corto ahora)
        java.util.Timer timer = new java.util.Timer();
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {
                        fusedLocationClient.removeLocationUpdates(locationCallback);
                    }

                    // Si NO obtuvimos actualización, mantener la ubicación anterior (no ir a Buenos Aires)
                    if (!locationUpdated[0]) {
                        android.util.Log.w("MapFragment", "⏱ Timeout en locationUpdates, manteniendo última ubicación conocida");
                    }
                } catch (Exception e) {
                    android.util.Log.e("MapFragment", "Error en timeout: " + e.getMessage());
                }
            }
        }, 5000);  // 5 segundos timeout (más corto)

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void useLocation(android.location.Location location) {
        if (!isAdded() || getView() == null) return;

        userLocation = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        lastTrackedLocation = location;
        addUserLocationMarker(userLocation);
        animateCameraTo(userLocation);

        android.util.Log.i("MapFragment", "✓ Ubicación utilizada (Precisión: " +
            String.format("%.1f", location.getAccuracy()) + "m)");

        SnackbarHelper.show(
                getView(),
                String.format(Locale.getDefault(),
                    "Tu ubicación (±%.0fm): %.4f, %.4f",
                    location.getAccuracy(),
                    location.getLatitude(),
                    location.getLongitude()),
                SnackbarHelper.Variant.SUCCESS
        );
    }

    private void startContinuousLocationTracking() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (continuousLocationCallback != null) {
            return; // ya activo
        }

        com.google.android.gms.location.LocationRequest locationRequest =
                new com.google.android.gms.location.LocationRequest.Builder(
                        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000)
                        .setMinUpdateIntervalMillis(1000)
                        .build();

        continuousLocationCallback = new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                if (locationResult == null) return;
                android.location.Location location = locationResult.getLastLocation();
                if (location == null) return;
                updateUserLocationIfMoved(location);
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, continuousLocationCallback, null);
    }

    private void stopContinuousLocationTracking() {
        if (continuousLocationCallback == null) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.removeLocationUpdates(continuousLocationCallback);
        }
        continuousLocationCallback = null;
    }

    /** Actualiza solo el marcador de ubicación si el usuario se movió, sin mover la cámara. */
    private void updateUserLocationIfMoved(android.location.Location location) {
        if (!isAdded() || getView() == null) return;

        if (lastTrackedLocation != null && lastTrackedLocation.distanceTo(location) < MOVEMENT_THRESHOLD_METERS) {
            return; // no se movió lo suficiente
        }
        lastTrackedLocation = location;

        userLocation = Point.fromLngLat(location.getLongitude(), location.getLatitude());
        addUserLocationMarker(userLocation);
    }

    private void addUserLocationMarker(Point point) {
        if (circleAnnotationManager == null) {
            return;
        }

        if (userLocationMarker != null) {
            circleAnnotationManager.delete(userLocationMarker);
        }

        CircleAnnotationOptions options = new CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(10.0)
                .withCircleColor("#2196F3")
                .withCircleOpacity(0.7)
                .withCircleStrokeWidth(3.0)
                .withCircleStrokeColor("#FFFFFF");

        userLocationMarker = circleAnnotationManager.create(options);
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
    private void onViewportChanged() {
        if (!isAdded() || getView() == null || mapboxMap == null) return;

        double zoom = mapboxMap.getCameraState().getZoom();

        if (zoom < MIN_ZOOM_TO_LOAD) {
            clearAllMarkerVisuals();
            android.util.Log.d("MapFragment", "🔍 Zoom " + String.format(Locale.US, "%.1f", zoom) + " < " + MIN_ZOOM_TO_LOAD + ", no cargando reportes");
            SnackbarHelper.show(getView(), "Acercate para ver reportes", SnackbarHelper.Variant.INFO);
            return;
        }

        if (isLoadingReports) return;

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

        isLoadingReports = true;
        if (lastSyncTimestamp == null) {
            lastSyncTimestamp = currentTimestampIso();
        }

        ApiClient.getInstance().getReportsByBounds(latMin, latMax, lngMin, lngMax, VIEWPORT_PAGE_SIZE)
                .enqueue(new Callback<ReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportResponse> call, @NonNull Response<ReportResponse> response) {
                isLoadingReports = false;
                if (!isAdded() || getView() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    java.util.List<ReportResponse.ReportData> reports = response.body().getData();
                    android.util.Log.d("MapFragment", "✓ Viewport reports: " + (reports != null ? reports.size() : 0));

                    // Remove visuals for markers outside current bounds
                    removeMarkersOutsideBounds(latMin, latMax, lngMin, lngMax);

                    if (reports != null) {
                        int added = 0;
                        for (ReportResponse.ReportData report : reports) {
                            String key = String.valueOf(report.getId());
                            boolean isNew = !reportMarkers.containsKey(key);
                            if (reportMarkers.size() < MAX_REPORTS || !isNew) {
                                addReportMarker(report);
                                cacheReport(report);
                                if (isNew) added++;
                            }
                        }
                        if (added > 0) {
                            android.util.Log.d("MapFragment", "📍 " + added + " nuevos marcadores en viewport");
                        }
                    }
                } else {
                    android.util.Log.e("MapFragment", "✗ Error HTTP: " + response.code());
                    SnackbarHelper.show(getView(), "Error: " + response.code(), SnackbarHelper.Variant.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReportResponse> call, @NonNull Throwable t) {
                isLoadingReports = false;
                if (!isAdded() || getView() == null) return;
                android.util.Log.e("MapFragment", "❌ Error de red: " + t.getMessage(), t);
                SnackbarHelper.show(getView(), "Error de conexión", SnackbarHelper.Variant.ERROR);
            }
        });
    }

    /** Elimina visuals de marcadores que ya no están en el bounding box visible. */
    private void removeMarkersOutsideBounds(double latMin, double latMax, double lngMin, double lngMax) {
        java.util.List<Integer> toRemove = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, ReportResponse.ReportData> entry : reportMarkers.entrySet()) {
            ReportResponse.ReportData r = entry.getValue();
            if (r.getLatitude() < latMin || r.getLatitude() > latMax ||
                r.getLongitude() < lngMin || r.getLongitude() > lngMax) {
                toRemove.add(r.getId());
            }
        }
        for (int id : toRemove) {
            removeMarkerVisuals(id);
            reportMarkers.remove(String.valueOf(id));
        }
        if (!toRemove.isEmpty()) {
            android.util.Log.d("MapFragment", "🗑 Removidos " + toRemove.size() + " marcadores fuera de viewport");
        }
    }

    /** Limpia todos los marcadores del mapa (sin borrar caché). */
    private void clearAllMarkerVisuals() {
        for (int id : new java.util.ArrayList<>(reportIconMarkers.keySet())) {
            removeMarkerVisuals(id);
        }
        reportMarkers.clear();
        coordOccupancy.clear();
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

    /** RF-05: cuando el worker termina, refresca el mapa para mostrar lo recién sincronizado. */
    private void observeSyncWork() {
        androidx.work.WorkManager.getInstance(requireContext())
                .getWorkInfosForUniqueWorkLiveData("report_sync")
                .observe(getViewLifecycleOwner(), workInfos -> {
                    if (workInfos == null) return;
                    for (androidx.work.WorkInfo info : workInfos) {
                        if (info.getState() == androidx.work.WorkInfo.State.SUCCEEDED) {
                            android.util.Log.d("MapFragment", "✓ Sync de pendientes completado, refrescando mapa");
                            pollForUpdates();
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
                    if (reportMarkers.size() >= MAX_REPORTS) break;
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

    private String currentTimestampIso() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new java.util.Date());
    }

    /**
     * Polling: pide reportes nuevos o modificados desde la última sincronización
     * (creaciones, cambios de estado, votos de otros usuarios) y actualiza el mapa.
     */
    private void pollForUpdates() {
        if (!isAdded() || getView() == null || lastSyncTimestamp == null) return;

        android.util.Log.d("MapFragment", "🔁 Polling stream/changes — since=" + lastSyncTimestamp);

        ApiClient.getInstance().getReportsStreamChanges(lastSyncTimestamp, MAX_REPORTS).enqueue(new Callback<ReportStreamResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportStreamResponse> call, @NonNull Response<ReportStreamResponse> response) {
                if (!isAdded() || getView() == null) return;

                if (response.isSuccessful() && response.body() != null) {
                    ReportStreamResponse streamResponse = response.body();
                    java.util.List<ReportResponse.ReportData> reports = streamResponse.getReports();

                    android.util.Log.d("MapFragment", "✓ stream/changes → count=" + streamResponse.getCount()
                            + " timestamp=" + streamResponse.getTimestamp());

                    if (reports != null) {
                        for (ReportResponse.ReportData report : reports) {
                            android.util.Log.d("MapFragment", "  ↳ reporte ID=" + report.getId()
                                    + " status=" + report.getStatus()
                                    + " updated_at=" + report.getUpdatedAt());
                            applyReportUpdate(report);
                        }
                    }

                    if (streamResponse.getTimestamp() != null) {
                        lastSyncTimestamp = streamResponse.getTimestamp();
                    }
                } else {
                    android.util.Log.e("MapFragment", "✗ Error en polling: " + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReportStreamResponse> call, @NonNull Throwable t) {
                android.util.Log.e("MapFragment", "❌ Error de red en polling: " + t.getMessage());
            }
        });
    }

    private void applyReportUpdate(ReportResponse.ReportData report) {
        String reportKey = String.valueOf(report.getId());
        ReportResponse.ReportData cached = reportMarkers.get(reportKey);

        if (cached == null) {
            if (reportMarkers.size() < MAX_REPORTS) {
                addReportMarker(report);
                android.util.Log.d("MapFragment", "🆕 Reporte nuevo via polling: ID=" + report.getId());
            }
            return;
        }

        boolean statusChanged = !cached.getStatus().equals(report.getStatus());
        reportMarkers.put(reportKey, report);

        if (statusChanged) {
            android.util.Log.d("MapFragment", "🔄 Estado actualizado via polling: ID=" + report.getId()
                    + " " + cached.getStatus() + " → " + report.getStatus());
            updateReportMarker(report.getId(), report.getStatus(), report.getVotesConfirm(), report.getVotesResolve());
        }
    }

    /** RF-18/RF-19: elimina los visuales del marcador sin perder el dato cacheado en reportMarkers. */
    private void removeMarkerVisuals(int reportId) {
        PointAnnotation existingIcon = reportIconMarkers.remove(reportId);
        if (existingIcon != null && pointAnnotationManager != null) pointAnnotationManager.delete(existingIcon);
        CircleAnnotation existingStroke = reportStrokeMarkers.remove(reportId);
        if (existingStroke != null && circleAnnotationManager != null) circleAnnotationManager.delete(existingStroke);
    }

    /** RF-18: reportes archivados nunca se pintan en el mapa. RF-19: filtros de categoría/estado/antigüedad. */
    private boolean passesFilters(ReportResponse.ReportData report) {
        String status = report.getStatus();
        if ("archived".equalsIgnoreCase(status)) return false;

        if (!filterStatus.equals("all") && !filterStatus.equalsIgnoreCase(status)) return false;

        if (!filterCategories.isEmpty()) {
            String slug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
            if (!filterCategories.contains(normalizeCategoryGroup(slug))) return false;
        }

        if (!filterAge.equals("all")) {
            long ageHours = reportAgeHours(report.getCreatedAt());
            int maxHours = switch (filterAge) {
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
        return switch (categorySlug) {
            case "bache", "vialidad" -> "vialidad";
            case "alumbrado-publico", "alumbrado" -> "alumbrado";
            case "fuga-de-agua", "agua" -> "agua";
            case "semaforo-danado", "trafico" -> "trafico";
            case "inseguridad", "seguridad" -> "seguridad";
            case "basura-acumulada", "parques", "basura" -> "basura";
            default -> "otros";
        };
    }

    /** Horas transcurridas desde created_at (ISO-8601). Devuelve 0 si no se puede parsear. */
    private long reportAgeHours(String createdAtIso) {
        if (createdAtIso == null) return 0;
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String cleaned = createdAtIso.replace("Z", "");
            if (cleaned.contains(".")) cleaned = cleaned.substring(0, cleaned.indexOf('.'));
            long createdAtMs = sdf.parse(cleaned).getTime();
            return Math.max(0, (System.currentTimeMillis() - createdAtMs) / (60 * 60 * 1000));
        } catch (Exception e) {
            android.util.Log.w("MapFragment", "No se pudo parsear created_at: " + createdAtIso);
            return 0;
        }
    }

    /** RF-19: re-evalúa filtros sobre los reportes ya conocidos, sin re-fetch. */
    private void applyFilters() {
        for (ReportResponse.ReportData report : new java.util.ArrayList<>(reportMarkers.values())) {
            if (passesFilters(report)) {
                if (!reportIconMarkers.containsKey(report.getId())) {
                    addReportMarker(report);
                }
            } else {
                removeMarkerVisuals(report.getId());
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
            cb.setChecked(filterCategories.isEmpty() || filterCategories.contains(entry.getKey()));
        }

        android.widget.RadioGroup rgStatus = dialogView.findViewById(R.id.rg_filter_status);
        int statusCheckedId = switch (filterStatus) {
            case "pending" -> R.id.rb_status_pending;
            case "verified" -> R.id.rb_status_verified;
            case "resolved" -> R.id.rb_status_resolved;
            default -> R.id.rb_status_all;
        };
        rgStatus.check(statusCheckedId);

        android.widget.RadioGroup rgAge = dialogView.findViewById(R.id.rg_filter_age);
        int ageCheckedId = switch (filterAge) {
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
                    filterCategories.clear();
                    if (!selected.isEmpty() && selected.size() < categoryCheckboxIds.size()) {
                        filterCategories.addAll(selected);
                    }

                    int checkedStatus = rgStatus.getCheckedRadioButtonId();
                    if (checkedStatus == R.id.rb_status_pending) filterStatus = "pending";
                    else if (checkedStatus == R.id.rb_status_verified) filterStatus = "verified";
                    else if (checkedStatus == R.id.rb_status_resolved) filterStatus = "resolved";
                    else filterStatus = "all";

                    int checkedAge = rgAge.getCheckedRadioButtonId();
                    if (checkedAge == R.id.rb_age_1h) filterAge = "1h";
                    else if (checkedAge == R.id.rb_age_6h) filterAge = "6h";
                    else if (checkedAge == R.id.rb_age_24h) filterAge = "24h";
                    else filterAge = "all";

                    applyFilters();
                })
                .setNeutralButton("Limpiar filtros", (dialog, which) -> {
                    filterCategories.clear();
                    filterStatus = "all";
                    filterAge = "all";
                    applyFilters();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addReportMarker(ReportResponse.ReportData report) {
        if (pointAnnotationManager == null || circleAnnotationManager == null) {
            android.util.Log.e("MapFragment", "No se puede añadir marcador: managers son nulos");
            return;
        }

        // Eliminar visuales previos para evitar duplicados en UI
        removeMarkerVisuals(report.getId());

        // RF-18/RF-19: no pintar marcador si no pasa filtros (archivado, categoría, estado, antigüedad)
        if (!passesFilters(report)) {
            reportMarkers.put(String.valueOf(report.getId()), report);
            return;
        }

        // IMPORTANTE: Point.fromLngLat requiere LONGITUD primero, luego LATITUD
        double lat = report.getLatitude();
        double lng = report.getLongitude();

        // Offset para marcadores con coords idénticas (~11m por slot)
        String coordKey = String.format(Locale.US, "%.6f,%.6f", lat, lng);
        int slotIndex = coordOccupancy.getOrDefault(coordKey, 0);
        coordOccupancy.put(coordKey, slotIndex + 1);
        if (slotIndex > 0) {
            double angle = (slotIndex - 1) * (2 * Math.PI / 6);  // máx 6 alrededor
            double offsetDeg = 0.00003;  // ~3 metros
            lat += offsetDeg * Math.cos(angle);
            lng += offsetDeg * Math.sin(angle);
        }

        String categorySlug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";

        Point point = Point.fromLngLat(lng, lat);
        String reportKey = String.valueOf(report.getId());
        reportMarkers.put(reportKey, report);
        int drawableId = getCategoryDrawableId(categorySlug);
        String categoryColor = getCategoryColor(categorySlug);
        int currentUserId = TokenManager.getInstance(requireContext()).getUserId();
        boolean isMine = (currentUserId != -1 && currentUserId == report.getUserId());

        android.util.Log.d("MapFragment", "Añadiendo marcador: ID=" + report.getId() +
                " | isMine=" + isMine + " color: " + categoryColor);

        // 1. Icono del reporte (Todo-en-uno: Icono + Borde Estado + Halo Mi Reporte)
        try {
            Bitmap iconBitmap = bitmapFromDrawable(drawableId, categoryColor, report.getStatus(), isMine);

            if (iconBitmap == null) {
                android.util.Log.w("MapFragment", "  ✗ Bitmap es null, saltando PointAnnotation");
                return;
            }

            // RF-28: marcador base más grande para usuarios con score/level alto
            double sizeMultiplier = getUserSizeMultiplier(report);

            // Crear PointAnnotation con bitmap directamente
            PointAnnotationOptions pointOptions = new PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(iconBitmap)
                    .withIconSize(1.1 * sizeMultiplier);

            PointAnnotation pointAnnotation = pointAnnotationManager.create(pointOptions);
            pointAnnotation.setDraggable(false);
            annotationToReportId.put(pointAnnotation.getId(), report.getId());
            reportIconMarkers.put(report.getId(), pointAnnotation);

            android.util.Log.d("MapFragment", "  ✓ Marcador completado (Capa única)");

        } catch (Exception e) {
            android.util.Log.e("MapFragment", "✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Bitmap bitmapFromDrawable(int drawableId, String categoryColorHex, String status, boolean isMine) {
        String cacheKey = drawableId + "_" + categoryColorHex + "_" + status + "_" + isMine;
        Bitmap cached = bitmapCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), drawableId);
            if (drawable == null) return null;

            final int SIZE = 130; // Un poco más grande para el halo
            Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            float center = SIZE / 2f;
            float radius = SIZE / 2.8f;

            // 1. Halo de "Mi Reporte" (Sutil resplandor)
            if (isMine) {
                android.graphics.Paint haloPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                haloPaint.setColor(android.graphics.Color.parseColor("#FFD700")); // Oro
                haloPaint.setAlpha(80);
                canvas.drawCircle(center, center, radius + 12, haloPaint);
            }

            // 2. Sombra base
            android.graphics.Paint shadowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            shadowPaint.setColor(android.graphics.Color.BLACK);
            shadowPaint.setAlpha(40);
            canvas.drawCircle(center, center + 4, radius, shadowPaint);

            // 3. Círculo principal (Categoría)
            android.graphics.Paint circlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(android.graphics.Color.parseColor(categoryColorHex));
            canvas.drawCircle(center, center, radius, circlePaint);

            // 4. Borde de Estado (Reemplaza al anillo ruidoso)
            android.graphics.Paint statusBorderPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            statusBorderPaint.setStyle(android.graphics.Paint.Style.STROKE);
            statusBorderPaint.setStrokeWidth(isMine ? 8f : 6f);
            statusBorderPaint.setColor(android.graphics.Color.parseColor(getStatusStrokeColor(status)));
            canvas.drawCircle(center, center, radius, statusBorderPaint);

            // 5. Icono central
            try {
                drawable.setTint(android.graphics.Color.WHITE);
            } catch (Exception ignored) {}

            int iconSize = (int) (radius * 1.1f);
            int iconOffset = (int) (center - iconSize / 2f);
            drawable.setBounds(iconOffset, iconOffset, iconOffset + iconSize, iconOffset + iconSize);
            drawable.draw(canvas);

            bitmapCache.put(cacheKey, bitmap);
            return bitmap;
        } catch (Exception e) {
            android.util.Log.e("MapFragment", "✗ Error creando bitmap: " + e.getMessage());
            return null;
        }
    }

    private String getStatusStrokeColor(String status) {
        return switch (status) {
            case "pending" -> "#FF9800";      // Naranja - pendiente
            case "verified" -> "#4CAF50";     // Verde - verificado
            case "resolved" -> "#2196F3";     // Azul - resuelto
            case "archived" -> "#9E9E9E";     // Gris - archivado
            default -> "#757575";              // Gris oscuro - desconocido
        };
    }

    private int getCategoryDrawableId(String categorySlug) {
        // Retorna el ID del drawable para cada categoría
        return switch (categorySlug) {
            case "bache", "vialidad" -> R.drawable.remove_road_24px;
            case "alumbrado-publico", "alumbrado" -> R.drawable.backlight_high_off_24px;
            case "fuga-de-agua", "agua" -> R.drawable.agua;
            case "semaforo-danado", "trafico" -> R.drawable.traffic_jam_24px;
            case "inseguridad", "seguridad" -> R.drawable.warning_24px;
            case "basura-acumulada", "parques", "basura" -> R.drawable.trash;
            default -> R.drawable.ic_category_otros;
        };
    }

    private String getCategoryColor(String categorySlug) {
        // Mapear slugs del UI (cuando se crea reporte) Y slugs de API (cuando se carga)
        return switch (categorySlug) {
            // UI slugs
            case "vialidad" -> "#FF6B6B";      // Rojo
            case "alumbrado" -> "#FFD93D";     // Amarillo
            case "agua" -> "#6BCB77";          // Verde
            case "trafico" -> "#4D96FF";       // Azul
            case "seguridad" -> "#9D4EDD";     // Púrpura
            case "parques" -> "#06D6A0";       // Turquesa
            case "basura" -> "#8B5A3C";        // Marrón

            // API slugs
            case "bache" -> "#FF6B6B";                    // Rojo (vialidad)
            case "alumbrado-publico" -> "#FFD93D";       // Amarillo (alumbrado)
            case "fuga-de-agua" -> "#6BCB77";             // Verde (agua)
            case "semaforo-danado" -> "#4D96FF";          // Azul (tráfico)
            case "inseguridad" -> "#9D4EDD";              // Púrpura (seguridad)
            case "basura-acumulada" -> "#8B5A3C";         // Marrón (basura)

            default -> "#808080";  // Gris (fallback)
        };
    }

    private double getRadiusByStatus(String status) {
        if (status == null) return 5.0;
        return switch (status) {
            case "verified" -> 8.0;
            case "resolved" -> 6.0;
            default -> 5.0;
        };
    }

    private double getOpacityByStatus(String status) {
        if (status == null) return 0.7;
        return switch (status) {
            case "pending" -> 0.8;
            case "verified" -> 0.9;
            case "resolved" -> 0.5;
            default -> 0.7;
        };
    }

    private void setupMapListeners() {
        GesturesUtils.getGestures(mapView).addOnMapLongClickListener(point -> {
            // Ya NO sobreescribimos userLocation. 
            // La ubicación del usuario es sagrada para el botón FAB.
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
        if (hasSameCategoryNearby(categoryId, latitude, longitude, 0.00045)) {
            SnackbarHelper.show(getView(), "Ya existe un reporte de esta categoría cerca", SnackbarHelper.Variant.WARNING);
            android.util.Log.w("MapFragment", "Reporte duplicado bloqueado: cat=" + category + " en " + latitude + "," + longitude);
            return;
        }

        String token = TokenManager.getInstance(requireContext()).getToken();

        if (token.isEmpty()) {
            android.util.Log.w("MapFragment", "Token de autenticación no encontrado");
            SnackbarHelper.show(getView(), "Iniciá sesión para reportar", SnackbarHelper.Variant.WARNING);
            return;
        }

        // Mapbox usa (longitude, latitude) pero nuestra API y UI usualmente (latitude, longitude)
        // Aseguramos que el request lleve los valores en los campos correctos
        ReportRequest request = new ReportRequest(categoryId, latitude, longitude, "Reporte desde app");

        if (!ConnectivityHelper.isOnline(requireContext())) {
            queueOfflineReport(categoryId, latitude, longitude, "Reporte desde app");
            return;
        }

        ApiClient.getInstance().createReport("Bearer " + token, request).enqueue(new Callback<CreateReportResponse>() {
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

    private boolean hasSameCategoryNearby(int categoryId, double lat, double lng, double radiusDeg) {
        for (ReportResponse.ReportData report : reportMarkers.values()) {
            if (report.getCategory() == null) continue;
            if (report.getCategory().getId() != categoryId) continue;
            double dLat = report.getLatitude() - lat;
            double dLng = report.getLongitude() - lng;
            if (Math.sqrt(dLat * dLat + dLng * dLng) <= radiusDeg) return true;
        }
        return false;
    }

    private int getCategoryIdBySlug(String slug) {
        return switch (slug) {
            case "vialidad" -> 1;   // bache
            case "alumbrado" -> 2;  // alumbrado-publico
            case "agua" -> 4;       // fuga-de-agua
            case "trafico" -> 5;    // semaforo-danado
            case "seguridad" -> 6;  // inseguridad
            case "parques" -> 3;    // basura-acumulada (temp)
            case "basura" -> 3;     // basura-acumulada
            default -> 3;
        };
    }

    private void showReportDetails(ReportResponse.ReportData report) {
        if (report == null) return;
        // Pasar ubicación real del usuario para validar distancia de votación
        ReportDetailBottomSheet bottomSheet = ReportDetailBottomSheet.newInstance(report, userLocation);
        // Pasar listener para cambios de estado
        bottomSheet.setStatusChangeListener(this);
        bottomSheet.show(getChildFragmentManager(), "report_detail");
    }

    @Override
    public void onReportStatusChanged(int reportId, String newStatus, int confirmCount, int resolveCount) {
        android.util.Log.d("MapFragment", "═══════════════════════════════════════════");
        android.util.Log.d("MapFragment", "📊 onReportStatusChanged() - ReportID: " + reportId);
        
        updateReportMarker(reportId, newStatus, confirmCount, resolveCount);
    }

    @Override
    public void onReportDataUpdated(ReportResponse.ReportData updatedReport) {
        if (updatedReport != null) {
            android.util.Log.d("MapFragment", "💾 Sincronizando caché: Reporte " + updatedReport.getId() + " actualizado.");
            reportMarkers.put(String.valueOf(updatedReport.getId()), updatedReport);
        }
    }

    @Override
    public void onReportRetracted(int reportId) {
        android.util.Log.d("MapFragment", "🗑️ Reporte retirado: ID=" + reportId);
        reportMarkers.remove(String.valueOf(reportId));

        PointAnnotation icon = reportIconMarkers.remove(reportId);
        if (icon != null && pointAnnotationManager != null) pointAnnotationManager.delete(icon);

        CircleAnnotation stroke = reportStrokeMarkers.remove(reportId);
        if (stroke != null && circleAnnotationManager != null) circleAnnotationManager.delete(stroke);
    }

    private void updateReportMarker(int reportId, String newStatus, int confirmCount, int resolveCount) {
        ReportResponse.ReportData report = reportMarkers.get(String.valueOf(reportId));
        if (report == null) return;

        // Actualizar datos del reporte
        report.setStatus(newStatus.toLowerCase());
        if (report.getVotes() != null) {
            report.getVotes().setConfirm(confirmCount);
            report.getVotes().setResolve(resolveCount);
        }

        // Simplemente volvemos a llamar a addReportMarker, que ya tiene lógica de limpieza
        addReportMarker(report);
        android.util.Log.d("MapFragment", "✓ Marcador regenerado con nuevo estado: " + newStatus);
    }

    private void animateBounce(PointAnnotation annotation) {
        float baseSize = 1.6f;
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(baseSize, baseSize * 1.4f, baseSize);
        animator.setDuration(400);
        animator.setInterpolator(new android.view.animation.OvershootInterpolator());
        animator.addUpdateListener(animation1 -> {
            annotation.setIconSize(((Float) animation1.getAnimatedValue()).doubleValue());
            pointAnnotationManager.update(annotation);
        });
        animator.start();
    }

    private void updateMarkersScale(double zoom) {
        // Factor de escala optimizado para que no se vea gigante con mucho zoom
        // Referencia: Zoom 15 = 1.0. Crecimiento muy sutil (base 1.06)
        float scaleFactor = (float) Math.max(0.6, Math.min(1.4, Math.pow(1.06, zoom - 15)));

        // Tamaño base más pequeño (1.1f en lugar de 1.6f)
        double baseIconSize = 1.1 * scaleFactor;

        for (java.util.Map.Entry<Integer, PointAnnotation> entry : reportIconMarkers.entrySet()) {
            ReportResponse.ReportData report = reportMarkers.get(String.valueOf(entry.getKey()));
            double sizeMultiplier = (report != null) ? getUserSizeMultiplier(report) : 1.0;
            entry.getValue().setIconSize(baseIconSize * sizeMultiplier);
        }

        if (!reportIconMarkers.isEmpty()) {
            pointAnnotationManager.update(pointAnnotationManager.getAnnotations());
        }
    }

    // RF-28: usuarios con score/level alto obtienen marcadores con tamaño base mayor
    private double getUserSizeMultiplier(ReportResponse.ReportData report) {
        ReportResponse.UserInfo user = report.getUser();
        if (user == null || user.getLevel() == null) return 1.0;
        switch (user.getLevel()) {
            case "experto":
                return 1.3;
            case "guardian":
                return 1.2;
            case "colaborador":
                return 1.1;
            default:
                return 1.0;
        }
    }

    private void setupFAB() {
        // Botón: Mi ubicación
        binding.fabMyLocation.setOnClickListener(v -> {
            if (userLocation != null) {
                animateCameraTo(userLocation);
            } else {
                getCurrentUserLocation();
            }
        });

        // Botón: Agregar reporte
        binding.fabAddReport.setOnClickListener(v -> {
            if (userLocation != null) {
                showRadialMenu(userLocation);
            } else {
                SnackbarHelper.show(getView(), "Obteniendo ubicación...", SnackbarHelper.Variant.INFO);
                getCurrentUserLocation();
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
        if (mapView != null) {
            mapView.onStart();
        }
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
        votableReportsHandler.postDelayed(votableReportsRunnable, VOTABLE_REPORTS_CHECK_INTERVAL_MS);
        startContinuousLocationTracking();
    }

    @Override
    public void onStop() {
        pollHandler.removeCallbacks(pollRunnable);
        votableReportsHandler.removeCallbacks(votableReportsRunnable);
        stopContinuousLocationTracking();
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    /**
     * Check for nearby votable reports and notify user (Option B - periodic local notifications)
     * Called every 30 seconds while map is visible
     */
    private void checkAndNotifyNearbyVotableReports() {
        if (userLocation == null || reportMarkers.isEmpty()) {
            return;
        }

        try {
            int currentUserId = TokenManager.getInstance(requireContext()).getUserId();
            java.util.List<ReportResponse.ReportData> votableReports = new java.util.ArrayList<>();

            // Calculate distance to each report
            for (ReportResponse.ReportData report : reportMarkers.values()) {
                // Skip archived reports
                if (report.getStatus() != null && report.getStatus().equals("archived")) {
                    continue;
                }

                // Skip own reports (can't vote on them)
                if (report.getUser() != null && report.getUser().getId() == currentUserId) {
                    continue;
                }

                // Calculate Haversine distance
                double distance = calculateDistance(
                        userLocation.latitude(),
                        userLocation.longitude(),
                        report.getLatitude(),
                        report.getLongitude()
                );

                // If within votable range, add to list
                if (distance <= VOTABLE_RANGE_KM) {
                    votableReports.add(report);
                }
            }

            int votableCount = votableReports.size();

            // Only notify if count changed (avoid spamming same notification)
            if (votableCount > 0 && votableCount != lastNotifiedVotableCount) {
                lastNotifiedVotableCount = votableCount;
                notifyVotableReportsNearby(votableCount);
                android.util.Log.d("MapFragment", "🗳️ Found " + votableCount + " votable reports nearby");
            } else if (votableCount == 0 && lastNotifiedVotableCount > 0) {
                lastNotifiedVotableCount = 0;
                // Optionally clear notification when no more reports nearby
            }

        } catch (Exception e) {
            android.util.Log.e("MapFragment", "Error checking votable reports", e);
        }
    }

    /**
     * Show local notification about votable reports nearby
     */
    private void notifyVotableReportsNearby(int count) {
        try {
            String title = "¡Puedes votar!";
            String message = count == 1 ?
                    "Hay 1 reporte cerca donde puedes votar" :
                    "Hay " + count + " reportes cerca donde puedes votar";

            // Create intent to open map (focus on votable reports)
            Intent intent = new Intent(requireContext(), com.bombayashi.reporteciudadano.MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    requireContext(),
                    999,  // Unique ID for votable reports notification
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );

            // Use the votable reports notification channel
            String channelId = NotificationChannelHelper.CHANNEL_ID_REPORTS;

            androidx.core.app.NotificationCompat.Builder notificationBuilder =
                    new androidx.core.app.NotificationCompat.Builder(requireContext(), channelId)
                            .setSmallIcon(R.drawable.warning_24px)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH);

            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) requireContext()
                            .getSystemService(android.content.Context.NOTIFICATION_SERVICE);

            if (notificationManager != null) {
                notificationManager.notify(999, notificationBuilder.build());
                android.util.Log.d("MapFragment", "✓ Notified: " + message);
            }

        } catch (Exception e) {
            android.util.Log.e("MapFragment", "Error showing votable reports notification", e);
        }
    }

    /**
     * Calculate distance between two coordinates using Haversine formula (in km)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS_KM = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

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
        return reportMarkers.get(String.valueOf(reportId));
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
                        reportMarkers.put(String.valueOf(reportId), report);
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
        String preset = "dusk"; // TODO: restaurar lógica dinámica por hora
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
