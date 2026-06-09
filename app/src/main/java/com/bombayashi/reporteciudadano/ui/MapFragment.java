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
    private CircleAnnotation userLocationMarker;

    private static final double DEFAULT_LATITUDE = -34.6037;
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private java.util.HashMap<String, ReportResponse.ReportData> reportMarkers = new java.util.HashMap<>();

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
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS, style -> {
            android.util.Log.d("MapFragment", "1. Estilo cargado");
            mapboxMap = mapView.getMapboxMap();

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
            circleAnnotationManager = CircleAnnotationManagerKt.createCircleAnnotationManager(
                    annotationPlugin,
                    new AnnotationConfig()
            );

            if (circleAnnotationManager != null) {
                android.util.Log.d("MapFragment", "✓ CircleAnnotationManager inicializado correctamente");

                circleAnnotationManager.addClickListener(annotation -> {
                    String annId = annotation.getId();
                    android.util.Log.d("MapFragment", "Click en anotación: " + annId);
                    ReportResponse.ReportData report = reportMarkers.get(annId);
                    if (report != null) {
                        android.util.Log.d("MapFragment", "Abriendo detalles del reporte ID: " + report.getId());
                        showReportDetails(report);
                        return true;
                    } else {
                        android.util.Log.w("MapFragment", "No se encontró reporte para ID: " + annId);
                    }
                    return false;
                });
            } else {
                android.util.Log.e("MapFragment", "✗ CircleAnnotationManager es null después de crear");
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
        if (circleAnnotationManager == null) {
            android.util.Log.e("MapFragment", "No se puede añadir marcador: circleAnnotationManager es nulo");
            return;
        }

        // IMPORTANTE: Point.fromLngLat requiere LONGITUD primero, luego LATITUD
        Point point = Point.fromLngLat(report.getLongitude(), report.getLatitude());
        String markerId = "report_" + report.getId();
        reportMarkers.put(markerId, report);

        String categorySlug = (report.getCategory() != null) ? report.getCategory().getSlug() : "otros";
        String categoryColor = getCategoryColor(categorySlug);
        double radius = getRadiusByStatus(report.getStatus());
        double opacity = getOpacityByStatus(report.getStatus());

        android.util.Log.d("MapFragment", "Añadiendo marcador: " + markerId + " en " + point.latitude() + "," + point.longitude() + " category: " + categorySlug + " color: " + categoryColor);

        CircleAnnotationOptions options = new CircleAnnotationOptions()
                .withPoint(point)
                .withCircleRadius(radius)
                .withCircleColor(categoryColor)
                .withCircleOpacity(opacity)
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#FFFFFF");

        CircleAnnotation annotation = circleAnnotationManager.create(options);
        annotation.setDraggable(false);
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
