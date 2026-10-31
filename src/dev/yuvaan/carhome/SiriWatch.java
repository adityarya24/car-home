package dev.yuvaan.carhome;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;

// The car sends the steering voice button to the box as a CarPlay "Siri"
// request: action 1 on every press, action 2 only now and then. The box
// opens our VoiceKey on action 2 alone, so most presses did nothing. The
// box's CarPlay service logs every request; watch for action 1 and open
// VoiceKey ourselves. Needs READ_LOGS (granted by the platform signature).
final class SiriWatch {
    private static final String TAG = "CarHomeSiri";

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile Process logcat;
    private volatile boolean running;

    SiriWatch(Context context) {
        this.context = context.getApplicationContext();
    }

    void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this::watch, "SiriWatch");
        t.setDaemon(true);
        t.start();
    }

    void stop() {
        running = false;
        Process p = logcat;
        if (p != null) p.destroy();
    }

    private void watch() {
        while (running) {
            try {
                // -T 1: only new lines from now on. HB is the box's CarPlay audio/button log.
                logcat = Runtime.getRuntime().exec(new String[]{"logcat", "-v", "brief", "-T", "1", "HB:D", "*:S"});
                BufferedReader in = new BufferedReader(new InputStreamReader(logcat.getInputStream()));
                Log.i(TAG, "Watching the voice button");
                for (String line; running && (line = in.readLine()) != null; ) {
                    if (line.contains("RequestSiri action :1") || line.contains("RequestSiri action :2")) {
                        main.post(this::press);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Log watch failed", e);
            }
            if (running) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void press() {
        if (VoiceKey.busy()) return;
        Log.i(TAG, "Voice button");
        context.startActivity(new Intent(context, VoiceKey.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
