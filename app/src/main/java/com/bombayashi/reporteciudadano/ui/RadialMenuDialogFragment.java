package com.bombayashi.reporteciudadano.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.DialogRadialMenuBinding;
import com.mapbox.geojson.Point;

public class RadialMenuDialogFragment extends DialogFragment {

    private DialogRadialMenuBinding binding;
    private Point capturedLocation;
    private OnCategorySelectedListener listener;

    public interface OnCategorySelectedListener {
        void onCategorySelected(String category, double latitude, double longitude);
    }

    public static RadialMenuDialogFragment newInstance(Point location, OnCategorySelectedListener listener) {
        RadialMenuDialogFragment fragment = new RadialMenuDialogFragment();
        fragment.capturedLocation = location;
        fragment.listener = listener;
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogRadialMenuBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupCategoryButtons();
    }

    private void setupCategoryButtons() {
        binding.fabVialidad.setOnClickListener(v -> onCategoryClicked("vialidad"));
        binding.fabAlumbrado.setOnClickListener(v -> onCategoryClicked("alumbrado"));
        binding.fabAgua.setOnClickListener(v -> onCategoryClicked("agua"));
        binding.fabTrafico.setOnClickListener(v -> onCategoryClicked("trafico"));
        binding.fabSeguridad.setOnClickListener(v -> onCategoryClicked("seguridad"));
        binding.fabParques.setOnClickListener(v -> onCategoryClicked("parques"));
        binding.fabBasura.setOnClickListener(v -> onCategoryClicked("basura"));
        binding.fabOtros.setOnClickListener(v -> onCategoryClicked("otros"));
    }

    private void onCategoryClicked(String category) {
        if (capturedLocation != null && listener != null) {
            listener.onCategorySelected(category, capturedLocation.latitude(), capturedLocation.longitude());
        }
        dismiss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
