package com.bombayashi.reporteciudadano.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import java.util.Random;

/**
 * Capa a pantalla completa que dibuja el "+N" de puntos como texto suelto, con
 * burbujas pequeñas flotando a su alrededor.
 *
 * Coreografía (t relativo a {@code emergeAt}, el pico del rebote del botón):
 * el texto nace detrás del borde superior del botón (recortado), sube en línea recta
 * ~36 dp con ease-out, se queda un momento y se desvanece. Las burbujas aparecen
 * repartidas alrededor del texto, suben con un vaivén y revientan. No intercepta
 * toques y se quita sola del padre al terminar.
 */
final class PointsBurstView extends View {

    private static final long RISE_MS = 600;
    private static final long HOLD_MS = 650;
    private static final long FADE_MS = 280;
    private static final float RISE_DP = 36f;
    private static final int BUBBLE_COUNT = 7;
    private static final long BUBBLE_POP_MS = 140;

    private static final int OUTLINE = Color.parseColor("#312E81"); // índigo profundo
    private static final int[] TINTS = {
            Color.parseColor("#818CF8"),
            Color.parseColor("#A5B4FC"),
            Color.parseColor("#6366F1"),
    };

    private final float dp;
    private final float originX;
    /** Borde superior del botón (o centro de pantalla sin ancla): de aquí emerge el texto. */
    private final float emergeY;
    /** true con ancla: el texto se recorta bajo {@code emergeY} para salir "de detrás". */
    private final boolean clipBelowEdge;
    private final long emergeAt;
    private final String amountText;
    private final String caption;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint captionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final Bitmap[] sprites = new Bitmap[TINTS.length];
    private final Bubble[] bubbles = new Bubble[BUBBLE_COUNT];
    private final float textHalfW, textHalfH;

