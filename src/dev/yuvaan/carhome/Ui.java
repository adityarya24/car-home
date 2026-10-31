package dev.yuvaan.carhome;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

// Shared look: glass panels over a soft colour wallpaper.
final class Ui {
    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable glass(Context c, float radius, int alpha) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(alpha, 255, 255, 255));
        g.setCornerRadius(dp(c, radius));
        g.setStroke(Math.max(1, dp(c, 1)), Color.argb(70, 255, 255, 255));
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        return t;
    }

    // An app button: its real icon on a glass tile, label underneath.
    static View appTile(Context c, String label, Drawable icon, View.OnClickListener onClick) {
        return appTile(c, label, icon, onClick, 34, 10);
    }

    static View appTile(Context c, String label, Drawable icon, View.OnClickListener onClick, int iconDp, int textSp) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(c, 4), dp(c, 6), dp(c, 4), dp(c, 4));
        box.setBackground(glass(c, 16, 34));
        ImageView iv = new ImageView(c);
        if (icon != null) iv.setImageDrawable(icon);
        box.addView(iv, new LinearLayout.LayoutParams(dp(c, iconDp), dp(c, iconDp)));
        TextView t = text(c, label, textSp, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(c, 3);
        box.addView(t, lp);
        box.setOnClickListener(onClick);
        box.setClickable(true);
        return box;
    }

    // An emoji on a warm rounded square, for tiles that aren't an app.
    static Drawable emojiIcon(Context c, String emoji) {
        int size = dp(c, 68);
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new android.graphics.LinearGradient(0, 0, size, size,
                Color.rgb(255, 196, 84), Color.rgb(255, 110, 140), Shader.TileMode.CLAMP));
        cv.drawRoundRect(0, 0, size, size, size * 0.24f, size * 0.24f, bg);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size * 0.6f);
        p.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = p.getFontMetrics();
        cv.drawText(emoji, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2, p);
        return new android.graphics.drawable.BitmapDrawable(c.getResources(), b);
    }

    static Drawable icon(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    static boolean installed(Context c, String pkg) {
        return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    static void open(Context c, String pkg) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(c, pkg + " not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(c, "Can't open " + pkg, Toast.LENGTH_SHORT).show();
        }
    }

    // Colourful blurred blobs; the glass panels sit on top.
    static final class Wallpaper extends View {
        // Each look is a base colour and three soft glows: {x, y, radius} as
        // parts of the width and height, then the colour.
        enum Look {
            OLD(Color.rgb(20, 24, 36), new float[][]{{0.15f, 0.2f, 0.45f}, {0.45f, 0.95f, 0.5f}, {0.9f, 0.1f, 0.45f}},
                    Color.rgb(255, 122, 89), Color.rgb(123, 92, 255), Color.rgb(0, 194, 255)),
            AURORA(Color.rgb(9, 13, 26), new float[][]{{0.85f, 0.0f, 0.55f}, {0.15f, 0.05f, 0.45f}, {0.45f, 1.05f, 0.6f}},
                    Color.argb(170, 0, 190, 170), Color.argb(150, 70, 90, 220), Color.argb(150, 120, 60, 200)),
            MORNING(Color.rgb(30, 40, 66), new float[][]{{0.1f, 0.1f, 0.55f}, {0.9f, 0.1f, 0.5f}, {0.5f, 1.05f, 0.55f}},
                    Color.argb(200, 255, 170, 120), Color.argb(170, 120, 190, 255), Color.argb(130, 170, 150, 255)),
            DAY(Color.rgb(12, 36, 66), new float[][]{{0.9f, 0.0f, 0.55f}, {0.1f, 0.9f, 0.55f}, {0.6f, 1.0f, 0.4f}},
                    Color.argb(190, 0, 200, 255), Color.argb(170, 60, 120, 255), Color.argb(120, 0, 210, 180)),
            EVENING(Color.rgb(28, 16, 38), new float[][]{{0.2f, 1.0f, 0.6f}, {0.9f, 0.05f, 0.5f}, {0.1f, 0.0f, 0.4f}},
                    Color.argb(210, 255, 120, 60), Color.argb(170, 220, 60, 130), Color.argb(140, 110, 60, 200));

            final int base;
            final float[][] at;
            final int[] colors;

            Look(int base, float[][] at, int... colors) {
                this.base = base;
                this.at = at;
                this.colors = colors;
            }
        }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean night;
        // Day is clear blue; night is the aurora, dimmed. Others stay for previews.
        private Look look = Look.DAY;

        Wallpaper(Context c) {
            super(c);
        }

        // Night keeps the colours, fainter, but bright enough that the glass
        // cards still stand out.
        void setNight(boolean on) {
            if (night == on) return;
            night = on;
            invalidate();
        }

        void setLook(Look l) {
            if (look == l) return;
            look = l;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            Look l = night ? Look.AURORA : look;
            canvas.drawColor(night ? Color.rgb(8, 10, 16) : l.base);
            for (int i = 0; i < l.at.length; i++) {
                float[] a = l.at[i];
                blob(canvas, w * a[0], h * a[1], w * a[2], l.colors[i]);
            }
        }

        private void blob(Canvas canvas, float x, float y, float r, int color) {
            if (night) color = Color.argb(Color.alpha(color) * 140 / 255, Color.red(color), Color.green(color), Color.blue(color));
            paint.setShader(new RadialGradient(x, y, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
        }
    }
}
