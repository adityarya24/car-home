package dev.yuvaan.carhome;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayList;

// The steering voice button opens this app's launch activity (the box's
// setting_key_voice_assist names our package). Listen once, then do the
// thing ourselves: Google's plain voice search only searches, and the
// assistant would open Maps full screen instead of in our panel.
public class VoiceKey extends Activity {
    private static final String TAG = "CarHomeVoice";
    private static final int LISTEN = 1;
    private static final long BUSY_MS = 8000;
    // One listen at a time: the box and SiriWatch may both open us for one press.
    private static long openedAt;

    static boolean busy() {
        return android.os.SystemClock.uptimeMillis() - openedAt < BUSY_MS;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) return;
        // Test without talking: am start -n dev.yuvaan.carhome/.VoiceKey --es say "navigate to India Gate"
        String say = getIntent().getStringExtra("say");
        if (say != null) {
            run(say);
            finish();
            return;
        }
        if (busy()) {
            finish();
            return;
        }
        openedAt = android.os.SystemClock.uptimeMillis();
        Intent listen = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Bolo: play…, navigate to…, next");
        try {
            startActivityForResult(listen, LISTEN);
        } catch (Exception e) {
            Log.w(TAG, "No recognizer, falling back to the assistant", e);
            start(new Intent(Intent.ACTION_VOICE_COMMAND));
            finish();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        ArrayList<String> heard = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        openedAt = 0;
        if (result == RESULT_OK && heard != null && !heard.isEmpty()) run(heard.get(0));
        finish();
    }

    private void run(String spoken) {
        VoiceCommand c = VoiceCommand.parse(spoken);
        Log.i(TAG, "Heard \"" + spoken + "\" -> " + c);
        switch (c.kind) {
            case OPEN:
                if (!open(c.query)) start(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, spoken));
                break;
            case NAVIGATE: {
                Intent nav = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(c.query)))
                        .setPackage(HomeActivity.MAPS);
                HomeActivity home = HomeActivity.live;
                if (home != null && home.navigate(nav)) start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                else start(nav);
                break;
            }
            case PLAY_MUSIC:
                if (!HomeActivity.playFromSearch(c.query)) {
                    start(new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                            .putExtra(SearchManager.QUERY, c.query)
                            .setPackage(HomeActivity.YT_MUSIC));
                }
                break;
            case PLAY_VIDEO:
                // A results link, since YouTube ignores ACTION_SEARCH while a video is open.
                start(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query="
                        + Uri.encode(c.query))).setPackage(HomeActivity.YOUTUBE));
                CarTap.firstVideoSoon(HomeActivity.YOUTUBE, c.query);
                break;
            case NEXT: key(KeyEvent.KEYCODE_MEDIA_NEXT); break;
            case PREVIOUS: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
            case PAUSE: key(KeyEvent.KEYCODE_MEDIA_PAUSE); break;
            case RESUME: key(KeyEvent.KEYCODE_MEDIA_PLAY); break;
            default:
                start(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, c.query));
        }
    }

    // "open youtube": the launcher app whose name matches what was said.
    private boolean open(String name) {
        String want = name.replace(" ", "");
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (android.content.pm.ResolveInfo r : getPackageManager().queryIntentActivities(main, 0)) {
            String label = r.loadLabel(getPackageManager()).toString().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
            if (label.equals(want) || (want.length() >= 4 && label.startsWith(want))) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                if (pkg.equals(HomeActivity.MAPS)) {
                    start(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
                } else {
                    Ui.open(this, pkg);
                }
                return true;
            }
        }
        return false;
    }

    private void key(int code) {
        AudioManager audio = getSystemService(AudioManager.class);
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Log.w(TAG, "Can't start " + i, e);
            Toast.makeText(this, "Ye nahi ho paya", Toast.LENGTH_SHORT).show();
        }
    }
}
