package com.bombayashi.reporteciudadano.ui;

import android.Manifest;
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
import com.bombayashi.reporteciudadano.network.ApiClient;
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

    private static final double DEFAULT_LATITUDE = -34.6037;
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int INITIAL_REPORTS_PAGE_SIZE = 25;  // Pagination: cargar 25 iniciales
    private static final int MAX_REPORTS = 200;  // Máximo de reportes en caché local
    private java.util.HashMap<String, ReportResponse.ReportData> reportMarkers = new java.util.HashMap<>();
    private java.util.HashMap<String, Integer> annotationToReportId = new java.util.HashMap<>();  // UUID → ReportID
    private java.util.HashMap<Integer, CircleAnnotation> reportStrokeMarkers = new java.util.HashMap<>();  // ReportID → CircleAnnotation (stroke)
    private java.util.HashMap<Integer, CircleAnnotation> reportStatusCircles = new java.util.HashMap<>();  // ReportID → CircleAnnotation (status)
    private java.util.HashMap<String, Integer> coordOccupancy = new java.util.HashMap<>();  // "lat,lng" → count, para offset de duplicados
    private int currentReportsPage = 1;  // Para pagination
    private boolean isLoadingReports = false;  // Flag para evitar duplicar requests

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

        mapView = binding.mapView;
        viewAnnotationManager = mapView.getViewAnnotationManager();

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            mapboxMap = mapView.getMapboxMap();
            android.util.Log.d("MapFragment", "1. Estilo cargado");

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

                pointAnnotationManager.addClickListener(annotation -> {
                    String annotationUUID = annotation.getId();
                    Integer reportId = annotationToReportId.get(annotationUUID);

                    android.util.Log.d("MapFragment", "✓ Click en icono UUID:" + annotationUUID.substring(0, 8) + "...");

                    if (reportId != null) {
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

    private void loadReportsFromAPI() {
        if (isLoadingReports) {
            android.util.Log.w("MapFragment", "⏳ Ya está cargando reportes, ignorando solicitud duplicada");
            return;
        }

        android.util.Log.d("MapFragment", "📥 Cargando reportes (página " + currentReportsPage + ")...");
        isLoadingReports = true;

        // Cargar con pagination: 25 reportes por página
        ApiClient.getInstance().getReports("", INITIAL_REPORTS_PAGE_SIZE).enqueue(new Callback<ReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportResponse> call, @NonNull Response<ReportResponse> response) {
                isLoadingReports = false;

                if (!isAdded() || getView() == null) {
                    android.util.Log.w("MapFragment", "Fragment no está adjunto o view es null");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    ReportResponse reportResponse = response.body();
                    java.util.List<ReportResponse.ReportData> reports = reportResponse.getData();

                    android.util.Log.d("MapFragment", "✓ Reportes recibidos: " +
                            (reports != null ? reports.size() : "0"));

                    if (reports != null && !reports.isEmpty()) {
                        // Limitar caché local a MAX_REPORTS
                        if (reportMarkers.size() >= MAX_REPORTS) {
                            android.util.Log.w("MapFragment", "⚠️ Caché de reportes alcanzó máximo (" + MAX_REPORTS + "), ignorando más");
                            return;
                        }

                        int addedCount = 0;
                        for (ReportResponse.ReportData report : reports) {
                            if (reportMarkers.size() < MAX_REPORTS) {
                                addReportMarker(report);
                                addedCount++;
                            }
                        }

                        android.util.Log.d("MapFragment", "📍 Agregados " + addedCount + " marcadores");
                        currentReportsPage++;

                        if (addedCount > 0) {
                            SnackbarHelper.show(getView(), "Cargados " + addedCount + " reportes",
                                    SnackbarHelper.Variant.INFO);
                        }
                    } else {
                        android.util.Log.w("MapFragment", "⚠️ Lista de reportes vacía");
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

    private void addReportMarker(ReportResponse.ReportData report) {
        if (pointAnnotationManager == null || circleAnnotationManager == null) {
            android.util.Log.e("MapFragment", "No se puede añadir marcador: managers son nulos");
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
        String statusStrokeColor = getStatusStrokeColor(report.getStatus());

        android.util.Log.d("MapFragment", "Añadiendo marcador: ID=" + report.getId() +
                " en " + String.format("%.4f", point.latitude()) + "," + String.format("%.4f", point.longitude()) +
                " category: " + categorySlug + " color: " + categoryColor);

        // 1. Icono del reporte (PointAnnotation con bitmap directo)
        try {
            Bitmap iconBitmap = bitmapFromDrawable(drawableId, categoryColor);

            if (iconBitmap == null) {
                android.util.Log.w("MapFragment", "  ✗ Bitmap es null, saltando PointAnnotation");
                return;
            }

            // Crear PointAnnotation con bitmap directamente (25% más pequeño)
            PointAnnotationOptions pointOptions = new PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(iconBitmap)
                    .withIconSize(1.9f);  // Reducido de 2.5f (25% menor)

            PointAnnotation pointAnnotation = pointAnnotationManager.create(pointOptions);
            pointAnnotation.setDraggable(false);
            annotationToReportId.put(pointAnnotation.getId(), report.getId());

            android.util.Log.d("MapFragment", "  ✓ PointAnnotation creada");

            // 2. Stroke del estado (CircleAnnotation - ajustado para cerrar el gap)
            CircleAnnotationOptions strokeOptions = new CircleAnnotationOptions()
                    .withPoint(point)
                    .withCircleRadius(38.0)  // Ajustado para contacto directo con icono (25% reducción)
                    .withCircleColor("#00000000")  // Transparente
                    .withCircleOpacity(0.0)
                    .withCircleStrokeWidth(6.0)  // Stroke grueso y visible
                    .withCircleStrokeColor(statusStrokeColor);

            CircleAnnotation strokeAnnotation = circleAnnotationManager.create(strokeOptions);
            strokeAnnotation.setDraggable(false);

            // Guardar referencia para actualizaciones posteriores
            reportStrokeMarkers.put(report.getId(), strokeAnnotation);

            android.util.Log.d("MapFragment", "  ✓ Marcador completado");

        } catch (Exception e) {
            android.util.Log.e("MapFragment", "✗ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Bitmap bitmapFromDrawable(int drawableId, String categoryColorHex) {
        try {
            android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), drawableId);

            if (drawable == null) {
                android.util.Log.e("MapFragment", "✗ Drawable es null");
                return null;
            }

            // Tamaño del bitmap
            final int SIZE = 96;

            // Si es BitmapDrawable, procesar igual
            Bitmap bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // 1. Dibujar círculo de color de categoría
            android.graphics.Paint circlePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            circlePaint.setColor(android.graphics.Color.parseColor(categoryColorHex));
            canvas.drawCircle(SIZE / 2f, SIZE / 2f, SIZE / 2.2f, circlePaint);

            // 2. Dibujar icono blanco en el centro
            try {
                drawable.setTint(0xFFFFFFFF);  // Icono blanco
            } catch (Exception ignored) {
            }

            int iconSize = (int) (SIZE * 0.6);  // 60% del tamaño total
            int iconOffset = (SIZE - iconSize) / 2;
            drawable.setBounds(iconOffset, iconOffset, iconOffset + iconSize, iconOffset + iconSize);
            drawable.draw(canvas);

            android.util.Log.d("MapFragment", "  ✓ Bitmap circular creado: " + SIZE + "x" + SIZE + " color: " + categoryColorHex);
            return bitmap;

        } catch (Exception e) {
            android.util.Log.e("MapFragment", "✗ Error: " + e.getMessage());
            e.printStackTrace();
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
        android.util.Log.d("MapFragment", "   Status: " + newStatus);
        android.util.Log.d("MapFragment", "   Confirm: " + confirmCount + ", Resolve: " + resolveCount);

        updateReportMarker(reportId, newStatus, confirmCount, resolveCount);
    }

    private void updateReportMarker(int reportId, String newStatus, int confirmCount, int resolveCount) {
        CircleAnnotation strokeMarker = reportStrokeMarkers.get(reportId);
        if (strokeMarker == null) {
            android.util.Log.w("MapFragment", "⚠️ No se encontró marcador de stroke para reportId: " + reportId);
            return;
        }

        android.util.Log.d("MapFragment", "✓ Marcador encontrado, actualizando...");

        // Determinar color y radio según estado
        String newColor = "#757575";  // Valor por defecto
        double newRadius = 28.0;

        switch (newStatus) {
            case "VERIFIED":
                newColor = "#4CAF50";  // Verde
                newRadius = 35.0;      // Más grande
                android.util.Log.d("MapFragment", "✅ Reporte VERIFICADO - Verde");
                break;

            case "RESOLVED":
                newColor = "#9E9E9E";  // Gris
                newRadius = 30.0;
                android.util.Log.d("MapFragment", "✅ Reporte RESUELTO - Gris");
                break;

            case "PENDING":
            default:
                ReportResponse.ReportData report = reportMarkers.get(String.valueOf(reportId));
                if (report != null) {
                    newColor = getStatusStrokeColor(report.getStatus());
                }
                android.util.Log.d("MapFragment", "ℹ️ Reporte PENDIENTE - Color de estado");
        }

        // Actualizar marcador - eliminar anterior y crear nuevo con nuevos valores
        circleAnnotationManager.delete(strokeMarker);

        CircleAnnotationOptions updatedOptions = new CircleAnnotationOptions()
            .withPoint(strokeMarker.getPoint())
            .withCircleColor("#00000000")
            .withCircleOpacity(0.0)
            .withCircleStrokeWidth(6.0)
            .withCircleStrokeColor(newColor)
            .withCircleRadius(newRadius);

        CircleAnnotation updatedStroke = circleAnnotationManager.create(updatedOptions);
        reportStrokeMarkers.put(reportId, updatedStroke);

        android.util.Log.d("MapFragment", "✓ Marcador actualizado: color=" + newColor + ", radio=" + newRadius);
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
        TokenManager.getInstance(requireContext()).clearAuth();
        updateFabVisibility();
        if (getView() != null)
            SnackbarHelper.show(getView(), "Sesión expirada. Iniciá sesión de nuevo.", SnackbarHelper.Variant.WARNING);
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
    }

    @Override
    public void onStop() {
        if (mapView != null) {
            mapView.onStop();
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mapView != null) {
            mapView.onDestroy();
        }
        binding = null;
    }
}
