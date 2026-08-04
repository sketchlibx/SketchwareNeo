package mod.sketchlibx.importer;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Verbose import logger for developer debug mode.
 *
 * Design contract:
 *  - Completely independent — no import logic, no Sketchware internals.
 *  - Thread-safe: all public methods synchronize on {@code this}.
 *  - Zero overhead when disabled: the enabled check is the very first line
 *    in every method and no String work is done unless it passes.
 *  - Reusable: the API is generic enough for Project Inspector, APK Builder,
 *    Block Generator, Java→Blocks, C/C++ Compiler — just call start() with
 *    a different sessionName and the log will be self-describing.
 *
 * Typical caller flow:
 * <pre>
 *   ImportLogger log = ImportLogger.create(verboseEnabled);
 *   log.start("AS Import", context, zipPath, "1.0");
 *
 *   log.step(1, "Extract ZIP");
 *   log.info("Size: 2.4 MB");
 *   log.stepDone(true);
 *
 *   log.step(2, "Detect Module");
 *   log.info("Found: app/");
 *   log.stepDone(true);
 *
 *   // ... rest of pipeline ...
 *
 *   log.stats(stats);
 *   log.finish(scId, appName, true);
 *   ImportLogger.showViewer(activity, log);
 * </pre>
 */
public class ImportLogger {

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ImportLogger create(boolean enabled) {
        return new ImportLogger(enabled);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    private final boolean enabled;
    private final StringBuilder log = new StringBuilder(8192);
    private final AtomicInteger warnings = new AtomicInteger(0);
    private final AtomicInteger errors   = new AtomicInteger(0);
    private final AtomicInteger skipped  = new AtomicInteger(0);

    // Step timing
    private long stepStartMillis = 0;
    private int  currentStepNumber = 0;

    // Session start
    private long sessionStartMillis = 0;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    private ImportLogger(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() { return enabled; }
    public int getWarningCount() { return warnings.get(); }
    public int getErrorCount()   { return errors.get(); }
    public int getSkippedCount() { return skipped.get(); }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Writes the session header block and records session start time.
     *
     * @param sessionName Human-readable name, e.g. "AS Import" or "APK Build".
     * @param context     Application context — used only for device info.
     * @param sourceInfo  Brief description of input (ZIP path, project path, etc.).
     * @param version     Tool/importer version string.
     */
    public synchronized void start(String sessionName, Context context,
                                   String sourceInfo, String version) {
        if (!enabled) return;
        sessionStartMillis = System.currentTimeMillis();
        line("==========  " + sessionName.toUpperCase() + " START  ==========");
        kv("Version",         version);
        kv("Time",            DATE_FMT.format(new Date(sessionStartMillis)));
        kv("Android Version", Build.VERSION.RELEASE + "  (API " + Build.VERSION.SDK_INT + ")");
        kv("Device",          Build.MANUFACTURER + " " + Build.MODEL);
        kv("Input",           sourceInfo);
        blank();
    }

    /**
     * Writes the summary block and total elapsed time.
     *
     * @param resultId   Generated project ID (sc_id) or empty string on failure.
     * @param resultName Generated project name or empty string on failure.
     * @param success    Whether the session completed without fatal error.
     */
    public synchronized void finish(String resultId, String resultName, boolean success) {
        if (!enabled) return;
        blank();
        line("==========  SUMMARY  ==========");
        long totalMs = System.currentTimeMillis() - sessionStartMillis;
        kv("Result",       success ? "SUCCESS" : "FAILED");
        kv("Total Time",   formatMs(totalMs));
        kv("Warnings",     String.valueOf(warnings.get()));
        kv("Errors",       String.valueOf(errors.get()));
        kv("Skipped",      String.valueOf(skipped.get()));
        if (!resultId.isEmpty())   kv("Generated ID",   resultId);
        if (!resultName.isEmpty()) kv("Project Name",   resultName);
        line("================================");
    }

    // ── Step lifecycle ────────────────────────────────────────────────────────

    /**
     * Begins a named pipeline step. Records the start time so that
     * {@link #stepDone(boolean)} can print the elapsed time.
     *
     * @param number Step number (1-based).
     * @param title  Short step name, e.g. "Extract ZIP".
     */
    public synchronized void step(int number, String title) {
        if (!enabled) return;
        stepStartMillis   = System.currentTimeMillis();
        currentStepNumber = number;
        blank();
        line("[STEP " + number + "]  " + title);
    }

    /**
     * Ends the current step, prints OK/FAILED and elapsed time.
     *
     * @param success true if the step completed without error.
     */
    public synchronized void stepDone(boolean success) {
        if (!enabled) return;
        long elapsed = System.currentTimeMillis() - stepStartMillis;
        kv("Result", success ? "OK" : "FAILED");
        kv("Time",   formatMs(elapsed));
    }

    /**
     * Marks the current step as skipped and increments the skipped counter.
     *
     * @param reason Short reason, e.g. "no assets directory".
     */
    public synchronized void stepSkipped(String reason) {
        if (!enabled) return;
        long elapsed = System.currentTimeMillis() - stepStartMillis;
        kv("Result", "SKIPPED  (" + reason + ")");
        kv("Time",   formatMs(elapsed));
        skipped.incrementAndGet();
    }

    // ── Content ───────────────────────────────────────────────────────────────

    /**
     * Logs an informational line. Lightweight — just appends the message.
     * ONLY does string work if logging is enabled.
     */
    public synchronized void info(String message) {
        if (!enabled) return;
        log.append("  ").append(message).append('\n');
    }

    /**
     * Logs a warning. Increments the warning counter.
     * Does NOT mark the step as failed.
     */
    public synchronized void warning(String message) {
        if (!enabled) return;
        log.append("  \u26A0 WARNING: ").append(message).append('\n');
        warnings.incrementAndGet();
    }

    /**
     * Logs a full error diagnostic block.
     *
     * Fields printed:
     *   STEP, Reason, Exception type, Stacktrace,
     *   Current File, Current Module, Current Activity, Current Layout,
     *   Current Library, Elapsed Time.
     *
     * @param reason Human-readable description of what went wrong.
     * @param t      The exception (may be null).
     * @param ctx    Optional {@link ErrorContext} with current pipeline state.
     */
    public synchronized void error(String reason, Throwable t, ErrorContext ctx) {
        if (!enabled) return;
        log.append("  \u274C ERROR\n");
        log.append("    Step   : ").append(currentStepNumber).append('\n');
        log.append("    Reason : ").append(reason).append('\n');
        if (t != null) {
            log.append("    Exception : ").append(t.getClass().getName()).append('\n');
            if (t.getMessage() != null) {
                log.append("    Message   : ").append(t.getMessage()).append('\n');
            }
            log.append("    Stacktrace:\n");
            for (StackTraceElement el : t.getStackTrace()) {
                log.append("      at ").append(el.toString()).append('\n');
            }
        }
        if (ctx != null) {
            if (ctx.currentFile     != null) log.append("    File     : ").append(ctx.currentFile).append('\n');
            if (ctx.currentModule   != null) log.append("    Module   : ").append(ctx.currentModule).append('\n');
            if (ctx.currentActivity != null) log.append("    Activity : ").append(ctx.currentActivity).append('\n');
            if (ctx.currentLayout   != null) log.append("    Layout   : ").append(ctx.currentLayout).append('\n');
            if (ctx.currentLibrary  != null) log.append("    Library  : ").append(ctx.currentLibrary).append('\n');
        }
        long elapsed = System.currentTimeMillis() - stepStartMillis;
        log.append("    Elapsed : ").append(formatMs(elapsed)).append('\n');
        errors.incrementAndGet();
    }

    /** Overload without ErrorContext (convenience for simple error sites). */
    public synchronized void error(String reason, Throwable t) {
        error(reason, t, null);
    }

    /**
     * Writes a labelled statistics block.
     * Call this once after all classifications/enumerations are complete.
     */
    public synchronized void stats(FileStats s) {
        if (!enabled) return;
        blank();
        line("--- File Statistics ---");
        kv("Activities",   String.valueOf(s.activities));
        kv("Fragments",    String.valueOf(s.fragments));
        kv("Custom Views", String.valueOf(s.customViews));
        kv("Java Files",   String.valueOf(s.javaFiles));
        kv("Kotlin Files", String.valueOf(s.kotlinFiles));
        kv("Layouts",      String.valueOf(s.layouts));
        kv("Drawables",    String.valueOf(s.drawables));
        kv("Fonts",        String.valueOf(s.fonts));
        kv("Assets",       String.valueOf(s.assets));
        kv("JNI Libs",     String.valueOf(s.jniLibs));
        kv("Libraries",    String.valueOf(s.libraries));
        blank();
    }

    /**
     * Writes a generic key-value pair (useful for ad-hoc facts).
     * Prefer {@link #info(String)} for free-form messages.
     */
    public synchronized void kv(String key, String value) {
        if (!enabled) return;
        log.append("  ").append(padRight(key, 16)).append(": ").append(value).append('\n');
    }

    // ── Output ────────────────────────────────────────────────────────────────

    /** Returns the full accumulated log text. Thread-safe snapshot. */
    public synchronized String getLog() {
        return enabled ? log.toString() : "";
    }

    // ── Viewer ────────────────────────────────────────────────────────────────

    /**
     * Shows the log viewer dialog.
     *
     * Features: selectable text, scrollable, monospace font, no line limit,
     * Copy / Share / Save as .txt / Close actions.
     *
     * Must be called on the UI thread.
     */
    public static void showViewer(Context context, ImportLogger logger) {
        if (!logger.isEnabled()) return;
        String logText = logger.getLog();

        // ── Scrollable monospace TextView ─────────────────────────────────────
        TextView tv = new TextView(context);
        tv.setText(logText);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11.5f);
        tv.setTextIsSelectable(true);
        tv.setLinksClickable(false);
        tv.setMaxLines(Integer.MAX_VALUE);
        int pad = dpToPx(context, 16);
        tv.setPadding(pad, pad, pad, pad);

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(context, 480)));
        scroll.addView(tv);

