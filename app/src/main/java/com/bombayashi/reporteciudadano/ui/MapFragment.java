package com.bombayashi.reporteciudadano.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.FragmentMapBinding;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.common.MapboxOptions;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.plugin.animation.CameraAnimationsUtils;
import com.mapbox.maps.plugin.gestures.GesturesUtils;

public class MapFragment extends Fragment {

    private FragmentMapBinding binding;
    private MapView mapView;
    private MapboxMap mapboxMap;
    private Point currentLocation;
    private static final double DEFAULT_LATITUDE = -34.6037; // Buenos Aires
    private static final double DEFAULT_LONGITUDE = -58.3816;
    private static final double DEFAULT_ZOOM = 15.0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        String token = getString(R.string.mapbox_access_token);
        if (!token.isEmpty()) {
            MapboxOptions.setAccessToken(token);
        }
        binding = FragmentMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = binding.mapView;
        mapView.getMapboxMap().loadStyleUri(com.mapbox.maps.Style.MAPBOX_STREETS, style -> {
            mapboxMap = mapView.getMapboxMap();
            setupMapListeners();
            setupFAB();
            centerOnDefaultLocation();
        });
    }

    private void setupMapListeners() {
        GesturesUtils.getGestures(mapView).addOnMapLongClickListener(point -> {
            currentLocation = point;
            SnackbarHelper.show(
                    requireActivity().findViewById(android.R.id.content),
                    String.format(Locale.getDefault(), "Ubicación capturada: %.4f, %.4f", point.latitude(), point.longitude()),
                    SnackbarHelper.Variant.INFO
            );
            return true;
        });
    }

    private void setupFAB() {
        FloatingActionButton fabMyLocation = binding.fabMyLocation;
        fabMyLocation.setOnClickListener(v -> centerOnCurrentLocation());
    }

    private void centerOnDefaultLocation() {
        currentLocation = Point.fromLngLat(DEFAULT_LONGITUDE, DEFAULT_LATITUDE);
        animateCameraTo(currentLocation);
    }

    private void centerOnCurrentLocation() {
        if (currentLocation != null) {
            animateCameraTo(currentLocation);
        }
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
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
