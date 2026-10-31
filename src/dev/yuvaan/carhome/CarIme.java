package dev.yuvaan.carhome;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// The box's keyboard. Maps in the launcher panel types through it too, but
// the system can't draw a keyboard for that display, so the launcher shows
// its own Keys while Maps is typing.
public class CarIme extends InputMethodService implements Keys.Listener {
    private static volatile CarIme current;

    // Lets the launcher's Keys type through whichever editor is active.
    static final Keys.Listener REMOTE = new Keys.Listener() {
        public void text(String s) { if (current != null) current.text(s); }
        public void delete() { if (current != null) current.delete(); }
        public void clear() { if (current != null) current.clear(); }
        public void search(String q) { if (current != null) current.search(q); }
        public void enter() { if (current != null) current.enter(); }
        public void hide() { if (current != null) current.hide(); }
    };

    private static final String PREFS = "keys", RECENTS = "recents";
    private static final int MAX_RECENTS = 3;

    private EditorInfo editor;

    @Override
    public View onCreateInputView() {
        return new Keys(this, this);
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        current = this;
        editor = info;
        // YouTube focuses its search box without asking for a keyboard; in a
        // panel that would leave no way to type, so show the keys anyway.
        HomeActivity home = HomeActivity.live;
        if (!restarting && home != null && home.inPanel(info.packageName)
                && info.inputType != InputType.TYPE_NULL) {
            home.showKeys(true, info.packageName);
        }
    }

    // Apps in the launcher panels: show the launcher's keys instead of
    // the system window, which would squeeze the home screen.
    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        HomeActivity home = HomeActivity.live;
        if (home != null && editor != null && home.inPanel(editor.packageName)) {
            Log.i("CarIme", "Launcher keys for " + editor.packageName);
            home.showKeys(true, editor.packageName);
            return false;
        }
        return super.onShowInputRequested(flags, configChange);
    }

    @Override
    public void hideWindow() {
        super.hideWindow();
        hideLauncherKeys();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        hideLauncherKeys();
    }

    private static void hideLauncherKeys() {
        HomeActivity home = HomeActivity.live;
        if (home != null) home.showKeys(false, null);
    }

    @Override
    public void text(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(s, 1);
    }

    @Override
    public void delete() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL);
    }

    @Override
    public void clear() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.commitText("", 1);
    }

    @Override
    public void search(String q) {
        clear();
        text(q);
        submit();
    }

    // Last few searches, newest first, for the chip row.
    static List<String> recents(Context c) {
        String saved = c.getSharedPreferences(PREFS, MODE_PRIVATE).getString(RECENTS, "");
        List<String> out = new ArrayList<>();
        for (String s : saved.split("\n")) if (!s.isEmpty() && !Arrays.asList(Keys.PLACES).contains(s)) out.add(s);
        return out;
    }

    private void remember(String q) {
        q = q.trim();
        if (q.length() < 2) return;
        List<String> list = recents(this);
        list.remove(q);
        list.add(0, q);
        while (list.size() > MAX_RECENTS) list.remove(list.size() - 1);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(RECENTS, String.join("\n", list)).apply();
    }

    @Override
    public void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            CharSequence typed = ic.getTextBeforeCursor(100, 0);
            if (typed != null) remember(typed.toString());
        }
        submit();
    }

    private void submit() {
        InputConnection ic = getCurrentInputConnection();
        int action = editor == null ? EditorInfo.IME_ACTION_NONE : editor.imeOptions & EditorInfo.IME_MASK_ACTION;
        if (ic != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action);
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
        }
    }

    @Override
    public void hide() {
        hideLauncherKeys();
        requestHideSelf(0);
    }
}
