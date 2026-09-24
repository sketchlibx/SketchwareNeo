package mod.sketchlibx.terminal;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.function.Consumer;

import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

import pro.sketchware.utility.SketchwareUtil;

public class NeoTerminalView extends LinearLayout {

    private static final String ISSUES_URL = "https://github.com/sketchlibx/SketchwareNeo/issues";

    private static final int COLOR_BG = Color.parseColor("#121212");
    private static final int COLOR_SURFACE = Color.parseColor("#1E1E1E");
    private static final int COLOR_SURFACE_HIGH = Color.parseColor("#2A2A2A");
    private static final int COLOR_TEXT = Color.parseColor("#E0E0E0");
    private static final int COLOR_ACCENT = Color.parseColor("#4CAF50");
    private static final int COLOR_MUTED = Color.parseColor("#9E9E9E");

    private final TerminalView terminalView;
    private final NeoTerminalSessionClient sessionClient;
    private final String scId;

    private LinearLayout tabStrip;
    private TerminalSessionManager.Entry activeEntry;
    private boolean initialSessionAttached = false;

    private TextView ctrlKeyView;
    private TextView altKeyView;

    public NeoTerminalView(Context context, String scId) {
        this(context, scId, true);
    }

    public NeoTerminalView(Context context, String scId, boolean showStatusBar) {
        super(context);
        this.scId = scId;

        setOrientation(VERTICAL);
        setBackgroundColor(COLOR_BG);
        setClipChildren(false);

        if (showStatusBar) {
            addView(buildTabStrip(context));
        }

        terminalView = new TerminalView(context, null);
        terminalView.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        terminalView.setTextSize(spToPx(context, 13));
        terminalView.setTypeface(Typeface.MONOSPACE);
        terminalView.setBackgroundColor(COLOR_BG);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setPadding(0, 0, 0, 0);

        sessionClient = new NeoTerminalSessionClient(terminalView);
        terminalView.setTerminalViewClient(sessionClient);

        addView(terminalView);
        addView(buildExtraKeysRow(context));

        applyImeAwarePadding();
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (initialSessionAttached) return;

        if (getWidth() > 0 && getHeight() > 0) {
            initialSessionAttached = true;
            attachInitialSession(getContext());
        } else {
            getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (getWidth() > 0 && getHeight() > 0 && !initialSessionAttached) {
                        initialSessionAttached = true;
                        getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        attachInitialSession(getContext());
                    }
                }
            });
        }
    }

    private void attachInitialSession(Context context) {
        TerminalSessionManager.Entry entry = TerminalSessionManager.getOrCreateFirst(context, scId, sessionClient);
        switchToSession(entry);
        refreshTabStrip(context);
    }

    private void switchToSession(TerminalSessionManager.Entry entry) {
        activeEntry = entry;
        entry.session.updateTerminalSessionClient(sessionClient);
        terminalView.attachSession(entry.session);
        terminalView.requestFocus();
    }


    private View buildTabStrip(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(COLOR_SURFACE);
        bar.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SketchwareUtil.dpToPx(44)));

        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        tabStrip = new LinearLayout(context);
        tabStrip.setOrientation(HORIZONTAL);
        tabStrip.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SketchwareUtil.dpToPx(6);
        tabStrip.setPadding(pad, 0, pad, 0);
        scroller.addView(tabStrip);
        bar.addView(scroller);

        bar.addView(buildIconButton(context, "\uFF0B", "New session", v -> {
            TerminalSessionManager.Entry fresh = TerminalSessionManager.createSession(getContext(), scId, sessionClient);
            switchToSession(fresh);
            refreshTabStrip(context);
        }));
        bar.addView(buildIconButton(context, "\u2715", "Kill session", v -> {
            if (activeEntry == null) return;
            TerminalSessionManager.killSession(scId, activeEntry.sessionId);
            TerminalSessionManager.Entry next = TerminalSessionManager.getOrCreateFirst(context, scId, sessionClient);
            switchToSession(next);
            refreshTabStrip(context);
        }));
        bar.addView(buildIconButton(context, "\u26A0", "Report issue", v -> {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }));

        return bar;
    }

    private void refreshTabStrip(Context context) {
        if (tabStrip == null) return;
        tabStrip.removeAllViews();

        List<TerminalSessionManager.Entry> sessions = TerminalSessionManager.getSessions(scId);
        for (TerminalSessionManager.Entry entry : sessions) {
            tabStrip.addView(buildTab(context, entry));
        }
    }

    private View buildTab(Context context, TerminalSessionManager.Entry entry) {
        boolean isActive = entry == activeEntry;

        TextView tab = new TextView(context);
        tab.setText(entry.label);
        tab.setTextColor(isActive ? Color.BLACK : COLOR_TEXT);
        tab.setTypeface(Typeface.MONOSPACE, isActive ? Typeface.BOLD : Typeface.NORMAL);
        tab.setTextSize(12f);
        tab.setGravity(Gravity.CENTER);
        int hPad = SketchwareUtil.dpToPx(12);
        tab.setPadding(hPad, 0, hPad, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.rightMargin = SketchwareUtil.dpToPx(4);
        tab.setLayoutParams(params);
        tab.setBackground(pillBackground(isActive ? COLOR_ACCENT : COLOR_SURFACE_HIGH));
        tab.setOnClickListener(v -> {
            switchToSession(entry);
            refreshTabStrip(context);
        });
        return tab;
    }

    private View buildIconButton(Context context, String glyph, String contentDescription, OnClickListener onClick) {
        TextView button = new TextView(context);
        button.setText(glyph);
        button.setTextColor(COLOR_MUTED);
        button.setTextSize(15f);
        button.setGravity(Gravity.CENTER);
        button.setContentDescription(contentDescription);
        int size = SketchwareUtil.dpToPx(32);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.leftMargin = SketchwareUtil.dpToPx(2);
        button.setLayoutParams(params);
        button.setBackground(circleRipple());
        button.setOnClickListener(onClick);
        return button;
    }

    private RippleDrawable circleRipple() {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.WHITE);
        return new RippleDrawable(android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF")), null, mask);
    }

    private void applyImeAwarePadding() {
        ViewCompat.setOnApplyWindowInsetsListener(this, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            int pad = Math.max(0, imeBottom - navBottom);
            v.setPadding(0, 0, 0, pad);
            return insets;
        });
    }

    private View buildExtraKeysRow(Context context) {
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setLayoutParams(new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SketchwareUtil.dpToPx(46)));
        scroller.setBackgroundColor(COLOR_SURFACE);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SketchwareUtil.dpToPx(6);
        row.setPadding(pad, pad, pad, pad);

        ctrlKeyView = (TextView) addExtraKey(row, "CTRL", null);
        ctrlKeyView.setOnClickListener(v -> toggleModifier(ctrlKeyView, sessionClient::setCtrlDown));

        altKeyView = (TextView) addExtraKey(row, "ALT", null);
        altKeyView.setOnClickListener(v -> toggleModifier(altKeyView, sessionClient::setAltDown));

        addExtraKey(row, "ESC", () -> writeToSession("\u001b"));
        addExtraKey(row, "TAB", () -> writeToSession("\t"));
        addExtraKey(row, "/", () -> writeToSession("/"));
        addExtraKey(row, "-", () -> writeToSession("-"));
        addExtraKey(row, "|", () -> writeToSession("|"));
        addExtraKey(row, "~", () -> writeToSession("~"));
        addExtraKey(row, "HOME", () -> writeToSession("\u001b[H"));
        addExtraKey(row, "END", () -> writeToSession("\u001b[F"));
        addExtraKey(row, "PGUP", () -> writeToSession("\u001b[5~"));
        addExtraKey(row, "PGDN", () -> writeToSession("\u001b[6~"));
        addExtraKey(row, "\u2191", () -> writeToSession("\u001b[A"));
        addExtraKey(row, "\u2193", () -> writeToSession("\u001b[B"));
        addExtraKey(row, "\u2190", () -> writeToSession("\u001b[D"));
        addExtraKey(row, "\u2192", () -> writeToSession("\u001b[C"));
        addExtraKey(row, "\u21B9", () -> {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.toggleSoftInputFromWindow(terminalView.getWindowToken(), 0, 0);
        });

        scroller.addView(row);
        return scroller;
    }

    private void toggleModifier(TextView keyView, Consumer<Boolean> setter) {
        boolean now = !keyView.isSelected();
        keyView.setSelected(now);
        keyView.setBackground(now ? pillBackground(COLOR_ACCENT) : pillBackground(COLOR_SURFACE_HIGH));
        keyView.setTextColor(now ? Color.BLACK : COLOR_TEXT);
        setter.accept(now);
    }

    private View addExtraKey(LinearLayout row, String label, Runnable onClick) {
        Context context = row.getContext();
        TextView key = new TextView(context);
        key.setText(label);
        key.setTextColor(COLOR_TEXT);
        key.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        key.setTextSize(12f);
        key.setGravity(Gravity.CENTER);
        int hPad = SketchwareUtil.dpToPx(12);
        key.setPadding(hPad, 0, hPad, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.rightMargin = SketchwareUtil.dpToPx(6);
        key.setLayoutParams(params);
        key.setBackground(pillBackground(COLOR_SURFACE_HIGH));
        key.setClickable(true);
        key.setFocusable(true);
        if (onClick != null) {
            key.setOnClickListener(v -> onClick.run());
        }
        row.addView(key);
        return key;
    }

    private GradientDrawable pillBackground(int fillColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(SketchwareUtil.dpToPx(8));
        return drawable;
    }

    private void writeToSession(String data) {
        TerminalSession session = terminalView.getCurrentSession();
        if (session != null && session.isRunning()) {
            session.write(data);
        }
        if (ctrlKeyView != null && ctrlKeyView.isSelected()) toggleModifier(ctrlKeyView, sessionClient::setCtrlDown);
        if (altKeyView != null && altKeyView.isSelected()) toggleModifier(altKeyView, sessionClient::setAltDown);
    }

    private static int spToPx(Context context, float sp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.getResources().getDisplayMetrics());
    }

    public TerminalView getTerminalView() {
        return terminalView;
    }

    public String getScId() {
        return scId;
    }

    public void destroy() {
    }
}
