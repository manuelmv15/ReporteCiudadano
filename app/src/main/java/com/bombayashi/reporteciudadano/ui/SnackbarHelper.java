package com.bombayashi.reporteciudadano.ui;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.bombayashi.reporteciudadano.R;
import com.google.android.material.snackbar.Snackbar;

public final class SnackbarHelper {

    public enum Variant { SUCCESS, ERROR, INFO, WARNING }

    private SnackbarHelper() {}

    public static void show(View anchor, String message, Variant variant) {
        build(anchor, message, variant, Snackbar.LENGTH_LONG, null, null).show();
    }

    public static void showShort(View anchor, String message, Variant variant) {
        build(anchor, message, variant, Snackbar.LENGTH_SHORT, null, null).show();
    }

    public static void showWithAction(View anchor, String message, Variant variant,
                                      String actionLabel, View.OnClickListener onAction) {
        build(anchor, message, variant, Snackbar.LENGTH_INDEFINITE, actionLabel, onAction).show();
    }

    private static Snackbar build(View anchor, String message, Variant variant, int duration,
                                  String actionLabel, View.OnClickListener onAction) {
        Snackbar snackbar = Snackbar.make(anchor, message, duration);

        int bgColor   = ContextCompat.getColor(anchor.getContext(), bgColorRes(variant));
        int textColor = ContextCompat.getColor(anchor.getContext(), onColorRes(variant));

        View snackView = snackbar.getView();
        snackView.setBackgroundTintList(ColorStateList.valueOf(bgColor));

        TextView tv = snackView.findViewById(com.google.android.material.R.id.snackbar_text);
        tv.setTextColor(textColor);
        tv.setMaxLines(3);
        applyIcon(anchor, tv, iconRes(variant), textColor);

        if (actionLabel != null && onAction != null) {
            snackbar.setAction(actionLabel, onAction);
            snackbar.setActionTextColor(textColor);
        }

        return snackbar;
    }

    private static void applyIcon(View anchor, TextView tv, @DrawableRes int iconRes, int tint) {
        Drawable icon = ContextCompat.getDrawable(anchor.getContext(), iconRes);
        if (icon == null) return;

        Drawable wrapped = DrawableCompat.wrap(icon.mutate());
        DrawableCompat.setTint(wrapped, tint);
        wrapped.setBounds(0, 0, wrapped.getIntrinsicWidth(), wrapped.getIntrinsicHeight());

        tv.setCompoundDrawablesRelative(wrapped, null, null, null);
        tv.setCompoundDrawablePadding(
                anchor.getContext().getResources().getDimensionPixelSize(R.dimen.snackbar_icon_padding)
        );
    }

    private static int bgColorRes(Variant v) {
        switch (v) {
            case SUCCESS: return R.color.snackbar_success_bg;
            case INFO:    return R.color.snackbar_info_bg;
            case WARNING: return R.color.snackbar_warning_bg;
            // Error usa el rol M3 real (se invierte solo en modo oscuro), no un color aparte.
            default:      return R.color.md_theme_error;
        }
    }

    private static int onColorRes(Variant v) {
        switch (v) {
            case SUCCESS: return R.color.snackbar_success_on;
            case INFO:    return R.color.snackbar_info_on;
            case WARNING: return R.color.snackbar_warning_on;
            default:      return R.color.md_theme_onError;
        }
    }

    private static int iconRes(Variant v) {
        switch (v) {
            case SUCCESS: return R.drawable.ic_snackbar_success;
            case INFO:    return R.drawable.ic_snackbar_info;
            case WARNING: return R.drawable.ic_snackbar_warning;
            default:      return R.drawable.ic_snackbar_error;
        }
    }
}
