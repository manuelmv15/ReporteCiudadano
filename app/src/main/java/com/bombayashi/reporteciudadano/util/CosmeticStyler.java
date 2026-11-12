package com.bombayashi.reporteciudadano.util;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.bombayashi.reporteciudadano.model.Cosmetics;

import java.util.List;

/**
 * Pinta los cosméticos de la tienda a partir de los valores que manda la API
 * (ver CosmeticSeeder en el backend). Así un artículo nuevo se ve sin actualizar la app.
 */
public final class CosmeticStyler {

    private CosmeticStyler() {}

    /**
     * Marco de avatar: `ring` es un contenedor con padding alrededor de la foto; su fondo
     * (óvalo, color sólido o degradado) asoma por ese padding. Sin marco queda transparente.
     */
    public static void applyFrame(View ring, Cosmetics.Value frame) {
        if (ring == null) return;
        int[] colors = parseColors(frame != null ? frame.getColors() : null);
        if (colors == null) {
            ring.setBackground(null);
            return;
        }
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        if (colors.length == 1) {
            d.setColor(colors[0]);
        } else {
            d.setOrientation(GradientDrawable.Orientation.TL_BR);
            d.setColors(colors);
        }
        ring.setBackground(d);
    }

    /** Título: píldora tonal con el color del artículo; oculta si no hay título. */
    public static void applyTitle(TextView tv, Cosmetics.Value title) {
        if (tv == null) return;
        if (title == null || title.getText() == null) {
            tv.setVisibility(View.GONE);
            return;
        }
        int color = parseColor(title.getColor(), 0xFF246A4B);
        // Los colores del catálogo son para fondo claro: en modo oscuro se aclaran para que se lean
        if (isNight(tv)) color = ColorUtils.blendARGB(color, Color.WHITE, 0.45f);
        tv.setText(title.getText());
        tv.setTextColor(color);
        tv.setBackgroundTintList(ColorStateList.valueOf((color & 0x00FFFFFF) | 0x26000000));
        tv.setVisibility(View.VISIBLE);
    }

    /**
     * Color de la barra flotante. Reconstruye el fondo glass (mismas esquinas y borde que
     * bg_floating_toolbar) con el color final, en vez de teñir el drawable existente:
     * FloatingToolbarLayout reaplica su tinte al montarse y borraba el teñido.
     * Sin tema vuelve al color glass del modo actual.
     */
    public static void applyTheme(View bar, Cosmetics.Value theme) {
        if (bar == null) return;
        android.content.Context ctx = bar.getContext();
        int glass = androidx.core.content.ContextCompat.getColor(ctx, com.bombayashi.reporteciudadano.R.color.glass_bar);
        int color = glass;
        if (theme != null && theme.getColor() != null) {
            int themeColor = parseColor(theme.getColor(), Color.WHITE);
            color = isNight(bar)
                    // Los temas son pasteles pensados para íconos oscuros; en modo oscuro los íconos
                    // son claros, así que se mezcla con el cristal oscuro y queda como tinte suave.
                    ? ColorUtils.blendARGB(glass | 0xFF000000, themeColor, 0.28f)
                    : themeColor;
            color = (color & 0x00FFFFFF) | (glass & 0xFF000000); // misma transparencia que el glass
        }

        float density = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(100 * density);
        d.setStroke(Math.max(1, Math.round(density)),
                androidx.core.content.ContextCompat.getColor(ctx, com.bombayashi.reporteciudadano.R.color.glass_stroke));
        androidx.core.view.ViewCompat.setBackgroundTintList(bar, null);
        bar.setBackground(d);
    }

    private static boolean isNight(View v) {
        int mode = v.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    /** Nivel de la API ("guardian") → etiqueta para mostrar ("Guardián"). */
    public static String levelLabel(String level) {
        if (level == null || level.isEmpty()) return "Nuevo";
        switch (level) {
            case "nuevo": return "Nuevo";
            case "colaborador": return "Colaborador";
            case "guardian": return "Guardián";
            case "experto": return "Experto";
            default: return Character.toUpperCase(level.charAt(0)) + level.substring(1);
        }
    }

    private static int[] parseColors(List<String> hexes) {
        if (hexes == null || hexes.isEmpty()) return null;
        int[] out = new int[hexes.size()];
        for (int i = 0; i < hexes.size(); i++) {
            out[i] = parseColor(hexes.get(i), Color.GRAY);
        }
        return out;
    }

    private static int parseColor(String hex, int fallback) {
        if (hex == null) return fallback;
        try {
            return Color.parseColor(hex);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
