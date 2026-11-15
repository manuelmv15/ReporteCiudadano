package com.bombayashi.reporteciudadano.ui;

import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * "+N" cuando el servidor otorga puntos. Con {@code anchor} (el botón votado), el botón
 * se hunde y rebota levemente; en el pico del rebote nace el texto "+N" detrás de
 * su borde superior y sube ~36 dp con ease-out. Sin ancla, el texto
 * sale del centro de la pantalla con {@code reason} como subtítulo.
 * El dibujo vive en {@link PointsBurstView}. {@code root} debe ser un FrameLayout
 * (la decorView de una Activity o de un diálogo). Los puntos vienen del servidor.
 */
public final class PointsAnimator {

    private static final long BOUNCE_MS = 460;
    /** Fracción de BOUNCE_MS en la que el botón llega al máximo del rebote. */
    private static final float BOUNCE_PEAK = 0.6f;

    private PointsAnimator() {}

    /** Sin ancla: el texto sale del centro de la pantalla. */
    public static void show(ViewGroup root, int points, String reason) {
        show(root, null, points, reason);
    }

    public static void show(ViewGroup root, View anchor, int points, String reason) {
        if (root == null || points <= 0 || !(root instanceof FrameLayout)) return;
        if (root.getWidth() == 0 || root.getHeight() == 0) {
            root.post(() -> show(root, anchor, points, reason));
            return;
        }

        boolean anchored = anchor != null && anchor.isAttachedToWindow() && anchor.getWidth() > 0;
        float ox = root.getWidth() / 2f;
        float oy = root.getHeight() / 2f;
        long emergeAt = 0;
        if (anchored) {
            int[] a = new int[2];
            int[] r = new int[2];
            anchor.getLocationInWindow(a);
            root.getLocationInWindow(r);
            ox = a[0] - r[0] + anchor.getWidth() / 2f;
            oy = a[1] - r[1]; // borde superior del botón
            emergeAt = (long) (BOUNCE_MS * BOUNCE_PEAK);
            bounce(anchor);
        }

        String label = reason != null && !reason.isEmpty() ? reason : null;
        PointsBurstView burst = new PointsBurstView(root.getContext(), ox, oy, anchored,
                emergeAt, points, anchored ? null : label);
        root.addView(burst, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.announceForAccessibility("Ganaste " + points + " puntos" + (label != null ? ". " + label : ""));
        (anchored ? anchor : root).performHapticFeedback(HapticFeedbackConstants.CONFIRM);
        burst.start();
    }

    /** Se hunde, rebota por encima de su tamaño y se asienta. */
    private static void bounce(View v) {
        Keyframe[] kf = {
                Keyframe.ofFloat(0f, 1f),
                Keyframe.ofFloat(0.25f, 0.96f),
                Keyframe.ofFloat(BOUNCE_PEAK, 1.03f),
                Keyframe.ofFloat(0.82f, 0.995f),
                Keyframe.ofFloat(1f, 1f),
        };
        ObjectAnimator anim = ObjectAnimator.ofPropertyValuesHolder(v,
                PropertyValuesHolder.ofKeyframe(View.SCALE_X, kf),
                PropertyValuesHolder.ofKeyframe(View.SCALE_Y, kf));
        anim.setDuration(BOUNCE_MS);
        anim.start();
    }
}