    /** ease-out marcado: arranca rápido y frena suave al llegar arriba. */
    private final Interpolator easeOut = new PathInterpolator(0.16f, 1f, 0.3f, 1f);
    private final Interpolator easeIn = new PathInterpolator(0.4f, 0f, 1f, 1f);
    private final Interpolator popIn = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);

    private long elapsed;
    private ValueAnimator clock;

    PointsBurstView(Context ctx, float originX, float emergeY, boolean clipBelowEdge,
                    long emergeAt, int points, String caption) {
        super(ctx);
        this.dp = ctx.getResources().getDisplayMetrics().density;
        this.originX = originX;
        this.emergeY = emergeY;
        this.clipBelowEdge = clipBelowEdge;
        this.emergeAt = emergeAt;
        this.amountText = "+" + points;
        this.caption = caption;
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        Typeface heavy = Typeface.create("sans-serif", Typeface.BOLD);
        fillPaint.setTypeface(heavy);
        fillPaint.setTextSize(26 * dp);
        fillPaint.setLetterSpacing(-0.02f);
        fillPaint.setTextAlign(Paint.Align.CENTER);
        fillPaint.setColor(Color.WHITE);
        fillPaint.setShadowLayer(6 * dp, 0, 2 * dp, 0x664F46E5);

        // contorno oscuro fino: legible sobre fondo claro u oscuro
        strokePaint.set(fillPaint);
        strokePaint.clearShadowLayer();
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeWidth(3.5f * dp);
        strokePaint.setColor(OUTLINE);

        captionPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        captionPaint.setTextSize(12 * dp);
        captionPaint.setTextAlign(Paint.Align.CENTER);
        captionPaint.setColor(TINTS[2]);

        ringPaint.setStyle(Paint.Style.STROKE);

        textHalfW = fillPaint.measureText(amountText) / 2;
        textHalfH = -(fillPaint.ascent() + fillPaint.descent()) / 2;
        initSprites();
        spawnBubbles();
    }

    /** Burbujas pre-renderizadas una vez: cuerpo translúcido, borde tenue y reflejo. */
    private void initSprites() {
        int size = (int) (24 * dp);
        float r = size / 2f;
        for (int i = 0; i < TINTS.length; i++) {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new RadialGradient(r * 0.7f, r * 0.6f, r * 1.2f,
                    new int[]{withAlpha(Color.WHITE, 90), withAlpha(TINTS[i], 120), withAlpha(TINTS[i], 220)},
                    new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(r, r, r - dp * 0.5f, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp);
            p.setColor(withAlpha(Color.WHITE, 150));
            c.drawCircle(r, r, r - dp, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(Color.WHITE, 210));
            c.drawOval(new RectF(r * 0.45f, r * 0.35f, r * 0.85f, r * 0.62f), p);
            sprites[i] = bmp;
        }
    }

    /** Reparte las burbujas en un anillo alrededor del texto, con variación. */
    private void spawnBubbles() {
        Random rnd = new Random();
        for (int i = 0; i < BUBBLE_COUNT; i++) {
            Bubble b = new Bubble();
            double angle = (i + rnd.nextFloat() * 0.6f) * (Math.PI * 2 / BUBBLE_COUNT);
            float spread = 1.15f + rnd.nextFloat() * 0.5f;
            b.offX = (float) Math.cos(angle) * (textHalfW + 10 * dp) * spread;
            b.offY = (float) Math.sin(angle) * (textHalfH + 10 * dp) * spread;
            b.driftX = (float) Math.cos(angle) * (6 + rnd.nextFloat() * 8) * dp;
            b.driftY = -(14 + rnd.nextFloat() * 22) * dp;
            b.radius = (2.5f + rnd.nextFloat() * 4f) * dp;
            b.delay = 60 + rnd.nextInt(260);
            b.life = 700 + rnd.nextInt(500);
            b.phase = rnd.nextFloat() * 6.28f;
            b.sprite = rnd.nextInt(sprites.length);
            bubbles[i] = b;
        }
    }

    // --------------------------------------------------------------- lifecycle

    void start() {
        if (!animationsEnabled()) {
            // "Quitar animaciones" activo: el texto aparece quieto en su sitio y se retira
            elapsed = emergeAt + RISE_MS;
            invalidate();
            postDelayed(this::removeSelf, 1500);
            return;
        }
        clock = ValueAnimator.ofFloat(0f, 1f);
        clock.setDuration(emergeAt + RISE_MS + HOLD_MS + FADE_MS);
        clock.setInterpolator(null); // tiempo lineal; cada capa aplica su curva
        clock.addUpdateListener(a -> {
            elapsed = a.getCurrentPlayTime();
            invalidate();
        });
        clock.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                removeSelf();
            }
        });
        clock.start();
    }

    private boolean animationsEnabled() {
        float scale = android.provider.Settings.Global.getFloat(getContext().getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale > 0f;
    }

    private void removeSelf() {
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (clock != null) clock.cancel();
        for (int i = 0; i < sprites.length; i++) {
            if (sprites[i] != null) { sprites[i].recycle(); sprites[i] = null; }
        }
    }

    // ------------------------------------------------------------------- draw

    @Override
    protected void onDraw(Canvas c) {
        long t = elapsed - emergeAt;
        if (t < 0 || sprites[0] == null) return;

        float rise = easeOut.getInterpolation(clamp(t / (float) RISE_MS));
        float fade = easeIn.getInterpolation(clamp((t - RISE_MS - HOLD_MS) / (float) FADE_MS));

        // con ancla nace oculto justo detrás del borde; sin ancla nace en el centro
        float startY = clipBelowEdge ? emergeY + textHalfH * 2 : emergeY + 12 * dp;
        float endY = emergeY - RISE_DP * dp;
        float cy = lerp(startY, endY, rise) - 6 * dp * fade;
        float cx = Math.max(textHalfW + 24 * dp, Math.min(getWidth() - textHalfW - 24 * dp, originX));

        drawBubbles(c, t, cx, cy);
        drawAmount(c, t, cx, cy, rise, fade);
    }

    private void drawAmount(Canvas c, long t, float cx, float cy, float rise, float fade) {
        int alpha = (int) (255 * clamp(t / 140f) * (1f - fade));
        if (alpha <= 0) return;
        float scale = lerp(0.9f, 1f, rise) * lerp(1f, 0.96f, fade);

        c.save();
        if (clipBelowEdge) c.clipRect(0, 0, getWidth(), emergeY);
        c.translate(cx, cy);
        c.scale(scale, scale);
        strokePaint.setAlpha((int) (alpha * 0.9f));
        fillPaint.setAlpha(alpha);
        c.drawText(amountText, 0, textHalfH, strokePaint);
        c.drawText(amountText, 0, textHalfH, fillPaint);
        if (caption != null) {
            captionPaint.setAlpha((int) (alpha * 0.9f));
            c.drawText(caption, 0, textHalfH + 22 * dp, captionPaint);
        }
        c.restore();
    }

    /** Burbujas que siguen al texto, se separan un poco, suben con vaivén y revientan. */
    private void drawBubbles(Canvas c, long t, float cx, float cy) {
        for (Bubble b : bubbles) {
            float local = t - b.delay;
            if (local < 0 || local > b.life + BUBBLE_POP_MS) continue;
            float p = easeOut.getInterpolation(clamp(local / (float) b.life));
            float x = cx + b.offX + b.driftX * p + (float) Math.sin(local / 1000f * 8f + b.phase) * 2 * dp;
            float y = cy + b.offY + b.driftY * p;

            if (local <= b.life) {
                float grow = popIn.getInterpolation(clamp(local / 200f));
                float r = b.radius * grow;
                spritePaint.setAlpha(255);
                dst.set(x - r, y - r, x + r, y + r);
                c.drawBitmap(sprites[b.sprite], null, dst, spritePaint);
            } else {
                float q = clamp((local - b.life) / (float) BUBBLE_POP_MS);
                ringPaint.setStrokeWidth(dp * (1.2f - q));
                ringPaint.setColor(withAlpha(TINTS[b.sprite], (int) (220 * (1 - q))));
                c.drawCircle(x, y, b.radius * (1f + 0.8f * q), ringPaint);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static final class Bubble {
        float offX, offY, driftX, driftY, radius, phase;
        int delay, life, sprite;
    }
}
