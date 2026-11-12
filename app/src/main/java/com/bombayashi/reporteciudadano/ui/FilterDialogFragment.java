package com.bombayashi.reporteciudadano.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;

import com.bombayashi.reporteciudadano.R;
import com.bombayashi.reporteciudadano.databinding.DialogMapFiltersBinding;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * RF-19: diálogo de filtros del mapa. Comparte la estética del menú radial (scrim oscuro,
 * círculos de color por categoría, animación escalonada) y de la barra flotante (panel glass).
 */
public class FilterDialogFragment extends DialogFragment {

    public interface OnFiltersAppliedListener {
        /** categories vacío = sin filtro de categoría. status/age usan "all" como "sin filtro". */
        void onFiltersApplied(Set<String> categories, String status, String age);
    }

    private static final int ANIMATION_DURATION_SHORT = 200;
    private static final int ANIMATION_DURATION_MEDIUM = 300;
    private static final float UNSELECTED_ALPHA = 0.35f;

    private static final class CategoryOption {
        final String slug; final String label; @DrawableRes final int icon; @ColorRes final int color;
        CategoryOption(String slug, String label, int icon, int color) {
            this.slug = slug; this.label = label; this.icon = icon; this.color = color;
        }
    }

    private static final CategoryOption[] CATEGORIES = {
            new CategoryOption("vialidad", "Bache", R.drawable.ic_category_vialidad, R.color.category_vialidad),
            new CategoryOption("alumbrado", "Alumbrado", R.drawable.ic_category_alumbrado, R.color.category_alumbrado),
            new CategoryOption("agua", "Agua", R.drawable.ic_category_agua, R.color.category_agua),
            new CategoryOption("senalizacion", "Señales", R.drawable.ic_category_senalizacion, R.color.category_senalizacion),
            new CategoryOption("seguridad", "Seguridad", R.drawable.ic_category_seguridad, R.color.category_seguridad),
            new CategoryOption("basura", "Basura", R.drawable.ic_category_basura, R.color.category_basura)
    };

    private DialogMapFiltersBinding binding;
    private OnFiltersAppliedListener listener;

    private final Set<String> initialCategories = new HashSet<>();
    private String initialStatus = "all";
    private String initialAge = "all";

    private final Map<String, FloatingActionButton> categoryButtons = new LinkedHashMap<>();
    private final Set<String> selectedCategories = new HashSet<>();
    private boolean dismissing = false;

    public static FilterDialogFragment newInstance(Set<String> categories, String status, String age,
                                                   OnFiltersAppliedListener listener) {
        FilterDialogFragment f = new FilterDialogFragment();
        f.initialCategories.addAll(categories);
        f.initialStatus = status;
        f.initialAge = age;
        f.listener = listener;
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, android.R.style.Theme_Translucent_NoTitleBar);
        // Tras recrear la actividad se pierde el listener: cerrar en vez de mostrar un diálogo inútil
        if (savedInstanceState != null && listener == null) dismissAllowingStateLoss();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = DialogMapFiltersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        buildCategoryGrid();
        bindState(initialCategories, initialStatus, initialAge);

        binding.filterScrim.setOnClickListener(v -> dismiss());
        binding.btnFilterCancel.setOnClickListener(v -> dismiss());
        binding.btnFilterClear.setOnClickListener(v -> bindState(new HashSet<>(), "all", "all"));
        binding.btnFilterApply.setOnClickListener(v -> applyAndDismiss());

