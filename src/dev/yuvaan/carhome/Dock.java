package dev.yuvaan.carhome;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;

// The app bar down the right edge: the clock, then the same apps in the same
// places every time, so a hand finds them without looking. The box's own bar
// there shuffles recent apps. Home has it in its layout; CarTap draws it over
// other apps, on top of the box's bar.
final class Dock extends LinearLayout {
    static final int WIDTH_DP = 72;


    private final TextClock time;
    private final ImageView last;
    private final Drawable dots = new Dots(), house = new House();
    private boolean overApps;

    // onMaps: Maps lives in the home screen's panel, so each caller decides
    // what "open Maps" means. On home the last button opens all apps; over
    // apps it is Home, and the dock shows the clock, since home's is hidden.
    Dock(Context c, Runnable onMaps, boolean overApps) {
        super(c);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.rgb(24, 28, 42));
        int p = Ui.dp(c, 5);
        setPadding(p, p, p, p);

        time = new TextClock(c);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("h:mm");
        time.setTextSize(17);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        time.setOnClickListener(v -> home(c));
        addView(time, new LayoutParams(-1, Ui.dp(c, 30)));

        app(c, Ui.icon(c, HomeActivity.MAPS), v -> onMaps.run());
        app(c, Ui.icon(c, HomeActivity.YT_MUSIC), v -> Ui.open(c, HomeActivity.YT_MUSIC));
        app(c, Ui.icon(c, HomeActivity.YOUTUBE), v -> Ui.open(c, HomeActivity.YOUTUBE));
        app(c, Ui.icon(c, HomeActivity.DIALER), v -> Ui.open(c, HomeActivity.DIALER));
        last = app(c, dots, v -> {
            if (this.overApps) home(c);
            else c.startActivity(new Intent(c, AppsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        });
        setOverApps(overApps);
    }

    // CarTap's dock stays on screen all the time and only changes mode, so
    // the box's own bar never shows through while home comes and goes.
    void setOverApps(boolean overApps) {
        this.overApps = overApps;
        time.setVisibility(overApps ? VISIBLE : GONE);
        last.setImageDrawable(overApps ? house : dots);
    }

    boolean overApps() {
        return overApps;
    }

    static void home(Context c) {
        c.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private ImageView app(Context c, Drawable icon, View.OnClickListener l) {
        FrameLayout slot = new FrameLayout(c);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_pressed}, Ui.glass(c, 14, 90));
        bg.addState(new int[0], Ui.glass(c, 14, 0));
        slot.setBackground(bg);
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(icon);
        if (icon == dots) iv.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        int size = Ui.dp(c, 48);
        slot.addView(iv, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
        slot.setOnClickListener(l);
        LayoutParams lp = new LayoutParams(-1, 0, 1);
        lp.topMargin = Ui.dp(c, 3);
        addView(slot, lp);
        return iv;
    }

    // A plain house, in white like the dots.
    private static final class House extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        House() {
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float w = b.width(), h = b.height(), x = b.left, y = b.top;
            android.graphics.Path roof = new android.graphics.Path();
            roof.moveTo(x + w * 0.5f, y + h * 0.12f);
            roof.lineTo(x + w * 0.92f, y + h * 0.5f);
            roof.lineTo(x + w * 0.08f, y + h * 0.5f);
            roof.close();
            canvas.drawPath(roof, paint);
            canvas.drawRect(x + w * 0.22f, y + h * 0.48f, x + w * 0.78f, y + h * 0.88f, paint);
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    // Nine rounded squares, like the box's own "all apps" button.
    private static final class Dots extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dots() {
            paint.setColor(Color.WHITE);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            float cell = b.width() / 3f, gap = cell * 0.18f, r = cell * 0.22f;
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    float x = b.left + i * cell, y = b.top + j * cell;
                    canvas.drawRoundRect(x + gap, y + gap, x + cell - gap, y + cell - gap, r, r, paint);
                }
            }
        }

        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
