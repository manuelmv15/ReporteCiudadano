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

import com.bombayashi.reporteciudadano.R;
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

public class MapFragment extends Fragment {

    private FragmentMapBinding binding;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Point currentLocation;
    private FusedLocationProviderClient fusedLocationClient;
    private CircleAnnotationManager circleAnnotationManager;
    private PointAnnotationManager pointAnnotationManager;
    private CircleAnnotation userLocationMarker;
    private com.mapbox.maps.viewannotation.ViewAnnotationManager viewAnnotationManager;

    private static final double DEFAULT_LATITUDE = -34.6037;
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private java.util.HashMap<String, ReportResponse.ReportData> reportMarkers = new java.util.HashMap<>();
    private java.util.HashMap<String, Integer> annotationToReportId = new java.util.HashMap<>();  // UUID → ReportID

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

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (!isAdded() || getView() == null) return;

            if (location != null) {
                currentLocation = Point.fromLngLat(location.getLongitude(), location.getLatitude());
                addUserLocationMarker(currentLocation);
                animateCameraTo(currentLocation);
                SnackbarHelper.show(
                        getView(),
                        String.format(Locale.getDefault(), "Tu ubicación: %.4f, %.4f", location.getLatitude(), location.getLongitude()),
                        SnackbarHelper.Variant.SUCCESS
                );
            } else {
                centerOnDefaultLocation();
            }
        });
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
        android.util.Log.d("MapFragment", "Iniciando carga de reportes desde API...");

        // Cargar TODOS los reportes sin filtro de status
        ApiClient.getInstance().getReports("", 100).enqueue(new Callback<ReportResponse>() {
            @Override
            public void onResponse(@NonNull Call<ReportResponse> call, @NonNull Response<ReportResponse> response) {
                if (!isAdded() || getView() == null) {
                    android.util.Log.w("MapFragment", "Fragment no está adjunto o view es null");
                    return;
                }

                if (response.isSuccessful() && response.body() != null) {
                    ReportResponse reportResponse = response.body();
                    java.util.List<ReportResponse.ReportData> reports = reportResponse.getData();

                    android.util.Log.d("MapFragment", "✓ Respuesta exitosa. Total reportes: " +
                            (reports != null ? reports.size() : "null"));

                    if (reports != null && !reports.isEmpty()) {
                        android.util.Log.d("MapFragment", "Agregando " + reports.size() + " marcadores al mapa");
                        for (ReportResponse.ReportData report : reports) {
                            android.util.Log.d("MapFragment", "  - Reporte ID:" + report.getId() +
                                    " Cat:" + (report.getCategory() != null ? report.getCategory().getSlug() : "null") +
                                    " Coords:" + report.getLatitude() + "," + report.getLongitude());
                            addReportMarker(report);
                        }
                        SnackbarHelper.show(getView(), "Reportes cargados: " + reports.size(), SnackbarHelper.Variant.INFO);
                    } else {
                        android.util.Log.w("MapFragment", "Lista de reportes está vacía");
                        SnackbarHelper.show(getView(), "Sin reportes disponibles", SnackbarHelper.Variant.INFO);
                    }
                } else {
                    android.util.Log.e("MapFragment", "✗ Respuesta no exitosa. Código: " + response.code());
                    SnackbarHelper.show(getView(), "Error: " + response.code(), SnackbarHelper.Variant.ERROR);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ReportResponse> call, @NonNull Throwable t) {
                if (!isAdded() || getView() == null) return;
                android.util.Log.e("MapFragment", "✗ Error al cargar reportes: " + t.getMessage(), t);
                SnackbarHelper.show(getView(), "Error de red", SnackbarHelper.Variant.ERROR);
            }
        });
    }

    private void addReportMarker(ReportResponse.ReportData report) {
        if (pointAnnotationManager == null || circleAnnotationManager == null) {
            android.util.Log.e("MapFragment", "No se puede añadir marcador: managers son nulos");
            return;
        }

        // IMPORTANTE: Point.fromLngLat requiere LONGITUD primero, luego LATITUD
        Point point = Point.fromLngLat(report.getLongitude(), report.getLatitude());
        String reportKey = String.valueOf(report.getId());
        reportMarkers.put(reportKey, report);

        String categorySlug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
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

            // Crear PointAnnotation con bitmap directamente
            PointAnnotationOptions pointOptions = new PointAnnotationOptions()
                    .withPoint(point)
                    .withIconImage(iconBitmap)
                    .withIconSize(2.5f);

            PointAnnotation pointAnnotation = pointAnnotationManager.create(pointOptions);
            pointAnnotation.setDraggable(false);
            annotationToReportId.put(pointAnnotation.getId(), report.getId());

            android.util.Log.d("MapFragment", "  ✓ PointAnnotation creada");

            // 2. Stroke del estado (CircleAnnotation - solo borde)
            CircleAnnotationOptions strokeOptions = new CircleAnnotationOptions()
                    .withPoint(point)
                    .withCircleRadius(28.0)
                    .withCircleColor("#00000000")  // Transparente
                    .withCircleOpacity(0.0)
                    .withCircleStrokeWidth(4.0)
                    .withCircleStrokeColor(statusStrokeColor);

            CircleAnnotation strokeAnnotation = circleAnnotationManager.create(strokeOptions);
            strokeAnnotation.setDraggable(false);

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
            // point.latitude() y point.longitude() ya vienen correctamente de Mapbox
            currentLocation = point;
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

        String token = requireActivity().getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                .getString("token", "");

        if (token.isEmpty()) {
            android.util.Log.w("MapFragment", "Token de autenticación no encontrado");
            SnackbarHelper.show(getView(), "Iniciá sesión para reportar", SnackbarHelper.Variant.WARNING);
            return;
        }

        int categoryId = getCategoryIdBySlug(category);
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

    private int getCategoryIdBySlug(String slug) {
        return switch (slug) {
            case "vialidad" -> 1;       // bache
            case "alumbrado" -> 2;      // alumbrado-publico
            case "agua" -> 4;           // fuga-de-agua
            case "trafico" -> 5;        // semaforo-danado
            case "seguridad" -> 6;      // inseguridad
            case "parques" -> 3;        // basura-acumulada (temp mapping)
            case "basura" -> 3;         // basura-acumulada
            default -> 3;               // default: basura-acumulada
        };
    }

    private void showReportDetails(ReportResponse.ReportData report) {
        if (report == null) return;
        ReportDetailBottomSheet bottomSheet = ReportDetailBottomSheet.newInstance(report);
        bottomSheet.show(getChildFragmentManager(), "report_detail");
    }

    private void setupFAB() {
        FloatingActionButton fabMyLocation = binding.fabMyLocation;
        fabMyLocation.setOnClickListener(v -> {
            if (currentLocation != null) {
                animateCameraTo(currentLocation);
            } else {
                getCurrentUserLocation();
            }
        });
    }

    private void centerOnDefaultLocation() {
        currentLocation = Point.fromLngLat(DEFAULT_LONGITUDE, DEFAULT_LATITUDE);
        addUserLocationMarker(currentLocation);
        animateCameraTo(currentLocation);
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
