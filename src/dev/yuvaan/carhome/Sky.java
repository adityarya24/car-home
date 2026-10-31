package dev.yuvaan.carhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

// Live weather behind the clock: sun, clouds, rain, fog, snow, storm, and
// stars at night. Slow and soft so it never pulls the eye off the road.
final class Sky extends View {
    enum Kind { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }

    private static final int FRAME_MS = 50;
    private static final int DROPS = 60, STARS = 40, CLOUDS = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloud = new Path();
    private final Random random = new Random(7);
    private final float[][] drops = new float[DROPS][3];  // x, y, speed (0..1 of height)
    private final float[][] stars = new float[STARS][3];  // x, y, phase
    private final float[][] clouds = new float[CLOUDS][3]; // x, y, size
    private Kind kind = Kind.CLEAR;
    private boolean night;
    private long flashUntil, nextFlash;

    Sky(Context c) {
        super(c);
        for (float[] d : drops) { d[0] = random.nextFloat(); d[1] = random.nextFloat(); d[2] = 0.5f + random.nextFloat() * 0.5f; }
        for (float[] s : stars) { s[0] = random.nextFloat(); s[1] = random.nextFloat() * 0.8f; s[2] = random.nextFloat() * 6.28f; }
        for (int i = 0; i < CLOUDS; i++) {
            clouds[i][0] = random.nextFloat();
            clouds[i][1] = 0.15f + random.nextFloat() * 0.5f;
            clouds[i][2] = 0.18f + random.nextFloat() * 0.12f;
        }
    }

    void set(Kind k, boolean isNight) {
        if (k == kind && isNight == night) return;
        kind = k;
        night = isNight;
        invalidate();
    }

    // open-meteo WMO weather codes.
    static Kind of(int code) {
        if (code <= 1) return Kind.CLEAR;
        if (code <= 3) return Kind.CLOUDY;
        if (code <= 48) return Kind.FOG;
        if (code <= 67 || (code >= 80 && code <= 82)) return Kind.RAIN;
        if (code <= 86) return Kind.SNOW;
        return Kind.STORM;
    }

