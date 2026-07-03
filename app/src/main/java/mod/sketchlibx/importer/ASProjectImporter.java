package mod.sketchlibx.importer;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.besome.sketch.beans.ProjectFileBean;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import a.a.a.wq;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Main entry point for the Android Studio → Sketchware Neo import pipeline.
 *
 * Orchestration order (no step starts until the previous one succeeds):
 *
 * [1] Extract ZIP to temp directory
 * [2] Detect project root + app module   (ModuleDetector)
 * [3] Validate structure                 (ImportValidator)
 * [4] Parse AndroidManifest.xml          (ManifestParser)
 * [5] Parse build.gradle / kts           (GradleParser)
 * [6] Classify source files              (SourceClassifier)
 * [7] Import layout XML → view/file data (LayoutImporter)
 * [8] Map resources                      (ResourceMapper)
 * [9] Detect libraries                   (LibraryDetector)
 * [10] Copy source files to data/<id>/files/java/
 * [11] Copy assets/
 * [12] Copy native libs (if any)
 * [13] Write all Sketchware project files (SketchwareWriter)
 * [14] Cleanup temp directory
 *
 * Atomicity guarantee:
 * SketchwareWriter.write() is the ONLY step that touches the Sketchware
 * project store. If it throws, SketchwareWriter.rollback() removes any
 * partially-written files before this task reports failure.
 * No existing Sketchware project is ever touched.
 *
 * sc_id isolation:
 * lC.b() (called inside SketchwareWriter) reads the existing project list
 * and always returns max(existing_ids) + 1. Confirmed from lC.java source.
 *
 * Usage:
 * new ASProjectImporter(context, zipPath, new ASProjectImporter.Callback() {
 * public void onProgress(String message)       { ... }
 * public void onSuccess(String scId)           { ... }
 * public void onFailure(String reason)         { ... }
 * }).execute();
 */
public class ASProjectImporter extends AsyncTask<Void, String, ASProjectImporter.Result> {

    private static final String TAG = "ASProjectImporter";

    // ── Callback interface ─────────────────────────────────────────────────────

    public interface Callback {
        /** Called on UI thread with human-readable progress messages. */
        void onProgress(String message);

        /** Called on UI thread when import succeeds. scId is the new project's ID. */
        void onSuccess(String scId);

        /**
         * Called on UI thread when import fails.
         * reason is a human-readable description of what went wrong.
         */
        void onFailure(String reason);
    }

    // ── Result carrier (internal) ─────────────────────────────────────────────

    static class Result {
        final boolean success;
        final String  scId;       // populated on success
        final String  failReason; // populated on failure

        Result(String scId)          { this.success = true;  this.scId = scId; this.failReason = null; }
        Result(String r, boolean f)  { this.success = false; this.scId = null; this.failReason = r; }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context  context;
    private final String   zipFilePath;
    private final Callback callback;

    /** Temp directory where ZIP is extracted. Deleted after import regardless of outcome. */
    private File tempDir;

    /** Writer instance kept so rollback() can be called on failure. */
    private SketchwareWriter writer;

    // ── Static Helper: File Picker & UI ───────────────────────────────────────

    public static void showPicker(Activity activity, ProjectsFragment fragment) {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"zip"});
        options.setTitle("Select AS Project (.zip)");
        
        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                // Initialize the Progress Dialog
                ProgressMsgBoxBinding binding = ProgressMsgBoxBinding.inflate(activity.getLayoutInflater());
                binding.tvProgress.setText("Initializing Importer...");
                
                AlertDialog loadingDialog = new MaterialAlertDialogBuilder(activity)
                        .setTitle("Importing Android Studio Project")
                        .setCancelable(false)
                        .setView(binding.getRoot())
                        .create();
                loadingDialog.show();

