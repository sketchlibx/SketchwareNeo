package mod.sketchlibx.terminal;

import android.graphics.Color;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.ref.WeakReference;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.terminal.TextStyle;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

/**
 * Single class implementing both session-level callbacks ({@link TerminalSessionClient}) and
 * view-level input/rendering hooks ({@link TerminalViewClient}) — this is the standard combined
 * pattern used by minimal terminal-view integrations (AndroidIDE does the same thing across two
 * smaller classes; here it's one, since Sketchware Neo only ever needs a single session per view).
 * <p>
 * NOTE ON LIBRARY VERSIONING: TerminalSessionClient / TerminalViewClient's exact member list has
 * shifted slightly across termux-app releases (mostly additions, not removals). If Android Studio
 * flags this class as not fully implementing an interface after you sync Gradle, hit
 * Alt+Enter → "Implement methods" on the class name — it'll show you exactly what your pinned
 * {@code termuxVersion} expects and you fill in the 1-2 extra stubs. Everything with actual
 * terminal-behavior logic (colors, keys, session lifecycle) is implemented below; anything
 * version-specific left as a stub is UI polish (long-press menus, scale-to-zoom, etc.), not
 * PTY/env correctness.
 */
public class NeoTerminalSessionClient implements TerminalSessionClient, TerminalViewClient {

    private static final String TAG = "NeoTerminal";

    private static final int TERMINAL_BACKGROUND = Color.parseColor("#121212");
    private static final int TERMINAL_FOREGROUND = Color.parseColor("#E0E0E0");
    private static final int TERMINAL_CURSOR = Color.parseColor("#4CAF50"); // green cursor, matches the prompt accent

    private final WeakReference<TerminalView> terminalViewRef;
    private boolean ctrlDown = false;
    private boolean altDown = false;

    public NeoTerminalSessionClient(TerminalView terminalView) {
        this.terminalViewRef = new WeakReference<>(terminalView);
    }

    private TerminalView view() {
        return terminalViewRef.get();
    }

    // ---------------------------------------------------------------------
    // TerminalSessionClient — session lifecycle / output
    // ---------------------------------------------------------------------

    @Override
    public void onTextChanged(TerminalSession changedSession) {
        TerminalView v = view();
        if (v != null && v.getCurrentSession() == changedSession) {
            v.onScreenUpdated();
        }
    }

    @Override
    public void onTitleChanged(TerminalSession changedSession) {
        // No tab-title UI in any of the 3 placements yet — hook left here for when you add one
        // (e.g. show the shell's OSC-set title in the BottomSheet's drag handle or the Activity toolbar).
    }

    @Override
    public void onSessionFinished(TerminalSession finishedSession) {
        TerminalView v = view();
        if (v != null && v.getCurrentSession() == finishedSession) v.onScreenUpdated();
    }

    @Override
    public void onCopyTextToClipboard(TerminalSession session, String text) {
        TerminalView v = view();
        if (v == null) return;
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("terminal", text));
        }
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
        TerminalView v = view();
        if (v == null) return;
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) v.getContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence paste = clipboard.getPrimaryClip().getItemAt(0).coerceToText(v.getContext());
            if (paste != null && session != null) {
                session.write(paste.toString());
            }
        }
    }

    @Override
    public void onBell(TerminalSession session) {
        // Deliberately silent — a beeping IDE terminal is more annoying than useful.
        // Flash the background briefly if you want a visual bell later.
    }

    @Override
    public void onColorsChanged(TerminalSession changedSession) {
        TerminalView v = view();
        if (v != null) v.onScreenUpdated();
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        // Blink state changed — TerminalView redraws itself on its own timer, nothing to do.
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return null; // library default (block cursor)
    }

    /**
     * Called once the session's {@code TerminalEmulator} exists (right after {@link TerminalView}
     * measures itself and calls {@code session.initializeEmulator(...)} for the first time).
     * This is the correct, and only reliable, point to push the app's default color scheme —
     * the emulator (and its 256-color palette) genuinely does not exist before this fires.
     */
    @Override
    public void onEmulatorSet() {
        TerminalView v = view();
        TerminalSession session = v != null ? v.getCurrentSession() : null;
        if (session == null || session.getEmulator() == null) return;

        try {
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND] = TERMINAL_BACKGROUND;
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_FOREGROUND] = TERMINAL_FOREGROUND;
            session.getEmulator().mColors.mCurrentColors[TextStyle.COLOR_INDEX_CURSOR] = TERMINAL_CURSOR;
        } catch (Throwable t) {
            // If TextStyle's index constants moved in your pinned termux version, don't crash the
            // terminal over cosmetics — it'll just render with the library's stock dark scheme
            // (which is already a very close match to #121212/#E0E0E0).
            Log.w(TAG, "Could not apply custom color scheme, falling back to library default", t);
        }
        if (v != null) v.onScreenUpdated();
    }

    // ---------------------------------------------------------------------
    // TerminalViewClient — input / view behavior
    // ---------------------------------------------------------------------

    @Override
    public float onScale(float scale) {
        return 1.0f; // pinch-to-zoom text size disabled; app controls text size explicitly
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        TerminalView v = view();
        if (v != null) {
            v.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) v.getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false; // let system back button behave normally (close BottomSheet / Activity)
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return true;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return true;
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) {
        return false; // let TerminalView's default handling process the key
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return false;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false; // no custom text-selection menu yet — default long-press selection still works
    }

    @Override
    public boolean readControlKey() {
        return ctrlDown;
    }

    @Override
    public boolean readAltKey() {
        return altDown;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) {
        return false; // let the library's default codepoint handling write to the session
    }

    @Override
    public void logError(String tag, String message) {
        Log.e(tag, message);
    }

    @Override
    public void logWarn(String tag, String message) {
        Log.w(tag, message);
    }

    @Override
    public void logInfo(String tag, String message) {
        Log.i(tag, message);
    }

    @Override
    public void logDebug(String tag, String message) {
        Log.d(tag, message);
    }

    @Override
    public void logVerbose(String tag, String message) {
        Log.v(tag, message);
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
        Log.e(tag, message, e);
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
        Log.e(tag, "", e);
    }

    /** Wired to the "Ctrl" toggle button in {@link NeoTerminalView}'s extra-keys row. */
    public void setCtrlDown(boolean down) {
        this.ctrlDown = down;
    }

    /** Wired to the "Alt" toggle button in {@link NeoTerminalView}'s extra-keys row. */
    public void setAltDown(boolean down) {
        this.altDown = down;
    }
}
