package dev.yuvaan.carhome;

import android.app.Activity;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

// Debug screen: is any sound reaching the mic? Shows a live level and logs
// the loudest moment per second (tag CarHomeMic). Started over adb only.
public class MicTest extends Activity {
    private volatile boolean running;
    private TextView label;
    private ProgressBar meter;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(20, 24, 36));
        int p = Ui.dp(this, 40);
        root.setPadding(p, p, p, p);
        TextView title = Ui.text(this, "Mic test: say something", 24, Color.WHITE, true);
        label = Ui.text(this, "Starting…", 18, Color.WHITE, false);
        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(100);
        root.addView(title);
        root.addView(label);
        root.addView(meter, new LinearLayout.LayoutParams(-1, Ui.dp(this, 30)));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        running = true;
        new Thread(this::listen).start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        running = false;
    }

    private void listen() {
        int rate = 16000;
        int size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2;
        AudioRecord rec;
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
            rec.startRecording();
        } catch (Exception e) {
            Log.e("CarHomeMic", "Can't record", e);
            runOnUiThread(() -> label.setText("Can't record: " + e.getMessage()));
            return;
        }
        short[] buf = new short[rate / 10];
        int loudest = 0;
        long second = System.currentTimeMillis();
        while (running) {
            int n = rec.read(buf, 0, buf.length);
            int peak = 0;
            for (int i = 0; i < n; i++) peak = Math.max(peak, Math.abs(buf[i]));
            loudest = Math.max(loudest, peak);
            int level = (int) Math.min(100, peak * 100L / 32767 * 4);
            int shown = peak;
            runOnUiThread(() -> {
                label.setText("Level " + shown);
                meter.setProgress(level);
            });
            if (System.currentTimeMillis() - second >= 1000) {
                Log.i("CarHomeMic", "peak " + loudest);
                loudest = 0;
                second = System.currentTimeMillis();
            }
        }
        rec.stop();
        rec.release();
    }
}