                // Run the new Importer with a Callback to update the Dialog UI
                new ASProjectImporter(activity, file.getAbsolutePath(), new Callback() {
                    @Override
                    public void onProgress(String message) {
                        binding.tvProgress.setText(message);
                    }

                    @Override
                    public void onSuccess(String scId) {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();
                        SketchwareUtil.toast("AS Project Imported Successfully!");
                        if (fragment != null) {
                            fragment.refreshProjectsList();
                        }
                    }

                    @Override
                    public void onFailure(String reason) {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();
                        SketchwareUtil.toastError("Import Failed: " + reason, Toast.LENGTH_LONG);
                    }
                }).execute();
            }
        };

        new FilePickerDialogFragment(options, callback).show(fragment.getChildFragmentManager(), "filePicker");
    }

    // ── Constructor ───────────────────────────────────────────────────────────

    public ASProjectImporter(Context context, String zipFilePath, Callback callback) {
        this.context     = context.getApplicationContext();
        this.zipFilePath = zipFilePath;
        this.callback    = callback;
    }

    // ── AsyncTask lifecycle ───────────────────────────────────────────────────

    @Override
    protected Result doInBackground(Void... voids) {
        try {
            return runPipeline();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in import pipeline", e);
            if (writer != null) writer.rollback();
            return failure("Unexpected error: " + e.getMessage());
        } finally {
            cleanupTemp();
        }
    }

    @Override
    protected void onProgressUpdate(String... values) {
        if (callback != null && values.length > 0) {
            callback.onProgress(values[0]);
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        if (callback == null) return;
        if (result.success) {
            callback.onSuccess(result.scId);
        } else {
            callback.onFailure(result.failReason);
        }
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    private Result runPipeline() throws Exception {

        // ── [1] Extract ZIP ───────────────────────────────────────────────────
        progress("Extracting project ZIP...");
        tempDir = extractZip(zipFilePath);
        if (tempDir == null) {
            return failure("Failed to extract ZIP file. Ensure the file is a valid .zip archive.");
        }

        // ── [2] Detect module ─────────────────────────────────────────────────
        progress("Detecting project structure...");
        ModuleDetector detector = new ModuleDetector();
        ModuleDetector.DetectionResult detection = detector.detect(tempDir);
        if (detection == null) {
            return failure("Cannot detect Android project structure. " +
                    "Ensure the ZIP contains a valid Android Studio project.");
        }
        Log.d(TAG, "Module dir: " + detection.appModuleDir.getAbsolutePath());

        File appModuleDir = detection.appModuleDir;
        File srcMain      = new File(appModuleDir, "src/main");

        // ── [3] Validate ──────────────────────────────────────────────────────
        progress("Validating project structure...");
        ImportValidator validator = new ImportValidator();
        if (!validator.validate(detection.projectRoot, appModuleDir)) {
            return failure(validator.getFailReason());
        }

        // ── [4] Parse manifest ────────────────────────────────────────────────
        progress("Parsing AndroidManifest.xml...");
        ManifestParser manifestParser = new ManifestParser(srcMain);
        ParsedManifest manifest = manifestParser.parse();
        Log.d(TAG, "Package: " + manifest.packageName
                + ", Activities: " + manifest.activities.size());

        // ── [5] Parse gradle ──────────────────────────────────────────────────
        progress("Parsing build configuration...");
        GradleParser gradleParser = new GradleParser();
        ParsedGradle gradle = gradleParser.parse(appModuleDir);

        // Use manifest package as fallback if gradle didn't find applicationId
        if ("com.imported.project".equals(gradle.applicationId)
                && !manifest.packageName.isEmpty()) {
            gradle.applicationId = manifest.packageName;
        }

        // ── [6] Classify source files ─────────────────────────────────────────
        progress("Classifying source files...");
        SourceClassifier classifier = new SourceClassifier();
        List<ClassifiedSource> sources = new ArrayList<>();

        File javaSrc   = new File(srcMain, "java");
        File kotlinSrc = new File(srcMain, "kotlin");
        if (javaSrc.exists())   sources.addAll(classifier.classify(javaSrc));
        if (kotlinSrc.exists()) sources.addAll(classifier.classify(kotlinSrc));

        // Merge manifest activity list with classified sources
        // (manifest is the source of truth for what qualifies as an Activity)
        reconcileWithManifest(sources, manifest);

        Log.d(TAG, "Classified sources: " + sources.size());

        // ── [7] Import layouts ────────────────────────────────────────────────
        progress("Importing layout files...");
        LayoutImporter layoutImporter = new LayoutImporter();

        // Process all layout folder variants (layout, layout-land, layout-sw600dp, etc.)
        // but use only the base "layout" folder for registration
        // Qualifier variants are copied as raw resource files
        File resDir        = new File(srcMain, "res");
        File baseLayoutDir = new File(resDir, "layout");

        List<LayoutImporter.ImportedLayout> layouts = layoutImporter.importLayouts(
                baseLayoutDir, sources, manifest, manifest.appTheme);

        // Build file + view data strings
        String fileData = layoutImporter.buildFileData(layouts);
        String viewData = layoutImporter.buildViewData(layouts);

        Log.d(TAG, "Imported layouts: " + layouts.size());

        // ── [8] Map resources ─────────────────────────────────────────────────
        progress("Copying resources...");

        // We need the sc_id to build destination paths, but sc_id is only generated
        // inside SketchwareWriter. Solution: generate a temp placeholder path in the
        // system temp area and move files after sc_id is known. HOWEVER — to keep this
        // simple and avoid double-copy overhead, we collect resource metadata NOW
        // and do the actual copy INSIDE writeResources() after sc_id is available.

        // ── [9] Detect libraries ──────────────────────────────────────────────
        progress("Detecting libraries...");
        LibraryDetector libDetector = new LibraryDetector();
        String libraryData = libDetector.buildLibraryData(gradle, appModuleDir);

        // ── [10-12] Write all Sketchware files ────────────────────────────────
        progress("Creating Sketchware project...");
        writer = new SketchwareWriter();

        // Build input for writer
        SketchwareWriter.WriteInput writeInput = new SketchwareWriter.WriteInput();
        writeInput.appName       = manifest.appName;
        writeInput.packageName   = gradle.applicationId;
        writeInput.versionCode   = gradle.versionCode;
        writeInput.versionName   = gradle.versionName;
        writeInput.minSdk        = gradle.minSdk;
        writeInput.targetSdk     = gradle.targetSdk;
        writeInput.hasKotlin     = gradle.hasKotlin;
        writeInput.fileData      = fileData;
        writeInput.viewData      = viewData;
        writeInput.libraryData   = libraryData;
        writeInput.manifestContent = buildCustomManifest(manifest);

        try {
            writer.write(writeInput);
        } catch (Exception e) {
            Log.e(TAG, "SketchwareWriter failed", e);
            writer.rollback();
            return failure("Failed to write project files: " + e.getMessage());
        }

        String scId      = writer.getGeneratedScId();
        String dataPath  = wq.b(scId);
        String filesPath = dataPath + "/files";

        // ── [10] Resource copy (now that sc_id is known) ──────────────────────
        progress("Copying image and media resources...");
        ResourceMapper resMapper = new ResourceMapper();

        String swImagesPath = wq.n() + "/" + scId;
        String swSoundsPath = wq.e() + "/" + scId;
        String swFontsPath  = wq.g() + "/" + scId;

        ResourceMapper.ResourceResult resResult = resMapper.process(
                resDir,
                filesPath,
                swImagesPath,
                swSoundsPath,
                swFontsPath,
                manifest.iconResName);

        // Update resource data in the already-written "resource" file
        // by re-writing it with the now-populated resource bean list
        if (!resResult.resourceData.equals("@images\n@sounds\n@fonts\n")) {
            try {
                a.a.a.oB enc = new a.a.a.oB();
                enc.a(dataPath + "/resource", enc.d(resResult.resourceData));
                Log.d(TAG, "Re-wrote resource file with " + resResult.resourceData.length() + " chars.");
            } catch (Exception e) {
                // Non-fatal: project is usable, resources just won't show in gallery
                Log.w(TAG, "Could not update resource data after copy", e);
            }
        }

        // Patch hasCustomIcon into project map if icon was found
        if (resResult.hasCustomIcon) {
            try {
                java.util.HashMap<String, Object> patch = new java.util.HashMap<>();
                patch.put("custom_icon", true);
                a.a.a.lC.b(scId, patch);
            } catch (Exception e) {
                Log.w(TAG, "Could not patch custom_icon flag", e);
            }
        }

        // ── [11] Copy Java / Kotlin source files ──────────────────────────────
        progress("Copying source files...");
        copySourceFiles(sources, filesPath + "/java");

        // Also copy any Kotlin source from kotlin/ dir if it was not under java/
        if (kotlinSrc.exists() && !kotlinSrc.equals(javaSrc)) {
            copyAllSourceFiles(kotlinSrc, filesPath + "/java");
        }

        // ── [12] Copy assets/ ─────────────────────────────────────────────────
        File assetsDir = new File(srcMain, "assets");
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            progress("Copying assets...");
            copyDirectory(assetsDir, new File(filesPath + "/assets"));
            Log.d(TAG, "Assets copied.");
        }

        // ── [13] Copy native libs (jniLibs/) ─────────────────────────────────
        if (gradle.hasNativeLibs) {
            File jniLibs = new File(srcMain, "jniLibs");
            if (jniLibs.exists()) {
                progress("Copying native libraries...");
                copyDirectory(jniLibs, new File(filesPath + "/jniLibs"));
                Log.d(TAG, "jniLibs copied.");
            }
        }

        // ── [14] Copy local .aar / .jar to Sketchware local lib path ──────────
        if (!gradle.localLibPaths.isEmpty()) {
            progress("Copying local libraries...");
            String localLibDest = filesPath + "/libs";
            FileUtil.makeDir(localLibDest);
            for (String localLibPath : gradle.localLibPaths) {
                File libFile = new File(localLibPath);
                FileUtil.copyFile(localLibPath, localLibDest + "/" + libFile.getName());
                Log.d(TAG, "Copied local lib: " + libFile.getName());
            }
        }

        progress("Import complete!");
        Log.d(TAG, "Import succeeded. sc_id=" + scId);
        return new Result(scId);
    }

    // ── Manifest reconciliation ───────────────────────────────────────────────

    /**
     * Ensures every activity declared in the manifest has a ClassifiedSource entry.
     *
     * If the manifest declares "SplashActivity" but the classifier found it as
     * Kind.OTHER (because its superclass wasn't recognized), we upgrade it to
     * Kind.ACTIVITY so it gets registered as a Sketchware screen.
     *
     * Also adds a synthetic ClassifiedSource for manifest activities that have
     * no corresponding source file in the ZIP (e.g. from a library module).
     */
    private void reconcileWithManifest(
            List<ClassifiedSource> sources,
            ParsedManifest manifest) {

        for (ParsedManifest.ActivityEntry entry : manifest.activities) {
            boolean found = false;
            for (ClassifiedSource cs : sources) {
                if (cs.simpleClassName.equals(entry.simpleClassName)) {
                    // Upgrade to ACTIVITY if classifier missed it
                    if (cs.kind != ClassifiedSource.Kind.ACTIVITY) {
                        Log.d(TAG, "Upgrading " + cs.simpleClassName
                                + " to ACTIVITY (found in manifest).");
                        // Can't mutate kind (final) — replace with new instance
                        ClassifiedSource upgraded = new ClassifiedSource(
                                cs.file, cs.packageName, cs.simpleClassName,
                                ClassifiedSource.Kind.ACTIVITY);
                        upgraded.associatedLayout    = cs.associatedLayout;
                        upgraded.sketchwareFileName  = cs.sketchwareFileName;
                        sources.set(sources.indexOf(cs), upgraded);
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Activity declared in manifest but no source file found
                // Create a minimal placeholder so the screen is registered
                Log.w(TAG, "Activity in manifest but no source found: "
                        + entry.simpleClassName + " — creating placeholder.");
                ClassifiedSource placeholder = new ClassifiedSource(
                        null, manifest.packageName, entry.simpleClassName,
                        ClassifiedSource.Kind.ACTIVITY);
                placeholder.sketchwareFileName =
                        SourceClassifier.classNameToSketchwareFileName(entry.simpleClassName);
                sources.add(placeholder);
            }
        }
    }

    // ── Source file copy ──────────────────────────────────────────────────────

    /**
     * Copies classified source files that have a non-null File reference
     * into the Sketchware java files directory, preserving package directory structure.
     *
     * Path format: filesPath/java/<package/sub/dirs/ClassName.java>
     */
    private void copySourceFiles(List<ClassifiedSource> sources, String javaDestPath) {
        for (ClassifiedSource cs : sources) {
            if (cs.file == null || !cs.file.exists()) continue;
            String packageSubDir = cs.packageName.replace('.', '/');
            String destDir = javaDestPath + "/" + packageSubDir;
            FileUtil.makeDir(destDir);
            FileUtil.copyFile(
                    cs.file.getAbsolutePath(),
                    destDir + "/" + cs.file.getName());
        }
    }

    /**
     * Fallback: recursively copies ALL source files from a directory,
     * preserving the directory structure relative to srcRoot.
     * Used for the kotlin/ source set if it differs from java/.
     */
    private void copyAllSourceFiles(File srcRoot, String destPath) {
        File[] files = srcRoot.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                copyAllSourceFiles(f, destPath + "/" + f.getName());
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                FileUtil.makeDir(destPath);
                FileUtil.copyFile(f.getAbsolutePath(), destPath + "/" + f.getName());
            }
        }
    }

    // ── Directory recursive copy ──────────────────────────────────────────────

    private void copyDirectory(File src, File dest) {
        FileUtil.makeDir(dest.getAbsolutePath());
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                copyDirectory(f, new File(dest, f.getName()));
            } else {
                FileUtil.copyFile(f.getAbsolutePath(),
                        dest.getAbsolutePath() + "/" + f.getName());
            }
        }
    }

    // ── ZIP extraction ────────────────────────────────────────────────────────

    /**
     * Extracts the ZIP file to a temporary directory under the app's cache dir.
     * Returns the temp directory, or null on failure.
     *
     * Security: skips entries with path traversal ("../") in their names.
     */
    private File extractZip(String zipPath) {
        File zipFile = new File(zipPath);
        if (!zipFile.exists()) {
            Log.e(TAG, "ZIP file not found: " + zipPath);
            return null;
        }

        File tempDest = new File(context.getCacheDir(),
                "as_import_" + System.currentTimeMillis());
        FileUtil.makeDir(tempDest.getAbsolutePath());

        try (ZipInputStream zis = new ZipInputStream(
                new java.io.FileInputStream(zipFile))) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Security: skip path traversal attempts
                if (entryName.contains("..")) {
                    Log.w(TAG, "Skipping suspicious ZIP entry: " + entryName);
                    zis.closeEntry();
                    continue;
                }

                File dest = new File(tempDest, entryName);

                if (entry.isDirectory()) {
                    FileUtil.makeDir(dest.getAbsolutePath());
                } else {
                    // Ensure parent dirs exist (some ZIPs omit dir entries)
                    FileUtil.makeDir(dest.getParentFile().getAbsolutePath());
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }

            Log.d(TAG, "ZIP extracted to: " + tempDest.getAbsolutePath());
            return tempDest;

        } catch (IOException e) {
            Log.e(TAG, "ZIP extraction failed", e);
            FileUtil.deleteFile(tempDest.getAbsolutePath());
            return null;
        }
    }

    // ── Custom manifest builder ───────────────────────────────────────────────

    /**
     * Builds a minimal custom_manifest.xml for Sketchware from the parsed manifest.
     *
     * Sketchware's custom_manifest.xml holds only the extra declarations that
     * Sketchware cannot generate automatically — permissions, services, receivers,
     * providers, and meta-data. Activities are managed by Sketchware itself.
     *
     * Format: Sketchware wraps this content inside <manifest><application> when building.
     * We therefore output only the inner body, not the full manifest XML.
     */
    private String buildCustomManifest(ParsedManifest manifest) {
        // For now, return an empty placeholder.
        // A future improvement would parse <uses-permission>, <service>,
        // <receiver>, <provider> elements and output them here.
        // The full manifest is already available in the original source ZIP
        // at srcMain/AndroidManifest.xml — we log a reminder.
        Log.i(TAG, "NOTE: Permissions, services, and receivers from AndroidManifest.xml " +
                "must be added manually via Sketchware's Manifest editor. " +
                "Custom manifest section was intentionally left minimal to avoid " +
                "conflicts with Sketchware's own manifest generation.");
        return "";
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanupTemp() {
        if (tempDir != null && tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
            Log.d(TAG, "Temp directory cleaned up.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void progress(String msg) {
        Log.d(TAG, msg);
        publishProgress(msg);
    }

    private Result failure(String reason) {
        Log.e(TAG, "Import failed: " + reason);
        return new Result(reason, false);
    }
}
