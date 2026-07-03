package mod.sketchlibx.importer;

import android.util.Log;

import java.io.File;

/**
 * Pre-flight validator.
 *
 * ALL validation must pass before the first file is written.
 * This ensures the import is atomic — either fully succeeds or nothing
 * is written to the Sketchware project store.
 *
 * Call validate() BEFORE creating the sc_id or any directories.
 */
public class ImportValidator {

    private static final String TAG = "ImportValidator";

    /** Human-readable failure reason, populated only when validate() returns false. */
    private String failReason = "";

    public String getFailReason() {
        return failReason;
    }

    /**
     * Validates the extracted project temp directory.
     *
     * @param projectRoot   The folder containing settings.gradle (found by ModuleDetector).
     * @param appModuleDir  The app module folder (contains src/main/).
     * @return true if all checks pass, false otherwise.
     */
    public boolean validate(File projectRoot, File appModuleDir) {

        // ── 1. projectRoot must exist ─────────────────────────────────────────
        if (projectRoot == null || !projectRoot.exists()) {
            return fail("Could not locate Android Studio project root. " +
                    "Ensure the ZIP contains a valid settings.gradle.");
        }

        // ── 2. appModule must exist ───────────────────────────────────────────
        if (appModuleDir == null || !appModuleDir.exists()) {
            return fail("Could not locate the app module. " +
                    "The ZIP must contain a module with src/main/.");
        }

        // ── 3. src/main must exist ────────────────────────────────────────────
        File srcMain = new File(appModuleDir, "src/main");
        if (!srcMain.exists() || !srcMain.isDirectory()) {
            return fail("src/main directory not found inside app module.");
        }

        // ── 4. AndroidManifest.xml must exist ────────────────────────────────
        File manifest = new File(srcMain, "AndroidManifest.xml");
        if (!manifest.exists()) {
            return fail("AndroidManifest.xml not found at: " +
                    manifest.getAbsolutePath());
        }
        if (manifest.length() == 0) {
            return fail("AndroidManifest.xml is empty.");
        }

        // ── 5. At least one build.gradle must exist ───────────────────────────
        boolean hasGradle = new File(appModuleDir, "build.gradle").exists()
                || new File(appModuleDir, "build.gradle.kts").exists();
        if (!hasGradle) {
            // Not fatal — we use safe defaults — but log a warning.
            Log.w(TAG, "No build.gradle found in " + appModuleDir.getAbsolutePath()
                    + ". Will use default version/SDK values.");
        }

        // ── 6. Java or Kotlin source must exist ──────────────────────────────
        File javaSrc  = new File(srcMain, "java");
        File kotlinSrc = new File(srcMain, "kotlin");
        boolean hasSource = (javaSrc.exists()  && hasFiles(javaSrc))
                ||           (kotlinSrc.exists() && hasFiles(kotlinSrc));
        if (!hasSource) {
            return fail("No .java or .kt source files found under src/main/java or src/main/kotlin.");
        }

        return true; // all checks passed
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean fail(String reason) {
        this.failReason = reason;
        Log.e(TAG, "Validation failed: " + reason);
        return false;
    }

    /** Returns true if the directory contains at least one file (recursively). */
    private boolean hasFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isFile()) return true;
            if (child.isDirectory() && hasFiles(child)) return true;
        }
        return false;
    }
}