        animateEntrance();
    }

    // ===== CATEGORÍAS =====

    private void buildCategoryGrid() {
        GridLayout grid = binding.gridFilterCategories;
        int pad = dp(6);
        for (CategoryOption opt : CATEGORIES) {
            LinearLayout cell = new LinearLayout(requireContext());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, pad, 0, pad);

            FloatingActionButton fab = new FloatingActionButton(requireContext());
            fab.setSize(FloatingActionButton.SIZE_MINI);
            fab.setImageResource(opt.icon);
            fab.setImageTintList(ColorStateList.valueOf(0xFFFFFFFF));
            fab.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), opt.color)));
            fab.setCompatElevation(dp(3));
            fab.setContentDescription(opt.label);
            fab.setOnClickListener(v -> toggleCategory(opt.slug));
            cell.addView(fab, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView label = new TextView(requireContext());
            label.setText(opt.label);
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium);
            label.setTextColor(resolveColor(com.google.android.material.R.attr.colorOnSurface));
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(1);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(2);
            cell.addView(label, lp);
            cell.setOnClickListener(v -> toggleCategory(opt.slug));

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            glp.width = 0;
            grid.addView(cell, glp);
            categoryButtons.put(opt.slug, fab);
        }
    }

    private void toggleCategory(String slug) {
        if (!selectedCategories.remove(slug)) selectedCategories.add(slug);
        FloatingActionButton fab = categoryButtons.get(slug);
        if (fab != null) {
            pulse(fab);
            renderCategory(slug, true);
        }
    }

    private void renderCategory(String slug, boolean animate) {
        FloatingActionButton fab = categoryButtons.get(slug);
        if (fab == null) return;
        View cell = (View) fab.getParent();
        float alpha = selectedCategories.contains(slug) ? 1f : UNSELECTED_ALPHA;
        if (animate) cell.animate().alpha(alpha).setDuration(ANIMATION_DURATION_SHORT).start();
        else cell.setAlpha(alpha);
    }

    // ===== ESTADO =====

    /** Vacío = sin filtro = todas las categorías seleccionadas. */
    private void bindState(Set<String> categories, String status, String age) {
        selectedCategories.clear();
        if (categories.isEmpty()) selectedCategories.addAll(categoryButtons.keySet());
        else selectedCategories.addAll(categories);
        for (String slug : categoryButtons.keySet()) renderCategory(slug, binding.getRoot().isLaidOut());

        binding.cgFilterStatus.check(switch (status) {
            case "pending" -> R.id.chip_status_pending;
            case "verified" -> R.id.chip_status_verified;
            case "resolved" -> R.id.chip_status_resolved;
            default -> R.id.chip_status_all;
        });
        binding.cgFilterAge.check(switch (age) {
            case "1h" -> R.id.chip_age_1h;
            case "6h" -> R.id.chip_age_6h;
            case "24h" -> R.id.chip_age_24h;
            default -> R.id.chip_age_all;
        });
    }

    private void applyAndDismiss() {
        // Todas o ninguna marcadas equivale a "sin filtro"
        Set<String> categories = new HashSet<>();
        if (!selectedCategories.isEmpty() && selectedCategories.size() < categoryButtons.size()) {
            categories.addAll(selectedCategories);
        }

        int s = binding.cgFilterStatus.getCheckedChipId();
        String status = s == R.id.chip_status_pending ? "pending"
                : s == R.id.chip_status_verified ? "verified"
                : s == R.id.chip_status_resolved ? "resolved" : "all";

        int a = binding.cgFilterAge.getCheckedChipId();
        String age = a == R.id.chip_age_1h ? "1h"
                : a == R.id.chip_age_6h ? "6h"
                : a == R.id.chip_age_24h ? "24h" : "all";

        if (listener != null) listener.onFiltersApplied(categories, status, age);
        dismiss();
    }

    // ===== ANIMACIONES (mismo lenguaje que RadialMenuDialogFragment) =====

    private void animateEntrance() {
        binding.filterScrim.setAlpha(0f);
        binding.filterScrim.animate().alpha(1f).setDuration(ANIMATION_DURATION_MEDIUM)
                .setInterpolator(new DecelerateInterpolator()).start();

        View panel = binding.filterPanel;
        panel.setAlpha(0f);
        panel.setTranslationY(dp(48));
        panel.setScaleX(0.94f);
        panel.setScaleY(0.94f);
        panel.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(ANIMATION_DURATION_MEDIUM)
                .setInterpolator(new DecelerateInterpolator()).start();

        int i = 0;
        for (FloatingActionButton fab : categoryButtons.values()) {
            fab.setScaleX(0f);
            fab.setScaleY(0f);
            fab.animate().scaleX(1f).scaleY(1f)
                    .setDuration(ANIMATION_DURATION_SHORT)
                    .setStartDelay(120 + i++ * 30L)
                    .setInterpolator(new OvershootInterpolator(1.5f)).start();
        }
    }

    private void pulse(View v) {
        v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(100)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> v.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    @Override
    public void dismiss() {
        if (dismissing || binding == null) {
            super.dismissAllowingStateLoss();
            return;
        }
        dismissing = true;
        binding.filterScrim.animate().alpha(0f).setDuration(ANIMATION_DURATION_SHORT).start();
        binding.filterPanel.animate().alpha(0f).translationY(dp(48))
                .setDuration(ANIMATION_DURATION_SHORT)
                .withEndAction(super::dismissAllowingStateLoss)
                .start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ===== UTIL =====

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int resolveColor(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
}
