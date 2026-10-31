package dev.yuvaan.carhome;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

// Big-key QWERTY for the car: shown by the launcher over Maps, and by CarIme
// for apps on the main screen. A row of chips on top searches in one tap.
final class Keys extends LinearLayout {
    interface Listener {
        void text(String s);
        void delete();
        void clear();
        void enter();
        void search(String s);
        void hide();
    }

    private static final String[] ROWS = {"1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"};
    // Home and Work already sit under Maps' own search box.
    static final String[] PLACES = {"Petrol pump", "Parking", "Restaurant", "ATM", "Hospital"};
    private static final long REPEAT_AFTER = 400, REPEAT_EVERY = 60, CLEAR_AFTER = 1500;

    private final Listener l;
    private final LinearLayout chips;
    private final HorizontalScrollView scroll;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> letters = new ArrayList<>();
    private TextView shift;
    // One capital, then back to small letters (passwords need the odd one).
    private boolean caps;

    Keys(Context c, Listener l) {
        super(c);
        this.l = l;
        audio = c.getSystemService(AudioManager.class);
        setOrientation(VERTICAL);
        int p = Ui.dp(c, 4);
        setPadding(p, p, p, p);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(20, 24, 36));
        bg.setCornerRadius(Ui.dp(c, 18));
        setBackground(bg);

        scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        chips = new LinearLayout(c);
        scroll.addView(chips, new LayoutParams(-1, -1));
        addView(scroll, new LayoutParams(-1, Ui.dp(c, 38)));
        setChips(true, null);

        for (String r : ROWS) {
            LinearLayout row = row();
            if (r.startsWith("z")) shift = key(row, "⇧", 1.6f, () -> setCaps(!caps));
            for (char ch : r.toCharArray()) {
                String s = String.valueOf(ch);
                TextView k = key(row, s, 1, () -> {
                    l.text(caps ? s.toUpperCase(java.util.Locale.ROOT) : s);
                    if (caps) setCaps(false);
                });
                if (Character.isLetter(ch)) letters.add(k);
            }
            if (r.startsWith("z")) backspace(row);
        }
        LinearLayout last = row();
        key(last, "⌄", 1.6f, l::hide);
        key(last, "space", 6, () -> l.text(" "));
        key(last, "Go", 2, l::enter);
    }

    private void setCaps(boolean on) {
        caps = on;
        for (TextView k : letters) {
            String t = k.getText().toString();
            k.setText(on ? t.toUpperCase(java.util.Locale.ROOT) : t.toLowerCase(java.util.Locale.ROOT));
        }
        shift.setTextColor(on ? Color.rgb(255, 200, 90) : Color.WHITE);
    }

    // Fixed places across the full width. The chip row is for Maps; other
    // apps get plain keys. Maps already lists recent searches itself.
    void setChips(boolean show, List<String> recents) {
        scroll.setVisibility(show ? VISIBLE : GONE);
        chips.removeAllViews();
        for (String s : PLACES) chip(s);
    }

    private void chip(String label) {
        TextView t = Ui.text(getContext(), label, 14, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        int h = Ui.dp(getContext(), 12);
        t.setPadding(h, 0, h, 0);
        t.setBackground(pressable(16, 30));
        String query = label.startsWith("🕘 ") ? label.substring(3) : label;
        t.setOnClickListener(v -> { click(); l.search(query); });
        LayoutParams lp = new LayoutParams(0, -1, 1);
        int m = Ui.dp(getContext(), 3);
        lp.setMargins(m, m, m, m);
        chips.addView(t, lp);
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER);
        addView(r, new LayoutParams(-1, Ui.dp(getContext(), 42)));
        return r;
    }

    private TextView key(LinearLayout row, String label, float weight, Runnable press) {
        TextView k = Ui.text(getContext(), label, 18, Color.WHITE, false);
        k.setGravity(Gravity.CENTER);
        k.setBackground(pressable(8, 40));
        k.setOnClickListener(v -> { click(); press.run(); });
        LayoutParams lp = new LayoutParams(0, -1, weight);
        int m = Ui.dp(getContext(), 2);
        lp.setMargins(m, m, m, m);
        row.addView(k, lp);
        return k;
    }

    // Tap deletes one; hold repeats; hold long clears the whole box.
    @SuppressLint("ClickableViewAccessibility")
    private void backspace(LinearLayout row) {
        TextView k = key(row, "⌫", 1.6f, l::delete);
        Runnable[] repeat = new Runnable[1];
        long[] down = new long[1];
        repeat[0] = () -> {
            if (System.currentTimeMillis() - down[0] >= CLEAR_AFTER) {
                l.clear();
                return;
            }
            l.delete();
            main.postDelayed(repeat[0], REPEAT_EVERY);
        };
        k.setOnClickListener(null);
        k.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    click();
                    l.delete();
                    down[0] = System.currentTimeMillis();
                    main.postDelayed(repeat[0], REPEAT_AFTER);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    main.removeCallbacks(repeat[0]);
                    return true;
            }
            return true;
        });
    }

    private StateListDrawable pressable(float radius, int alpha) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, Ui.glass(getContext(), radius, 120));
        d.addState(new int[0], Ui.glass(getContext(), radius, alpha));
        d.setExitFadeDuration(150);
        return d;
    }

    private void click() {
        audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f);
    }
}
