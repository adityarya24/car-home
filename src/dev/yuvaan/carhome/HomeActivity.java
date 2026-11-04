package dev.yuvaan.carhome;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.util.Log;
import android.media.AudioManager;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.ProgressBar;
import android.os.SystemClock;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.app.SearchManager;
import android.provider.MediaStore;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Scanner;

public class HomeActivity extends Activity {
    static final String MAPS = "com.google.android.apps.maps";
    static final String YOUTUBE = "com.google.android.youtube";
    static final String SETTINGS = "com.android.settings";
    static final String YT_MUSIC = "com.google.android.apps.youtube.music";
    private static final String MEDIA = "media", LAST_APP = "lastApp", WAS_PLAYING = "wasPlaying", RESUMED_BOOT = "resumedBoot";
    static final String CARPLAY = "com.suding.speedplay";
    static final String DIALER = "com.google.android.dialer";

    // Left column and Maps panel, as layout weights.
    private static final int LEFT_WEIGHT = 34, MAPS_WEIGHT = 66;
    // Home address (set on the first tap of Home, long press to change), the
    // exact point of the suggestion it was picked from, and the last location
    // fix, kept for boots before Maps has a new one.
    private static final String PLACES = "places", HOME = "home", HOME_AT = "homeAt", LAST_LAT = "lat", LAST_LON = "lon";

    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView weather, songTitle, songArtist, playPause;
    private ImageView songIcon;
    private ProgressBar progress;
    private View musicCard;
    private MediaController controller;
    private MediaSessionManager sessions;
    private AppPanel maps, video;
    private SiriWatch siri;
    private View kidColumn;
    private LinearLayout row;
    private View left;
    private View keys;
    private Keys keyboard;
    static volatile HomeActivity live;

    void openMaps() {
        maps.openApp();
    }
    private Ui.Wallpaper wallpaper;
    private View nightShade;
    // From the weather call; null until it has answered once.
    private static volatile Boolean isDay;
    private static volatile Sky.Kind skyKind = Sky.Kind.CLEAR;
    private Sky sky;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(build());
        live = this;
        siri = new SiriWatch(this);
        siri.start();
        startWeather();
        sessions = getSystemService(MediaSessionManager.class);
        main.postDelayed(this::resumeAfterBoot, 10 * 1000);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        watchMedia();
        main.removeCallbacks(tick);
        main.post(tick);
        showNight();
        CarTap.showOverApps(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CarTap.showOverApps(true);
        main.removeCallbacks(tick);
        try {
            sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        if (live == this) live = null;
        if (siri != null) siri.stop();
        if (keys != null) getWindowManager().removeView(keys);
        if (maps != null) maps.release();
        if (video != null) video.release();
        main.removeCallbacksAndMessages(null);
        if (controller != null) controller.unregisterCallback(mediaCallback);
        super.onDestroy();
    }

    // Preview a sky over adb: am start -n dev.yuvaan.carhome/.HomeActivity --es sky RAIN --ez night false
    // or a wallpaper: --es wall AURORA
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String wall = intent.getStringExtra("wall");
        if (wall != null) wallpaper.setLook(Ui.Wallpaper.Look.valueOf(wall));
        String k = intent.getStringExtra("sky");
        if (k == null) return;
        // Holds until the next weather call.
        skyKind = Sky.Kind.valueOf(k);
        isDay = !intent.getBooleanExtra("night", false);
        showNight();
    }

    @Override
    public void onBackPressed() {
        // Home screen: back goes nowhere.
    }

