package dev.yuvaan.carhome;

import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Locale;

// Presses one button for a spoken command: "Shuffle" on YT Music's search
// results, or the first video on YouTube's. Neither app plays straight from
// a search for us, so this finishes the job. It only acts for a few seconds
// after a command, and only in those two apps.
//
// It also gives back the left-edge swipe for Back that the box's own
// launcher had: full-screen apps like Netflix hide the side bar, and there
// is no other way out of them. And over other apps it draws the Dock on
// top of the box's own bar, which shuffles recent apps.
public class CarTap extends AccessibilityService {
    private static final String TAG = "CarHomeTap";
    private static final long GIVE_UP_MS = 12000, POLL_MS = 700, SETTLE_MS = 1500;
    // The car's bezel covers the first ~25px, so the strip is wide; it skips
    // the top, where apps keep their own back arrow and menu button.
    private static final int EDGE_DP = 30, TOP_DP = 70, SWIPE_DP = 40;

    private static volatile CarTap current;
    private static String wantPkg;
    private static String[] wantLabels;
    private static boolean wantVideo;
    private static String wantQuery;
    private static long from, until;

    private final Handler main = new Handler(Looper.getMainLooper());
    private View edge;
    private Dock dock;

    // Labels are tried in order: an exact button text or description.
    static void pressSoon(String pkg, String... labels) {
        arm(pkg, labels, false, null);
    }

    // YouTube results for this query: tap the first video. Waits until the
    // search box shows the query, so an older results page isn't used.
    static void firstVideoSoon(String pkg, String query) {
        arm(pkg, new String[0], true, query.toLowerCase(Locale.ROOT));
    }

    static boolean ready() {
        return current != null;
    }

    private static void arm(String pkg, String[] labels, boolean video, String query) {
        wantPkg = pkg;
        wantLabels = labels;
        wantVideo = video;
        wantQuery = query;
        from = SystemClock.uptimeMillis() + SETTLE_MS;
        until = from + GIVE_UP_MS;
        CarTap t = current;
        if (t != null) t.poll();
    }

    @Override
    protected void onServiceConnected() {
        current = this;
        addEdge();
        addDock();
        Log.i(TAG, "Ready");
    }

    @Override
    public void onDestroy() {
        if (current == this) current = null;
        if (edge != null) getSystemService(WindowManager.class).removeView(edge);
        if (dock != null) getSystemService(WindowManager.class).removeView(dock);
        super.onDestroy();
    }

    // A thin strip down the left edge: swipe right from it to go back.
    @SuppressLint("ClickableViewAccessibility")
    private void addEdge() {
        edge = new View(this);
        float swipe = SWIPE_DP * getResources().getDisplayMetrics().density;
        float[] downX = new float[1];
        boolean[] done = new boolean[1];
        edge.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = ev.getRawX();
                    done[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!done[0] && ev.getRawX() - downX[0] > swipe) {
                        done[0] = true;
                        performGlobalAction(GLOBAL_ACTION_BACK);
                    }
                    break;
            }
            return true;
        });
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Ui.dp(this, EDGE_DP), WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT | Gravity.TOP;
        lp.y = Ui.dp(this, TOP_DP);
        try {
            getSystemService(WindowManager.class).addView(edge, lp);
        } catch (WindowManager.BadTokenException e) {
            // The service was turned off again before it finished connecting.
            Log.w(TAG, "No edge swipe", e);
            edge = null;
            return;
        }
        edge.setVisibility(homeInFront() ? View.GONE : View.VISIBLE);
    }

    // The edge swipe is for other apps only: Back does nothing on home. The
    // dock is always up, over home's own copy too, and just switches mode.
    // HomeActivity calls this as it comes and goes.
    static void showOverApps(boolean show) {
        CarTap t = current;
        if (t == null) return;
        if (t.edge != null) t.edge.setVisibility(show ? View.VISIBLE : View.GONE);
        if (t.dock != null) t.dock.setOverApps(show);
    }

    private static boolean homeInFront() {
        HomeActivity home = HomeActivity.live;
        return home != null && home.hasWindowFocus();
    }

    private void addDock() {
        Dock[] self = new Dock[1];
        Dock d = new Dock(this, () -> {
            HomeActivity home = HomeActivity.live;
            if (!self[0].overApps() && home != null) home.openMaps();
            else Dock.home(this);
        }, !homeInFront());
        self[0] = d;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                Ui.dp(this, Dock.WIDTH_DP), WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE);
        lp.gravity = Gravity.RIGHT | Gravity.TOP;
        try {
            getSystemService(WindowManager.class).addView(d, lp);
        } catch (WindowManager.BadTokenException e) {
            Log.w(TAG, "No dock", e);
            return;
        }
        dock = d;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (wantPkg != null && e.getPackageName() != null && wantPkg.contentEquals(e.getPackageName())) tryNow();
    }

    @Override
    public void onInterrupt() {
    }

    private void poll() {
        main.removeCallbacks(pollTick);
        main.post(pollTick);
    }

    private final Runnable pollTick = new Runnable() {
        @Override
        public void run() {
            if (wantPkg == null) return;
            tryNow();
            if (wantPkg != null) main.postDelayed(this, POLL_MS);
        }
    };

    private void tryNow() {
        if (wantPkg == null) return;
        if (SystemClock.uptimeMillis() > until) {
            Log.w(TAG, "Gave up looking in " + wantPkg);
            wantPkg = null;
            return;
        }
        if (SystemClock.uptimeMillis() < from) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null || !wantPkg.contentEquals(root.getPackageName())) return;
        AccessibilityNodeInfo hit = null;
        if (wantVideo) {
            if (wantQuery != null && find(root, n -> text(n).equals(wantQuery)) == null) return;
            // Results read "<title> - <length> - ... - play video"; the mini player
            // is just "play video", so skip that one.
            hit = find(root, n -> desc(n).endsWith(" - play video"));
        } else {
            for (String label : wantLabels) {
                String l = label.toLowerCase(Locale.ROOT);
                hit = find(root, n -> text(n).equals(l) || desc(n).equals(l));
                if (hit != null) break;
            }
        }
        if (hit == null) return;
        for (AccessibilityNodeInfo n = hit; n != null; n = n.getParent()) {
            if (n.isClickable()) {
                Log.i(TAG, "Pressing \"" + text(hit) + desc(hit) + "\" in " + wantPkg);
                n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                wantPkg = null;
                return;
            }
        }
    }

    private interface Test { boolean ok(AccessibilityNodeInfo n); }

    // Depth-first, so the first match is the one nearest the top of the page.
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo n, Test t) {
        if (n == null) return null;
        if (n.isVisibleToUser() && t.ok(n)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo hit = find(n.getChild(i), t);
            if (hit != null) return hit;
        }
        return null;
    }

    private static String text(AccessibilityNodeInfo n) {
        return n.getText() == null ? "" : n.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private static String desc(AccessibilityNodeInfo n) {
        return n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim().toLowerCase(Locale.ROOT);
    }
}
