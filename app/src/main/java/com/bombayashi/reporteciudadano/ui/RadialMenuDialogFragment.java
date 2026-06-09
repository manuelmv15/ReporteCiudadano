package com.bombayashi.reporteciudadano.ui;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.DialogRadialMenuBinding;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mapbox.geojson.Point;

public class RadialMenuDialogFragment extends DialogFragment {

    private DialogRadialMenuBinding binding;
    private Point capturedLocation;
    private OnCategorySelectedListener listener;

    // Distancia mínima para considerar un deslizamiento (en dp)
    private static final float SWIPE_THRESHOLD_DP = 50;

    // Duración de animaciones M3 (milisegundos)
    private static final int ANIMATION_DURATION_SHORT = 200;
    private static final int ANIMATION_DURATION_MEDIUM = 300;

    private float swipeThresholdPx;
    private float startX, startY;
    private boolean isSwipingOut = false;

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

        // Convertir dp a px
        swipeThresholdPx = SWIPE_THRESHOLD_DP * getResources().getDisplayMetrics().density;
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
        setupTouchInteractions();
        animateMenuEntrance();
    }

    // ===== SETUP BOTONES =====
    private void setupCategoryButtons() {
        setupFabWithAnimations(binding.fabVialidad, "vialidad");
        setupFabWithAnimations(binding.fabAlumbrado, "alumbrado");
        setupFabWithAnimations(binding.fabAgua, "agua");
        setupFabWithAnimations(binding.fabTrafico, "trafico");
        setupFabWithAnimations(binding.fabSeguridad, "seguridad");
        setupFabWithAnimations(binding.fabParques, "parques");
        setupFabWithAnimations(binding.fabBasura, "basura");
        setupFabWithAnimations(binding.fabOtros, "otros");
    }

    private void setupFabWithAnimations(FloatingActionButton fab, String category) {
        fab.setOnClickListener(v -> {
            // Animación de click rápida + dismiss inmediato
            animateFabClick(fab);
            onCategorySelected(category);
        });

        // Touch listeners para hover/animations
        fab.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    animateFabHover(fab, true);
                    return false;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateFabHover(fab, false);
                    return false;
            }
            return false;
        });
    }

    // ===== ANIMACIONES =====

    private void animateMenuEntrance() {
        // Fade in del fondo
        View bg = binding.getRoot().getChildAt(0);
        bg.setAlpha(0.0f);
        bg.animate()
                .alpha(1.0f)
                .setDuration(ANIMATION_DURATION_MEDIUM)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Scale in + fade in de botones con delay escalonado
        FloatingActionButton[] fabs = {
            binding.fabVialidad, binding.fabAlumbrado, binding.fabAgua, binding.fabTrafico,
            binding.fabSeguridad, binding.fabParques, binding.fabBasura, binding.fabOtros
        };

        for (int i = 0; i < fabs.length; i++) {
            FloatingActionButton fab = fabs[i];
            fab.setScaleX(0.0f);
            fab.setScaleY(0.0f);
            fab.setAlpha(0.0f);

            fab.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .alpha(1.0f)
                    .setDuration(ANIMATION_DURATION_SHORT)
                    .setStartDelay(i * 30L)  // Stagger delay
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }

    private void animateFabHover(FloatingActionButton fab, boolean isHovering) {
        float targetScale = isHovering ? 1.2f : 1.0f;
        float targetElevation = isHovering ? 12.0f : 6.0f;

        fab.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(ANIMATION_DURATION_SHORT)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        fab.setElevation(targetElevation);
    }

    private void animateFabClick(FloatingActionButton fab) {
        // Animación de feedback rápida (pulse): escala temporal sin desaparecer
        fab.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(100)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    // Regresa a escala normal
                    fab.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .start();
                })
                .start();
    }

    private void animateMenuExit() {
        // Fade out del fondo
        View bg = binding.getRoot().getChildAt(0);
        bg.animate()
                .alpha(0.0f)
                .setDuration(ANIMATION_DURATION_SHORT)
                .start();

        // Scale out de botones
        FloatingActionButton[] fabs = {
            binding.fabVialidad, binding.fabAlumbrado, binding.fabAgua, binding.fabTrafico,
            binding.fabSeguridad, binding.fabParques, binding.fabBasura, binding.fabOtros
        };

        for (int i = 0; i < fabs.length; i++) {
            fabs[i].animate()
                    .scaleX(0.0f)
                    .scaleY(0.0f)
                    .alpha(0.0f)
                    .setDuration(ANIMATION_DURATION_SHORT)
                    .setStartDelay(i * 20L)
                    .start();
        }
    }

    // ===== TOUCH INTERACTIONS =====

    private void setupTouchInteractions() {
        View bg = binding.getRoot().getChildAt(0);

        bg.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getX();
                    startY = event.getY();
                    isSwipingOut = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getX() - startX;
                    float deltaY = event.getY() - startY;
                    float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                    if (distance > swipeThresholdPx) {
                        isSwipingOut = true;
                        // Animar el fade out progresivo al deslizar
                        float progress = Math.min(distance / (swipeThresholdPx * 3), 1.0f);
                        bg.setAlpha(1.0f - (progress * 0.5f));
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (isSwipingOut) {
                        // Deslizamiento detectado - cerrar
                        animateMenuExit();
                        dismiss();
                    } else {
                        // Solo fue un tap en el fondo - cerrar normalmente
                        dismiss();
                    }
                    return true;
            }
            return false;
        });
    }

    // ===== CALLBACKS =====

    private void onCategorySelected(String category) {
        if (capturedLocation != null && listener != null) {
            listener.onCategorySelected(category, capturedLocation.latitude(), capturedLocation.longitude());
        }
        // Cerrar el menú inmediatamente después de seleccionar
        dismiss();
    }

    @Override
    public void dismiss() {
        animateMenuExit();
        super.dismissAllowingStateLoss();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