    private View build() {
        FrameLayout root = new FrameLayout(this);
        wallpaper = new Ui.Wallpaper(this);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout screen = new LinearLayout(this);
        root.addView(screen, new FrameLayout.LayoutParams(-1, -1));
        row = new LinearLayout(this);
        int pad = Ui.dp(this, 10);
        row.setPadding(pad, pad, pad, pad);
        screen.addView(row, new LinearLayout.LayoutParams(0, -1, 1));
        // The same bar CarTap shows over apps, so the apps never move.
        screen.addView(new Dock(this, this::openMaps, false), new LinearLayout.LayoutParams(Ui.dp(this, Dock.WIDTH_DP), -1));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        this.left = left;
        row.addView(left, new LinearLayout.LayoutParams(0, -1, LEFT_WEIGHT));

        left.addView(skyCard(clockCard()), new LinearLayout.LayoutParams(-1, -2));
        left.addView(gap(), new LinearLayout.LayoutParams(-1, Ui.dp(this, 8)));
        left.addView(musicCard(), new LinearLayout.LayoutParams(-1, 0, 1));
        left.addView(gap(), new LinearLayout.LayoutParams(-1, Ui.dp(this, 8)));
        left.addView(shortcuts(), new LinearLayout.LayoutParams(-1, Ui.dp(this, 46)));

        row.addView(gap(), new LinearLayout.LayoutParams(Ui.dp(this, 10), -1));
        row.addView(mapsPanel(), new LinearLayout.LayoutParams(0, -1, MAPS_WEIGHT));
        // Night: a light shade over everything, Maps included, so the screen
        // doesn't glare or reflect in the windscreen. It never takes touches.
        nightShade = new View(this);
        nightShade.setBackgroundColor(Color.argb(40, 0, 0, 0));
        nightShade.setVisibility(View.GONE);
        root.addView(nightShade, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    // Called by CarIme when Maps in the panel starts or stops typing. The keys
    // get their own non-focusable window: touching the home window would take
    // focus from Maps and close its search box.
    void showKeys(boolean show, String pkg) {
        main.post(() -> {
            if (keys == null) {
                if (!show) return;
                FrameLayout box = new FrameLayout(this);
                int pad = Ui.dp(this, 10);
                box.setPadding(pad, 0, pad, pad);
                keyboard = new Keys(this, CarIme.REMOTE);
                box.addView(keyboard);
                keys = box;
                WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                        -1, -2, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
                lp.gravity = Gravity.BOTTOM;
                lp.token = getWindow().getDecorView().getWindowToken();
                getWindowManager().addView(keys, lp);
            }
            if (show) keyboard.setChips(MAPS.equals(pkg), CarIme.recents(this));
            keys.setVisibility(show ? View.VISIBLE : View.GONE);
        });
    }

    private View gap() {
        return new View(this);
    }

    private View clockCard() {
        LinearLayout card = card();
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("h:mm");
        time.setTextSize(46);
        time.setTextColor(Color.WHITE);
        time.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        TextClock ampm = new TextClock(this);
        ampm.setFormat12Hour("a");
        ampm.setFormat24Hour("a");
        ampm.setTextSize(18);
        ampm.setTextColor(Color.argb(220, 255, 255, 255));
        ampm.setPadding(Ui.dp(this, 6), 0, 0, 0);
        LinearLayout clock = new LinearLayout(this);
        clock.addView(time);
        clock.addView(ampm);
        card.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(13);
        date.setTextColor(Color.argb(200, 255, 255, 255));
        card.addView(date);
        weather = Ui.text(this, "Weather…", 13, Color.argb(200, 255, 255, 255), false);
        card.addView(weather);
        // Long press the clock for every installed app.
        card.setOnLongClickListener(v -> {
            startActivity(new Intent(this, AppsActivity.class));
            return true;
        });
        return card;
    }

    // The clock card over a live weather sky, clipped to the card's corners.
    private View skyCard(View card) {
        FrameLayout box = new FrameLayout(this);
        box.setBackground(Ui.glass(this, 22, 30));
        box.setClipToOutline(true);
        sky = new Sky(this);
        box.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        card.setBackground(null);
        box.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return box;
    }

    // Album art on the left, as tall as the card; song, progress and
    // controls beside it. Fills the space between the clock and the dock.
    private View musicCard() {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        songIcon = new SquareImage(this);
        songIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        songIcon.setBackground(Ui.glass(this, 14, 30));
        songIcon.setClipToOutline(true);
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(0, -1, 38);
        art.gravity = Gravity.CENTER_VERTICAL;
        card.addView(songIcon, art);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(Ui.dp(this, 12), 0, 0, 0);
        songTitle = Ui.text(this, "Nothing playing", 15, Color.WHITE, true);
        songArtist = Ui.text(this, "YT Music", 12, Color.argb(190, 255, 255, 255), false);
        side.addView(songTitle);
        side.addView(songArtist);
        side.addView(gap(), new LinearLayout.LayoutParams(-1, 0, 1));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.argb(90, 255, 255, 255)));
        side.addView(progress, new LinearLayout.LayoutParams(-1, Ui.dp(this, 6)));
        side.addView(gap(), new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(control("⏮\uFE0E", v -> transport(0)), controlParams());
        playPause = control("▶\uFE0E", v -> transport(1));
        controls.addView(playPause, controlParams());
        controls.addView(control("⏭\uFE0E", v -> transport(2)), controlParams());
        side.addView(controls);
        card.addView(side, new LinearLayout.LayoutParams(0, -1, 62));

        card.setOnClickListener(v -> {
            if (controller != null) Ui.open(this, controller.getPackageName());
            else Ui.open(this, YT_MUSIC);
        });
        musicCard = card;
        return card;
    }

    // Square, as big as its slot allows, so the art grows with the card.
    private static final class SquareImage extends ImageView {
        SquareImage(Context c) { super(c); }

        @Override
        protected void onMeasure(int w, int h) {
            int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
            setMeasuredDimension(size, size);
        }
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            showProgress();
            main.postDelayed(this, 1000);
        }
    };

