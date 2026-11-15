package com.bombayashi.reporteciudadano.ui;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
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
        setupFabWithAnimations(binding.fabSenalizacion, "senalizacion");
        setupFabWithAnimations(binding.fabSeguridad, "seguridad");
        setupFabWithAnimations(binding.fabBasura, "basura");

    }

    private void setupFabWithAnimations(FloatingActionButton fab, String category) {
        applyVolumeIcon(fab, category);

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

    // ===== ÍCONOS CON VOLUMEN =====

    /**
     * Reemplaza el glyph plano por un bitmap compuesto: sombra suave, relleno con el
     * color de categoría, brillo diagonal tipo vidrio (misma técnica que los pines
     * "propios" del mapa) y el ícono blanco centrado. El fondo/tint del FAB se deja
     * intacto (ripple, elevación, forma) — el bitmap ocupa casi toda la cara del botón.
     */
    private void applyVolumeIcon(FloatingActionButton fab, String category) {
        int drawableId = MarkerRenderer.getCategoryDrawableId(category);
        int colorRes = MarkerRenderer.getCategoryColor(category);
        Bitmap bitmap = buildVolumeIcon(drawableId, colorRes);
        if (bitmap == null) return;

        fab.setImageBitmap(bitmap);
        // ~30% más que los 40dp (design_fab_size_mini) que ya ocupaban todo el espacio
        // del FAB. Ojo: el touch target mínimo de Material es 48dp — si esto empieza a
        // recortarse en los bordes, probablemente estemos pasando ese límite real.
        int sizePx = (int) (52 * getResources().getDisplayMetrics().density);
        fab.setMaxImageSize(sizePx);
    }

    private Bitmap buildVolumeIcon(@DrawableRes int drawableId, @ColorRes int colorRes) {
        Drawable d = ContextCompat.getDrawable(requireContext(), drawableId);
        if (d == null) return null;

        final int SIZE = 140;
        final float INSET = 8f;
        final float CORNER = SIZE * 0.32f;

        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);

        RectF rect = new RectF(INSET, INSET, SIZE - INSET, SIZE - INSET);
        Path shape = new Path();
        shape.addRoundRect(rect, CORNER, CORNER, Path.Direction.CW);

        // Sombra suave debajo, para separar el ícono del fondo del botón.
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(Color.BLACK);
        shadow.setAlpha(55);
        shadow.setMaskFilter(new BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL));
        canvas.save();
        canvas.translate(0, 3f);
        canvas.drawPath(shape, shadow);
        canvas.restore();

        // Relleno base con el color de la categoría.
        int baseColor = ContextCompat.getColor(requireContext(), colorRes);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(baseColor);
        canvas.drawPath(shape, fill);

        canvas.save();
        canvas.clipPath(shape);

        // Brillo diagonal (glass sheen), sutil.
        RadialGradient sheen = new RadialGradient(
                SIZE * 0.32f, SIZE * 0.28f, SIZE * 0.85f,
                new int[]{0x4DFFFFFF, 0x14FFFFFF, 0x00000000},
                new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP);
        Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sheenPaint.setShader(sheen);
        canvas.drawPath(shape, sheenPaint);

        // Sombreado hacia abajo, para dar sensación de profundidad/curvatura.
        LinearGradient baseShade = new LinearGradient(
                0, SIZE * 0.55f, 0, SIZE,
                0x00000000, 0x33000000,
                Shader.TileMode.CLAMP);
        Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadePaint.setShader(baseShade);
        canvas.drawPath(shape, shadePaint);
        canvas.restore();

        // Ícono blanco centrado.
        try { d.setTint(Color.WHITE); } catch (Exception ignored) {}
        int iconSize = (int) (SIZE * 0.5f);
        int off = (SIZE - iconSize) / 2;
        d.setBounds(off, off, off + iconSize, off + iconSize);
        d.draw(canvas);

        return bmp;
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
            binding.fabVialidad, binding.fabAlumbrado, binding.fabAgua, binding.fabSenalizacion,
            binding.fabSeguridad, binding.fabBasura
        };
        TextView[] labels = {
            binding.tvLabelVialidad, binding.tvLabelAlumbrado, binding.tvLabelAgua,
            binding.tvLabelSenalizacion, binding.tvLabelSeguridad, binding.tvLabelBasura
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

            TextView label = labels[i];
            label.setAlpha(0.0f);
            label.animate()
                    .alpha(1.0f)
                    .setDuration(ANIMATION_DURATION_SHORT)
                    .setStartDelay(i * 30L + 40L)
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
            binding.fabVialidad, binding.fabAlumbrado, binding.fabAgua, binding.fabSenalizacion,
            binding.fabSeguridad, binding.fabBasura
        };
        TextView[] labels = {
            binding.tvLabelVialidad, binding.tvLabelAlumbrado, binding.tvLabelAgua,
            binding.tvLabelSenalizacion, binding.tvLabelSeguridad, binding.tvLabelBasura
        };

        for (int i = 0; i < fabs.length; i++) {
            fabs[i].animate()
                    .scaleX(0.0f)
                    .scaleY(0.0f)
                    .alpha(0.0f)
                    .setDuration(ANIMATION_DURATION_SHORT)
                    .setStartDelay(i * 20L)
                    .start();

            labels[i].animate()
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
