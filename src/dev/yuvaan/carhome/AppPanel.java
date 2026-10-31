package dev.yuvaan.carhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.TaskInfo;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Outline;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Method;

// Runs another app (Maps, or YouTube in kids mode) live inside a home-screen
// panel: a virtual display drawn into a SurfaceView, with touches forwarded.
final class AppPanel extends FrameLayout implements SurfaceHolder.Callback {
    private static final String TAG = "CarHomePanel";
    private static final int SUPPORTS_TOUCH = 1 << 6;
    private static final int DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int TRUSTED = 1 << 10;
    private static final int IME_FALLBACK_DISPLAY = 1;
    private static final long WATCH_MS = 3000;

    private final Activity activity;
    final String pkg;
    private final String name;
    private final float zoom;
    private Object taskManager;
    private Method setFocusedTask;
    private final SurfaceView surface;
    private final TextView error;
    private VirtualDisplay display;
    private Object inputManager;
    private Method setDisplayId, injectInputEvent;
    private boolean launched;

    // zoom < 1 gives the app more room (smaller UI), so YouTube lays out
    // like a tablet instead of a cramped phone.
    AppPanel(Activity activity, String pkg, String name, float zoom) {
        super(activity);
        this.activity = activity;
        this.pkg = pkg;
        this.name = name;
        this.zoom = zoom;
        surface = new SurfaceView(activity);
        addView(surface, new LayoutParams(-1, -1));
        roundCorners(Ui.dp(activity, 18));
        error = Ui.text(activity, "", 14, Color.WHITE, false);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(GONE);
        addView(error, new LayoutParams(-1, -1));
        surface.getHolder().addCallback(this);
        surface.setOnTouchListener((v, event) -> forwardTouch(event));
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // surfaceChanged supplies the final panel dimensions.
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (width <= 0 || height <= 0) return;
        try {
            int density = Math.round(activity.getResources().getDisplayMetrics().densityDpi * zoom);
            if (display == null) {
                int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | SUPPORTS_TOUCH | DESTROY_CONTENT_ON_REMOVAL | TRUSTED;
                display = activity.getSystemService(DisplayManager.class).createVirtualDisplay(
                        "Car Home " + name, width, height, density, holder.getSurface(), flags);
                if (display == null) throw new IllegalStateException("Virtual display unavailable");
                Class<?> input = Class.forName("android.hardware.input.InputManager");
                inputManager = input.getMethod("getInstance").invoke(null);
                setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
                injectInputEvent = input.getMethod("injectInputEvent", InputEvent.class, int.class);
                try {
                    taskManager = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null);
                    setFocusedTask = taskManager.getClass().getMethod("setFocusedTask", int.class);
                } catch (Exception e) {
                    Log.w(TAG, "Panel focus unavailable", e);
                }
                imeOnMainScreen(display.getDisplay().getDisplayId());
                Log.i(TAG, "Created display " + display.getDisplay().getDisplayId()
                        + " " + width + "x" + height + " density=" + density);
            } else {
                display.resize(width, height, density);
                display.setSurface(holder.getSurface());
            }
            if (!launched) openApp();
            removeCallbacks(watchdog);
            postDelayed(watchdog, WATCH_MS);
        } catch (Exception e) {
            showError(name + " display unavailable", e);
            release();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (display != null) display.setSurface(null);
    }

    // If the app dies, the panel would freeze on its last frame; relaunch it.
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (display == null) return;
            if (!appRunning()) {
                Log.w(TAG, name + " gone from the panel, relaunching");
                openApp();
            }
            postDelayed(this, WATCH_MS);
        }
    };

    private boolean appRunning() {
        try {
            return appTask() != -1;
        } catch (Exception e) {
            return true;
        }
    }

    // Id of the app's task on this panel's display, or -1.
    private int appTask() throws Exception {
        int id = display.getDisplay().getDisplayId();
        for (ActivityManager.RunningTaskInfo t : activity.getSystemService(ActivityManager.class).getRunningTasks(50)) {
            if (TaskInfo.class.getField("displayId").getInt(t) == id) return t.taskId;
        }
        return -1;
    }


    void openApp() {
        if (display == null || !surface.getHolder().getSurface().isValid()) return;
        try {
            Intent intent = activity.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) throw new IllegalStateException(name + " is not installed");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(display.getDisplay().getDisplayId());
            activity.startActivity(intent, options.toBundle());
            launched = true;
            error.setVisibility(GONE);
            Log.i(TAG, "Launched " + name + " on display " + display.getDisplay().getDisplayId());
        } catch (Exception e) {
            showError("Can't open " + name, e);
        }
    }

    // Starts an intent (say, a Maps route) inside this panel.
    boolean open(Intent intent) {
        if (display == null) return false;
        try {
            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(display.getDisplay().getDisplayId());
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle());
            launched = true;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Can't open " + intent + " in " + name, e);
            return false;
        }
    }

    private boolean forwardTouch(MotionEvent event) {
        if (display == null || inputManager == null) return true;
        // With two panels Android doesn't always move focus to the one being
        // touched, so typing would go to the other app. Hand it over ourselves.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && setFocusedTask != null) {
            try {
                int task = appTask();
                if (task != -1) setFocusedTask.invoke(taskManager, task);
            } catch (Exception e) {
                Log.w(TAG, "Couldn't focus " + name, e);
            }
        }
        MotionEvent copy = MotionEvent.obtain(event);
        try {
            copy.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            setDisplayId.invoke(copy, display.getDisplay().getDisplayId());
            boolean injected = (Boolean) injectInputEvent.invoke(inputManager, copy, 0);
            if (!injected) throw new IllegalStateException("Input injection rejected");
        } catch (Exception e) {
            showError(name + " touch unavailable", e);
        } finally {
            copy.recycle();
        }
        return true;
    }

    // SurfaceView ignores the parent's clip, so round it too (hidden API).
    private void roundCorners(float radius) {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        try {
            SurfaceView.class.getMethod("setCornerRadius", float.class).invoke(surface, radius);
        } catch (Exception e) {
            Log.w(TAG, "Rounded panel corners unavailable", e);
        }
    }

    // The keyboard can't draw on the virtual display, so show it on the main
    // screen; it still types into the app.
    private static void imeOnMainScreen(int displayId) {
        try {
            Object wm = Class.forName("android.view.WindowManagerGlobal")
                    .getMethod("getWindowManagerService").invoke(null);
            wm.getClass().getMethod("setDisplayImePolicy", int.class, int.class)
                    .invoke(wm, displayId, IME_FALLBACK_DISPLAY);
        } catch (Exception e) {
            Log.w(TAG, "Keyboard policy unavailable", e);
        }
    }

    void release() {
        removeCallbacks(watchdog);
        if (display != null) display.release();
        display = null;
        launched = false;
        inputManager = null;
    }

    private void showError(String message, Exception e) {
        Log.e(TAG, message, e);
        error.setText(message);
        error.setVisibility(VISIBLE);
    }
}