    private void showProgress() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        long length = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        if (s == null || length <= 0) {
            progress.setProgress(0);
            return;
        }
        long at = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            at += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        progress.setProgress((int) Math.min(1000, Math.max(0, at * 1000 / length)));
    }

    private TextView control(String s, View.OnClickListener l) {
        TextView t = Ui.text(this, s, 22, Color.WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.glass(this, 14, 40));
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        return lp;
    }

    // Big one-tap jobs under the music card; the apps are in the Dock.
    private View shortcuts() {
        LinearLayout r = new LinearLayout(this);
        shortcut(r, "🏠 Home", v -> goHome());
        r.getChildAt(0).setOnLongClickListener(v -> {
            askHome();
            return true;
        });
        shortcut(r, "⛽ Petrol", v -> navigate(new Intent(Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=" + Uri.encode("petrol pump"))).setPackage(MAPS)));
        shortcut(r, "🧸 Kids", v -> kidsMode());
        shortcut(r, "💡", v -> askLight());
        r.getChildAt(3).setOnLongClickListener(v -> {
            navigate(new Intent().setClassName(LED, LED + ".YunlianLedColorSetNewActivity"));
            return true;
        });
        return r;
    }

    // The box's ambient LEDs follow one system property, "R-G-B-count-speed-mode",
    // which the stock LED app (com.suding.led) writes and the firmware reads. Only
    // the colour changes here; speed and effect stay as the LED app left them.
    private static final String LED = "com.suding.led", LED_NOW = "persist.sd.color.type",
            LED_SAVED = "persist.sd.original_color.type";
    private static final int[] LIGHTS = {0xFF262A, 0xFF8746, 0xFFE822, 0x85FF1F, 0x00FFEF,
            0x0076FA, 0x8A01FE, 0xFE01FB, 0xFFFFFF, 0x000000};

    private void askLight() {
        // Two rows of five, so the dots stay big enough to hit while driving.
        LinearLayout dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.VERTICAL);
        dots.setGravity(Gravity.CENTER_HORIZONTAL);
        dots.setPadding(Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16));
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Ambient light").setView(dots)
                .setNegativeButton("More", (x, w) -> navigate(new Intent()
                        .setClassName(LED, LED + ".YunlianLedColorSetNewActivity")))
                .create();
        for (int c : LIGHTS) {
            TextView dot = Ui.text(this, c == 0 ? "Off" : "", 13, Color.WHITE, true);
            dot.setGravity(Gravity.CENTER);
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(0xFF000000 | c);
            g.setStroke(Ui.dp(this, 2), 0x55FFFFFF);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                if (!setLight(c)) {
                    d.dismiss();
                    navigate(new Intent().setClassName(LED, LED + ".YunlianLedColorSetNewActivity"));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44));
            lp.setMargins(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
            if (dots.getChildCount() == 0 || ((LinearLayout) dots.getChildAt(dots.getChildCount() - 1)).getChildCount() == 5)
                dots.addView(new LinearLayout(this));
            ((LinearLayout) dots.getChildAt(dots.getChildCount() - 1)).addView(dot, lp);
        }
        d.show();
    }

    // False when the firmware refuses the write; the caller opens the LED app instead.
    private static boolean setLight(int rgb) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            String[] was = ((String) sp.getMethod("get", String.class, String.class)
                    .invoke(null, LED_SAVED, "0-0-0-4-0.5-0")).split("-");
            StringBuilder s = new StringBuilder((rgb >> 16 & 255) + "-" + (rgb >> 8 & 255) + "-" + (rgb & 255));
            for (int i = 3; i < was.length; i++) s.append('-').append(was[i]);
            java.lang.reflect.Method set = sp.getMethod("set", String.class, String.class);
            set.invoke(null, LED_NOW, s.toString());
            set.invoke(null, LED_SAVED, s.toString());
            return true;
        } catch (Exception e) {
            Log.w("CarHome", "ambient light", e);
            return false;
        }
    }

    private void goHome() {
        SharedPreferences p = getSharedPreferences(PLACES, MODE_PRIVATE);
        String home = p.getString(HOME, "");
        if (home.isEmpty()) {
            askHome();
            return;
        }
        // A picked suggestion has its exact point; typed text is left to Maps to find.
        String q = p.getString(HOME_AT, home);
        navigate(new Intent(Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=" + Uri.encode(q))).setPackage(MAPS));
    }

    // Typing shows up to four matching places near the car, so it is clear the
    // right "Sector 21" is the one being saved. Picking one keeps its exact point.
    private void askHome() {
        SharedPreferences p = getSharedPreferences(PLACES, MODE_PRIVATE);
        EditText in = new EditText(this);
        in.setSingleLine();
        in.setHint("Type your area, then pick it below");
        in.setText(p.getString(HOME, ""));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        box.addView(in);
        box.addView(list);
        // [0] = point of the picked suggestion, kept until the text is edited again.
        String[] at = {p.getString(HOME_AT, null)};
        int[] typed = {0};
        boolean[] filling = {false};
        in.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (filling[0]) return;
                at[0] = null;
                String q = s.toString().trim();
                int mine = ++typed[0];
                if (q.length() < 3) {
                    list.removeAllViews();
                    return;
                }
                // Wait for a pause in typing, and drop answers to older text.
                main.postDelayed(() -> {
                    if (mine != typed[0]) return;
                    new Thread(() -> {
                        List<String[]> found = suggestPlaces(HomeActivity.this, q);
                        main.post(() -> {
                            if (mine != typed[0]) return;
                            list.removeAllViews();
                            for (String[] f : found) {
                                TextView row = Ui.text(HomeActivity.this, "📍 " + f[0], 15, Color.WHITE, false);
                                row.setPadding(0, Ui.dp(HomeActivity.this, 9), 0, Ui.dp(HomeActivity.this, 9));
                                row.setOnClickListener(v -> {
                                    filling[0] = true;
                                    in.setText(f[0]);
                                    in.setSelection(f[0].length());
                                    filling[0] = false;
                                    at[0] = f[1];
                                    list.removeAllViews();
                                });
                                list.addView(row);
                            }
                        });
                    }).start();
                }, 500);
            }
        });
        new AlertDialog.Builder(this)
                .setTitle("Home address")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    SharedPreferences.Editor e = p.edit().putString(HOME, in.getText().toString().trim());
                    if (at[0] != null) e.putString(HOME_AT, at[0]);
                    else e.remove(HOME_AT);
                    e.apply();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Places matching `q`, nearest to the car first: [label, "lat,lon"]. Photon
    // (OSM data, made for search-as-you-type, no key) rather than the box's
    // geocoder, which answers in Vietnamese. Empty when offline.
    private static List<String[]> suggestPlaces(Context c, String q) {
        List<String[]> out = new java.util.ArrayList<>();
        SharedPreferences p = c.getSharedPreferences(PLACES, MODE_PRIVATE);
        String near = p.contains(LAST_LAT) ? "&lat=" + p.getString(LAST_LAT, "") + "&lon=" + p.getString(LAST_LON, "") : "";
        try {
            URL u = new URL("https://photon.komoot.io/api/?limit=4&lang=en&q=" + Uri.encode(q) + near);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "CarHome/0.9");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            try (InputStream in = conn.getInputStream(); Scanner sc = new Scanner(in).useDelimiter("\\A")) {
                org.json.JSONArray features = new JSONObject(sc.next()).getJSONArray("features");
                for (int i = 0; i < features.length(); i++) {
                    JSONObject f = features.getJSONObject(i), pr = f.getJSONObject("properties");
                    org.json.JSONArray xy = f.getJSONObject("geometry").getJSONArray("coordinates");
                    StringBuilder label = new StringBuilder();
                    for (String k : new String[]{"name", "street", "district", "city", "state"}) {
                        String v = pr.optString(k, "");
                        if (!v.isEmpty() && label.indexOf(v) < 0) label.append(label.length() > 0 ? ", " : "").append(v);
                    }
                    if (label.length() > 0) out.add(new String[]{label.toString(), xy.getDouble(1) + "," + xy.getDouble(0)});
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private void shortcut(LinearLayout row, String label, View.OnClickListener l) {
        TextView t = Ui.text(this, label, 14, Color.WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.glass(this, 14, 40));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        row.addView(t, lp);
    }

    private View mapsPanel() {
        maps = new AppPanel(this, MAPS, "Maps", 1f);
        return maps;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        c.setPadding(p, Ui.dp(this, 8), p, Ui.dp(this, 10));
        c.setBackground(Ui.glass(this, 22, 30));
        return c;
    }

    // Kids mode: YouTube live on the left (60%) instead of the clock column,
    // Maps on the right. Maps' nav voice ducks the video on its own.
    private void kidsMode() {
        if (video != null) return;
        video = new AppPanel(this, YOUTUBE, "YouTube", 0.75f);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        // Its own bar under the video, so it never covers YouTube's buttons.
        TextView exit = Ui.text(this, "✕  Exit kids mode", 14, Color.WHITE, true);
        exit.setGravity(Gravity.CENTER);
        exit.setBackground(Ui.glass(this, 14, 40));
        exit.setOnClickListener(v -> exitKidsMode());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 38));
        lp.topMargin = Ui.dp(this, 8);
        column.addView(exit, lp);
        kidColumn = column;
        left.setVisibility(View.GONE);
        row.addView(column, 0, new LinearLayout.LayoutParams(0, -1, 60));
        maps.setLayoutParams(new LinearLayout.LayoutParams(0, -1, 40));
    }

    private void exitKidsMode() {
        if (video == null) return;
        video.release();
        row.removeView(kidColumn);
        video = null;
        kidColumn = null;
        left.setVisibility(View.VISIBLE);
        maps.setLayoutParams(new LinearLayout.LayoutParams(0, -1, MAPS_WEIGHT));
    }

    // A spoken route: show it in the Maps panel, not full screen.
    boolean navigate(Intent route) {
        return maps != null && maps.open(route);
    }

    // A spoken "play". YT Music's search intent only opens results, and a
    // plain playFromSearch on its session fails ("entity not found"); it
    // only searches for clients connected to its media browser, the way
    // Android Auto does it.
    static boolean playFromSearch(String query) {
        HomeActivity h = live;
        if (h == null) return false;
        MediaBrowser[] b = new MediaBrowser[1];
        b[0] = new MediaBrowser(h, new ComponentName(YT_MUSIC, YT_MUSIC + ".mediabrowser.MusicBrowserService"),
                new MediaBrowser.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        new MediaController(h, b[0].getSessionToken()).getTransportControls()
                                .playFromSearch(query, new Bundle());
                        Log.i("CarHomeVoice", "YT Music browser playFromSearch " + query);
                        backHomeSoon();
                        h.main.postDelayed(b[0]::disconnect, 30000);
                    }

                    @Override
                    public void onConnectionFailed() {
                        Log.w("CarHomeVoice", "YT Music browser refused us; searching and pressing Shuffle");
                        CarTap.pressSoon(YT_MUSIC, "Shuffle", "Play", "Mix");
                        backHomeSoon();
                        h.startActivity(new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                                .putExtra(SearchManager.QUERY, query).setPackage(YT_MUSIC)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                }, null);
        b[0].connect();
        return true;
    }

    // After a spoken "play", come back home once the song has started.
    static void backHomeSoon() {
        HomeActivity h = live;
        if (h == null) return;
        h.main.postDelayed(() -> {
            if (h.playing()) h.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }, 7000);
    }

    // True when pkg runs inside one of the home-screen panels.
    boolean inPanel(String pkg) {
        return pkg != null && (pkg.equals(maps.pkg) || (video != null && pkg.equals(video.pkg)));
    }

    private void hideBars() {
        WindowInsetsController c = getWindow().getInsetsController();
        if (c == null) return;
        c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    // ---- Now playing ----

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pickController;

    private final MediaController.Callback mediaCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            showMedia();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            showMedia();
        }
    };

    private void watchMedia() {
        ComponentName me = new ComponentName(this, MediaListener.class);
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, me);
            pickController(sessions.getActiveSessions(me));
        } catch (SecurityException e) {
            // Granted once over adb: cmd notification allow_listener dev.yuvaan.carhome/.MediaListener
            songTitle.setText("Music access off");
            songArtist.setText("Tap to allow");
            musicCard.setOnClickListener(v ->
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }
    }

    private void pickController(List<MediaController> list) {
        if (controller != null) controller.unregisterCallback(mediaCallback);
        controller = null;
        if (list != null) {
            // Prefer whatever is playing, else the most recent session.
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    controller = c;
                    break;
                }
            }
            if (controller == null && !list.isEmpty()) controller = list.get(0);
        }
        if (controller != null) controller.registerCallback(mediaCallback, main);
        showMedia();
    }

    private void showMedia() {
        if (controller == null) {
            songTitle.setText("Nothing playing");
            songArtist.setText("YT Music");
            songIcon.setImageDrawable(Ui.emojiIcon(this, "🎵"));
            playPause.setText("▶\uFE0E");
            return;
        }
        MediaMetadata m = controller.getMetadata();
        String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        songTitle.setText(title == null ? "Unknown" : title);
        songArtist.setText(artist == null ? "" : artist);
        android.graphics.Bitmap art = m == null ? null : m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art != null) songIcon.setImageBitmap(art);
        else songIcon.setImageDrawable(Ui.icon(this, controller.getPackageName()));
        showProgress();
        PlaybackState s = controller.getPlaybackState();
        playPause.setText(s != null && s.getState() == PlaybackState.STATE_PLAYING ? "❚❚" : "▶\uFE0E");
        rememberPlaying(s);
    }

    // What was playing when the car went off. Only a real pause clears it:
    // switching off kills the app, which reports stopped, not paused.
    private void rememberPlaying(PlaybackState s) {
        if (s == null) return;
        SharedPreferences.Editor e = getSharedPreferences(MEDIA, MODE_PRIVATE).edit();
        if (s.getState() == PlaybackState.STATE_PLAYING) {
            e.putString(LAST_APP, controller.getPackageName()).putBoolean(WAS_PLAYING, true).apply();
        } else if (s.getState() == PlaybackState.STATE_PAUSED) {
            e.putBoolean(WAS_PLAYING, false).apply();
        }
    }

    // Once per boot: if music was on when the car switched off, play it again.
    private void resumeAfterBoot() {
        SharedPreferences p = getSharedPreferences(MEDIA, MODE_PRIVATE);
        int boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (p.getInt(RESUMED_BOOT, -2) == boot) return;
        p.edit().putInt(RESUMED_BOOT, boot).apply();
        String app = p.getString(LAST_APP, null);
        if (!p.getBoolean(WAS_PLAYING, false) || app == null || playing()) return;
        Log.i("CarHome", "Resuming " + app + " after boot " + boot);
        getSystemService(AudioManager.class).dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        getSystemService(AudioManager.class).dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
        // The system may not know the last player yet: ask the app directly.
        main.postDelayed(() -> {
            if (playing()) return;
            for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
                Intent i = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(app)
                        .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(action, KeyEvent.KEYCODE_MEDIA_PLAY));
                sendBroadcast(i);
            }
        }, 6000);
    }

    private boolean playing() {
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    private void transport(int which) {
        if (controller == null) {
            if (which == 1) Ui.open(this, YT_MUSIC);
            return;
        }
        MediaController.TransportControls t = controller.getTransportControls();
        if (which == 0) t.skipToPrevious();
        else if (which == 2) t.skipToNext();
        else {
            PlaybackState s = controller.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) t.pause();
            else t.play();
        }
    }

    // ---- Weather (open-meteo, no key) ----

    private void startWeather() {
        new Thread(() -> {
            String line = fetchWeather(this);
            main.post(() -> {
                if (line != null) weather.setText(line);
                showNight();
            });
            // No network yet (just booted, or no signal): try again soon.
            main.postDelayed(this::startWeather, line == null ? 60 * 1000 : 30 * 60 * 1000);
        }).start();
    }

    private static String fetchWeather(Context c) {
        Location here = lastLocation(c);
        SharedPreferences p = c.getSharedPreferences(PLACES, MODE_PRIVATE);
        if (here != null) {
            p.edit().putString(LAST_LAT, "" + here.getLatitude()).putString(LAST_LON, "" + here.getLongitude()).apply();
        } else if (!p.contains(LAST_LAT)) {
            return null; // No fix yet: retried in a minute.
        }
        double lat = Double.parseDouble(p.getString(LAST_LAT, "0")), lon = Double.parseDouble(p.getString(LAST_LON, "0"));
        try {
            URL u = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,is_day&timezone=auto");
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream(); Scanner sc = new Scanner(in).useDelimiter("\\A")) {
                JSONObject cur = new JSONObject(sc.next()).getJSONObject("current");
                long temp = Math.round(cur.getDouble("temperature_2m"));
                isDay = cur.getInt("is_day") == 1;
                skyKind = Sky.of(cur.getInt("weather_code"));
                String place = placeName(c, lat, lon);
                return temp + "°  " + describe(cur.getInt("weather_code")) + (place == null ? "" : " · " + place);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // Newest fix from any provider; Maps keeps the fused one fresh.
    private static Location lastLocation(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location best = null;
        for (String p : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(p);
            if (l != null && (best == null || l.getElapsedRealtimeNanos() > best.getElapsedRealtimeNanos())) best = l;
        }
        return best;
    }

    // The box's geocoder answers with Vietnamese names (it came set up for
    // Vietnam), so anything that isn't plain English falls through to OSM.
    private static String placeName(Context c, double lat, double lon) {
        try {
            List<Address> a = new Geocoder(c, new java.util.Locale("en", "IN")).getFromLocation(lat, lon, 1);
            if (a != null && !a.isEmpty()) {
                Address ad = a.get(0);
                for (String n : new String[]{ad.getSubLocality(), ad.getLocality(), ad.getSubAdminArea()}) {
                    if (plain(n)) return n;
                }
                Log.i("CarHome", "Geocoder gave " + ad.getSubLocality() + " / " + ad.getLocality());
            }
        } catch (Exception ignored) {
        }
        try {
            URL u = new URL("https://nominatim.openstreetmap.org/reverse?format=json&zoom=16&accept-language=en&lat="
                    + lat + "&lon=" + lon);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setRequestProperty("User-Agent", "CarHome/0.5");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            try (InputStream in = conn.getInputStream(); Scanner sc = new Scanner(in).useDelimiter("\\A")) {
                JSONObject ad = new JSONObject(sc.next()).getJSONObject("address");
                for (String k : new String[]{"suburb", "neighbourhood", "village", "town", "city", "county"}) {
                    String n = ad.optString(k, null);
                    if (plain(n)) return n;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static boolean plain(String s) {
        return s != null && !s.isEmpty() && s.chars().allMatch(ch -> ch < 128);
    }

    // Sunset from the weather; without it, 7 pm to 6 am counts as night.
    private void showNight() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean night = isDay != null ? !isDay : hour >= 19 || hour < 6;
        nightShade.setVisibility(night ? View.VISIBLE : View.GONE);
        wallpaper.setNight(night);
        sky.set(skyKind, night);
    }

    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        return "Storm";
    }
}