        // ── Button row ────────────────────────────────────────────────────────
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.END);
        btnRow.setPadding(pad, dpToPx(context, 4), pad, dpToPx(context, 4));

        Button btnCopy  = makeBtn(context, "Copy");
        Button btnShare = makeBtn(context, "Share");
        Button btnSave  = makeBtn(context, "Save .txt");
        btnRow.addView(btnCopy);
        btnRow.addView(btnShare);
        btnRow.addView(btnSave);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll);
        root.addView(btnRow);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle("Verbose Import Log")
                .setView(root)
                .setPositiveButton("Close", null)
                .create();

        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager)
                    context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Import Log", logText));
                toast(context, "Log copied to clipboard");
            }
        });

        btnShare.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Sketchware Neo Import Log");
            intent.putExtra(Intent.EXTRA_TEXT, logText);
            context.startActivity(Intent.createChooser(intent, "Share log via..."));
        });

        btnSave.setOnClickListener(v -> {
            String fileName = "import_log_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            .format(new Date())
                    + ".txt";
            File dest = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), fileName);
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write(logText);
                toast(context, "Saved to Downloads/" + fileName);
            } catch (IOException e) {
                toast(context, "Save failed: " + e.getMessage());
            }
        });

        dialog.show();
    }

    // ── FileStats holder ──────────────────────────────────────────────────────

    /** Collects file-count statistics to be logged via {@link #stats(FileStats)}. */
    public static final class FileStats {
        public int activities, fragments, customViews, javaFiles, kotlinFiles,
                   layouts, drawables, fonts, assets, jniLibs, libraries,
                   services, receivers, providers, applicationClasses;
    }

    // ── ErrorContext holder ───────────────────────────────────────────────────

    /**
     * Carries the current pipeline position for error diagnostics.
     * All fields are optional; null fields are omitted from the log.
     */
    public static final class ErrorContext {
        public String currentFile;
        public String currentModule;
        public String currentActivity;
        public String currentLayout;
        public String currentLibrary;

        public ErrorContext file(String v)     { currentFile     = v; return this; }
        public ErrorContext module(String v)   { currentModule   = v; return this; }
        public ErrorContext activity(String v) { currentActivity = v; return this; }
        public ErrorContext layout(String v)   { currentLayout   = v; return this; }
        public ErrorContext library(String v)  { currentLibrary  = v; return this; }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void line(String s) {
        log.append(s).append('\n');
    }

    private void blank() {
        log.append('\n');
    }

    private static String padRight(String s, int len) {
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    private static String formatMs(long ms) {
        if (ms < 1000) return ms + " ms";
        return String.format(Locale.getDefault(), "%.2f s", ms / 1000.0);
    }

    private static int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static Button makeBtn(Context ctx, String label) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(dpToPx(ctx, 4));
        b.setLayoutParams(lp);
        return b;
    }

    private static void toast(Context ctx, String msg) {
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
}
