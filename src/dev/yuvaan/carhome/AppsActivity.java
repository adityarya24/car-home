package dev.yuvaan.carhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Every launchable app, as glass tiles. Long press hides an app; the last
// tile shows hidden ones (dimmed), and a long press brings one back.
public class AppsActivity extends Activity {
    private static final String PREFS = "apps", HIDDEN = "hidden";
    // Big enough to hit while driving: five across, four rows on the 800x480 screen.
    private static final int COLUMNS = 5, ICON_DP = 54, TEXT_SP = 13;
    // Box tools nobody opens while driving, hidden until the list is edited.
    private static final Set<String> DEFAULT_HIDDEN = new HashSet<>(java.util.Arrays.asList(
            "com.android.launcher3/android.app.Activity",
            "com.android.launcher3/vn.gotech.sdk.SettingActivity",
            "com.suding.apkinstaller/com.suding.apkinstaller.MainActivity",
            "de.hohnepeople.keepadb/de.hohnepeople.keepadb.MainActivity",
            "com.tailscale.ipn/com.tailscale.ipn.MainActivity",
            "com.android.documentsui/com.android.documentsui.LauncherActivity",
            "com.android.gallery3d/com.android.gallery3d.app.GalleryActivity"));

    private GridLayout grid;
    private boolean showHidden;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.addView(new Ui.Wallpaper(this), new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        grid = new GridLayout(this);
        grid.setColumnCount(COLUMNS);
        int pad = Ui.dp(this, 10);
        grid.setPadding(pad, pad, pad, pad);
        fill();
        scroll.addView(grid);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
    }

    private void fill() {
        grid.removeAllViews();
        Set<String> hidden = hidden();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = getPackageManager().queryIntentActivities(main, 0);
        apps.sort((a, b) -> a.loadLabel(getPackageManager()).toString()
                .compareToIgnoreCase(b.loadLabel(getPackageManager()).toString()));
        int hiddenCount = 0;
        for (ResolveInfo r : apps) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            // One package can have several icons (the vendor launcher has two).
            String key = pkg + "/" + r.activityInfo.name;
            boolean isHidden = hidden.contains(key);
            if (isHidden) hiddenCount++;
            if (isHidden && !showHidden) continue;
            String label = r.loadLabel(getPackageManager()).toString();
            View tile = Ui.appTile(this, label, r.loadIcon(getPackageManager()), v -> {
                Ui.open(this, pkg);
                finish();
            }, ICON_DP, TEXT_SP);
            if (isHidden) tile.setAlpha(0.4f);
            tile.setOnLongClickListener(v -> {
                setHidden(key, !isHidden);
                Toast.makeText(this, label + (isHidden ? " is back" : " hidden"), Toast.LENGTH_SHORT).show();
                fill();
                return true;
            });
            add(tile);
        }
        if (hiddenCount > 0) {
            add(Ui.appTile(this, showHidden ? "Done" : "Hidden (" + hiddenCount + ")",
                    getDrawable(showHidden ? android.R.drawable.ic_menu_close_clear_cancel : android.R.drawable.ic_menu_view),
                    v -> {
                        showHidden = !showHidden;
                        fill();
                    }, ICON_DP, TEXT_SP));
        }
    }

    private void add(View tile) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        int m = Ui.dp(this, 4);
        lp.setMargins(m, m, m, m);
        grid.addView(tile, lp);
    }

    private Set<String> hidden() {
        return new HashSet<>(getSharedPreferences(PREFS, MODE_PRIVATE).getStringSet(HIDDEN, DEFAULT_HIDDEN));
    }

    private void setHidden(String key, boolean hide) {
        Set<String> s = hidden();
        if (hide) s.add(key); else s.remove(key);
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putStringSet(HIDDEN, s).apply();
    }
}