    // Takes the card's size; never asks for height of its own.
    @Override
    protected void onMeasure(int w, int h) {
        int height = MeasureSpec.getMode(h) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(h) : 0;
        setMeasuredDimension(MeasureSpec.getSize(w), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        long now = SystemClock.uptimeMillis();
        float t = now / 1000f;

        sky(canvas, w, h);
        if (night && (kind == Kind.CLEAR || kind == Kind.CLOUDY)) stars(canvas, w, h, t);
        if (kind == Kind.CLEAR || kind == Kind.CLOUDY) {
            if (night) moon(canvas, w, h); else sun(canvas, w, h, t);
        }
        if (kind != Kind.CLEAR) clouds(canvas, w, h, t);
        if (kind == Kind.FOG) fog(canvas, w, h, t);
        if (kind == Kind.RAIN || kind == Kind.STORM) rain(canvas, w, h);
        if (kind == Kind.SNOW) snow(canvas, w, h, t);
        if (kind == Kind.STORM) lightning(canvas, w, h, now);

        postInvalidateDelayed(FRAME_MS);
    }

    private void sky(Canvas c, int w, int h) {
        int top, bottom;
        if (night) {
            top = Color.rgb(14, 20, 48);
            bottom = Color.rgb(40, 34, 78);
        } else switch (kind) {
            case CLEAR: top = Color.rgb(255, 150, 90); bottom = Color.rgb(255, 196, 120); break;
            case RAIN: case STORM: top = Color.rgb(52, 62, 86); bottom = Color.rgb(84, 96, 122); break;
            case SNOW: top = Color.rgb(150, 170, 200); bottom = Color.rgb(200, 212, 230); break;
            default: top = Color.rgb(108, 136, 178); bottom = Color.rgb(160, 180, 206); break;
        }
        paint.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        paint.setAlpha(150);
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private void sun(Canvas c, int w, int h, float t) {
        float x = w * 0.82f, y = h * 0.3f, r = h * 0.28f;
        float pulse = 1f + 0.06f * (float) Math.sin(t * 0.8f);
        paint.setShader(new RadialGradient(x, y, r * 2.6f * pulse, Color.argb(150, 255, 230, 150), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 2.6f * pulse, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(235, 255, 236, 170));
        c.drawCircle(x, y, r, paint);
    }

    private void moon(Canvas c, int w, int h) {
        float x = w * 0.84f, y = h * 0.3f, r = h * 0.2f;
        paint.setShader(new RadialGradient(x, y, r * 2.5f, Color.argb(70, 220, 225, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 2.5f, paint);
        paint.setShader(null);
        paint.setColor(Color.argb(230, 240, 240, 255));
        c.drawCircle(x, y, r, paint);
        paint.setColor(Color.rgb(24, 28, 58));
        c.drawCircle(x + r * 0.45f, y - r * 0.25f, r * 0.85f, paint);
    }

    private void stars(Canvas c, int w, int h, float t) {
        for (float[] s : stars) {
            int a = (int) (90 + 120 * (0.5f + 0.5f * Math.sin(t * 1.3f + s[2])));
            paint.setColor(Color.argb(a, 255, 255, 255));
            c.drawCircle(s[0] * w, s[1] * h, Math.max(1f, h * 0.008f), paint);
        }
    }

    private void clouds(Canvas c, int w, int h, float t) {
        boolean dark = kind == Kind.RAIN || kind == Kind.STORM;
        int color = night || dark ? Color.argb(70, 170, 180, 200) : Color.argb(80, 255, 255, 255);
        paint.setColor(color);
        for (float[] cl : clouds) {
            float span = w * 1.4f;
            float x = ((cl[0] * span + t * w * 0.012f) % span) - w * 0.2f;
            float y = cl[1] * h, r = cl[2] * h;
            // One shape, filled once, so overlapping puffs don't stack up.
            cloud.reset();
            cloud.addCircle(x, y, r, Path.Direction.CW);
            cloud.addCircle(x + r * 0.95f, y + r * 0.35f, r * 0.7f, Path.Direction.CW);
            cloud.addCircle(x - r * 0.95f, y + r * 0.4f, r * 0.6f, Path.Direction.CW);
            cloud.addRoundRect(x - r * 1.5f, y + r * 0.2f, x + r * 1.6f, y + r, r * 0.4f, r * 0.4f, Path.Direction.CW);
            cloud.setFillType(Path.FillType.WINDING);
            c.drawPath(cloud, paint);
        }
    }

    private void fog(Canvas c, int w, int h, float t) {
        for (int i = 0; i < 3; i++) {
            float y = h * (0.35f + i * 0.22f);
            float shift = (float) Math.sin(t * 0.3f + i) * w * 0.08f;
            paint.setColor(Color.argb(60, 230, 235, 245));
            c.drawRoundRect(-w * 0.1f + shift, y - h * 0.06f, w * 1.1f + shift, y + h * 0.06f, h * 0.06f, h * 0.06f, paint);
        }
    }

    private void rain(Canvas c, int w, int h) {
        paint.setColor(Color.argb(140, 200, 220, 255));
        paint.setStrokeWidth(Math.max(1f, h * 0.008f));
        float len = h * 0.09f;
        for (float[] d : drops) {
            d[1] += d[2] * 0.045f;
            if (d[1] > 1.1f) { d[1] = -0.1f; d[0] = random.nextFloat(); }
            float x = d[0] * w, y = d[1] * h;
            c.drawLine(x, y, x - len * 0.25f, y + len, paint);
        }
    }

    private void snow(Canvas c, int w, int h, float t) {
        paint.setColor(Color.argb(200, 255, 255, 255));
        for (float[] d : drops) {
            d[1] += d[2] * 0.008f;
            if (d[1] > 1.05f) { d[1] = -0.05f; d[0] = random.nextFloat(); }
            float x = d[0] * w + (float) Math.sin(t + d[2] * 10) * h * 0.04f;
            c.drawCircle(x, d[1] * h, h * 0.014f * d[2], paint);
        }
    }

    // A soft flash every 6-14 seconds.
    private void lightning(Canvas c, int w, int h, long now) {
        if (now > nextFlash) {
            flashUntil = now + 160;
            nextFlash = now + 6000 + random.nextInt(8000);
        }
        if (now < flashUntil) {
            paint.setColor(Color.argb(90, 255, 255, 255));
            c.drawRect(0, 0, w, h, paint);
        }
    }

    @Override
    protected void onVisibilityChanged(View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE) invalidate();
    }
}
