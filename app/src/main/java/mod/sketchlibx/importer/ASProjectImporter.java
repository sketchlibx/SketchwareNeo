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
 * [1]  Extract ZIP to temp directory
 * [2]  Detect project root + app module   (ModuleDetector)
 * [3]  Validate structure                 (ImportValidator)
 * [4]  Parse AndroidManifest.xml          (ManifestParser)
 * [5]  Parse build.gradle / kts           (GradleParser)
 * [6]  Classify source files              (SourceClassifier)
 * [7]  Import layout XML → view/file data (LayoutImporter)
 * [8]  Map resources                      (ResourceMapper)
 * [9]  Detect libraries                   (LibraryDetector)
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
 *     public void onProgress(String message)       { ... }
 *     public void onSuccess(String scId)           { ... }
 *     public void onFailure(String reason)         { ... }
 * }).execute();
 *
 * For verbose debug logging, use the three-argument showPicker() which
 * prompts the user before starting. Alternatively construct directly:
 * new ASProjectImporter(context, zipPath, ImportLogger.create(true), callback).execute();
 */
public class ASProjectImporter extends AsyncTask<Void, String, ASProjectImporter.Result> {

    // ── Version ───────────────────────────────────────────────────────────────

    /** Bumped here so the verbose log header always shows the correct version. */
    static final String IMPORTER_VERSION = "1.0.0";

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
        final String  scId;
        final String  failReason;

        Result(String scId)          { this.success = true;  this.scId = scId; this.failReason = null; }
        Result(String r, boolean f)  { this.success = false; this.scId = null; this.failReason = r; }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Context      context;
    private final String       zipFilePath;
    private final Callback     callback;
    private final ImportLogger logger;       // never null — disabled logger has zero overhead

    /** Temp directory where ZIP is extracted. Deleted after import regardless of outcome. */
    private File tempDir;

    /** Writer instance kept so rollback() can be called on failure. */
    private SketchwareWriter writer;

    // ── Static Helper: File Picker & UI ───────────────────────────────────────

    /**
     * Shows the file picker. After the user selects a ZIP, shows the
     * "Enable Verbose Logging?" dialog before starting the import.
     *
     * The pipeline behaviour is completely unaffected by logging — only
     * diagnostic output is added.
     */
    public static void showPicker(Activity activity, ProjectsFragment fragment) {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"zip"});
        options.setTitle("Select AS Project (.zip)");

        FilePickerCallback fileCallback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                // Ask the developer whether to enable verbose logging.
                // Either choice leads to the same import pipeline.
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Enable Verbose Logging?")
                        .setMessage("Verbose logging records every import step. " +
                                "Useful for debugging failed imports. Disable for normal use.")
                        .setPositiveButton("Enable", (d, w) ->
                                startImport(activity, fragment, file, ImportLogger.create(true)))
                        .setNegativeButton("Skip", (d, w) ->
                                startImport(activity, fragment, file, ImportLogger.create(false)))
                        .setCancelable(false)
                        .show();
            }
        };

        new FilePickerDialogFragment(options, fileCallback)
                .show(fragment.getChildFragmentManager(), "filePicker");
    }

    /**
     * Internal: boots the import after the verbose-logging decision is made.
     * Exists only to avoid duplicating the progress-dialog + callback wiring.
     */
    private static void startImport(Activity activity, ProjectsFragment fragment,
                                    File zipFile, ImportLogger log) {

        ProgressMsgBoxBinding binding = ProgressMsgBoxBinding.inflate(activity.getLayoutInflater());
        binding.tvProgress.setText("Initializing Importer...");

        AlertDialog loadingDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Importing Android Studio Project")
                .setCancelable(false)
                .setView(binding.getRoot())
                .create();
        loadingDialog.show();

        new ASProjectImporter(activity, zipFile.getAbsolutePath(), log, new Callback() {
            @Override
            public void onProgress(String message) {
                binding.tvProgress.setText(message);
            }

            @Override
            public void onSuccess(String scId) {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                SketchwareUtil.toast("AS Project Imported Successfully!");
                if (fragment != null) fragment.refreshProjectsList();
                // Show the log viewer dialog if verbose mode was enabled.
                if (log.isEnabled()) {
                    ImportLogger.showViewer(activity, log);
                }
            }

            @Override
            public void onFailure(String reason) {
                if (loadingDialog.isShowing()) loadingDialog.dismiss();
                SketchwareUtil.toastError("Import Failed: " + reason, Toast.LENGTH_LONG);
                // Show the log even on failure — that's when it's most useful.
                if (log.isEnabled()) {
                    ImportLogger.showViewer(activity, log);
                }
            }
        }).execute();
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Original constructor. Verbose logging is disabled. Existing callers unaffected. */
    public ASProjectImporter(Context context, String zipFilePath, Callback callback) {
        this(context, zipFilePath, ImportLogger.create(false), callback);
    }

    /** Full constructor — pass a configured {@link ImportLogger} for verbose mode. */
    public ASProjectImporter(Context context, String zipFilePath,
                             ImportLogger logger, Callback callback) {
        this.context     = context.getApplicationContext();
        this.zipFilePath = zipFilePath;
        this.callback    = callback;
        this.logger      = logger;
    }

    // ── AsyncTask lifecycle ───────────────────────────────────────────────────

    @Override
    protected Result doInBackground(Void... voids) {
        // Open the logger session here, on the background thread, so timing is
        // accurate from the very first operation.
        logger.start("AS Import", context, zipFilePath, IMPORTER_VERSION);
        try {
            return runPipeline();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in import pipeline", e);
            logger.error("Unexpected error in import pipeline", e);
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
        // Close the logger session and write the summary block (background thread
        // has already finished so there is no race).
        if (result.success) {
            logger.finish(result.scId, "", true);
        } else {
            logger.finish("", result.failReason != null ? result.failReason : "", false);
        }

        if (callback == null) return;
        if (result.success) callback.onSuccess(result.scId);
        else                callback.onFailure(result.failReason);
    }

    // ── Pipeline ──────────────────────────────────────────────────────────────

    private Result runPipeline() throws Exception {

        // ── [1] Extract ZIP ───────────────────────────────────────────────────
        progress("Extracting project ZIP...");
        logger.step(1, "Extract ZIP");
        File zipFile = new File(zipFilePath);
        logger.info("Source : " + zipFilePath);
        logger.info("Size   : " + formatFileSize(zipFile.length()));

        tempDir = extractZip(zipFilePath);
        if (tempDir == null) {
            logger.error("Failed to extract ZIP file.", null,
                    new ImportLogger.ErrorContext().file(zipFilePath));
            logger.stepDone(false);
            return failure("Failed to extract ZIP file. Ensure the file is a valid .zip archive.");
        }
        int entryCount = countFiles(tempDir);
        logger.info("Extracted entries : " + entryCount);
        logger.info("Temp dir          : " + tempDir.getAbsolutePath());
        logger.stepDone(true);

        // ── [2] Detect module ─────────────────────────────────────────────────
        progress("Detecting project structure...");
        logger.step(2, "Detect Module");
        ModuleDetector detector = new ModuleDetector();
        ModuleDetector.DetectionResult detection = detector.detect(tempDir);
        if (detection == null) {
            logger.error("Cannot detect Android project structure.", null, null);
            logger.stepDone(false);
            return failure("Cannot detect Android project structure. " +
                    "Ensure the ZIP contains a valid Android Studio project.");
        }
        logger.info("Project root : " + detection.projectRoot.getAbsolutePath());
        logger.info("App module   : " + detection.appModuleDir.getAbsolutePath());
        logger.stepDone(true);
        Log.d(TAG, "Module dir: " + detection.appModuleDir.getAbsolutePath());

        File appModuleDir = detection.appModuleDir;
        File srcMain      = new File(appModuleDir, "src/main");

        // ── [3] Validate ──────────────────────────────────────────────────────
        progress("Validating project structure...");
        logger.step(3, "Validate");
        ImportValidator validator = new ImportValidator();
        if (!validator.validate(detection.projectRoot, appModuleDir)) {
            logger.error(validator.getFailReason(), null,
                    new ImportLogger.ErrorContext().module(appModuleDir.getName()));
            logger.stepDone(false);
            return failure(validator.getFailReason());
        }
        logger.info("AndroidManifest.xml \u2714");
        logger.info("build.gradle        \u2714");
        logger.info("java sources        \u2714");
        logger.stepDone(true);

        // ── [4] Parse manifest ────────────────────────────────────────────────
        progress("Parsing AndroidManifest.xml...");
        logger.step(4, "Parse AndroidManifest.xml");
        ManifestParser manifestParser = new ManifestParser(srcMain);
        ParsedManifest manifest = manifestParser.parse();
        logger.info("Package    : " + manifest.packageName);
        logger.info("App Name   : " + manifest.appName);
        logger.info("Activities : " + manifest.activities.size());
        logger.info("Icon Res   : " + manifest.iconResName);
        logger.info("App Theme  : " + (manifest.appTheme.isEmpty() ? "(none)" : manifest.appTheme));
        for (ParsedManifest.ActivityEntry ae : manifest.activities) {
            logger.info("  \u2192 " + ae.simpleClassName
                    + (ae.isLauncher ? "  (launcher)" : ""));
        }
        logger.stepDone(true);
        Log.d(TAG, "Package: " + manifest.packageName
                + ", Activities: " + manifest.activities.size());

        // ── [5] Parse gradle ──────────────────────────────────────────────────
        progress("Parsing build configuration...");
        logger.step(5, "Parse build.gradle");
        GradleParser gradleParser = new GradleParser();
        ParsedGradle gradle = gradleParser.parse(appModuleDir);

        if ("com.imported.project".equals(gradle.applicationId)
                && !manifest.packageName.isEmpty()) {
            gradle.applicationId = manifest.packageName;
            logger.warning("applicationId not found in gradle — using manifest package: "
                    + manifest.packageName);
        }
        logger.info("Application ID  : " + gradle.applicationId);
        logger.info("Version         : " + gradle.versionCode + " / " + gradle.versionName);
        logger.info("Min SDK         : " + gradle.minSdk);
        logger.info("Target SDK      : " + gradle.targetSdk);
        logger.info("Kotlin          : " + gradle.hasKotlin);
        logger.info("Firebase        : " + gradle.hasFirebase);
        logger.info("AdMob           : " + gradle.hasAdMob);
        logger.info("Google Maps     : " + gradle.hasGoogleMaps);
        logger.info("Native Libs     : " + gradle.hasNativeLibs);
        logger.info("Local Libs      : " + gradle.localLibPaths.size());
        logger.stepDone(true);

        // ── [6] Classify source files ─────────────────────────────────────────
        progress("Classifying source files...");
        logger.step(6, "Classify Source Files");
        SourceClassifier classifier = new SourceClassifier();
        List<ClassifiedSource> sources = new ArrayList<>();

        File javaSrc   = new File(srcMain, "java");
        File kotlinSrc = new File(srcMain, "kotlin");
        logger.info("java/ dir exists   : " + javaSrc.exists());
        logger.info("kotlin/ dir exists : " + kotlinSrc.exists());
        if (javaSrc.exists())   sources.addAll(classifier.classify(javaSrc));
        if (kotlinSrc.exists()) sources.addAll(classifier.classify(kotlinSrc));

        reconcileWithManifest(sources, manifest);
        Log.d(TAG, "Classified sources: " + sources.size());

        // Build the stats object — populated incrementally across remaining steps.
        ImportLogger.FileStats stats = new ImportLogger.FileStats();
        for (ClassifiedSource cs : sources) {
            switch (cs.kind) {
                case ACTIVITY:    stats.activities++;    break;
                case FRAGMENT:    stats.fragments++;     break;
                case CUSTOM_VIEW: stats.customViews++;   break;
                case SERVICE:     stats.services++;      break;
                case RECEIVER:    stats.receivers++;     break;
                case PROVIDER:    stats.providers++;     break;
                case APPLICATION: stats.applicationClasses++; break;
                default:                                 break;
            }
            if (cs.file != null) {
                if (cs.file.getName().endsWith(".java")) stats.javaFiles++;
                else if (cs.file.getName().endsWith(".kt")) stats.kotlinFiles++;
            }
        }
        logger.info("Total classified : " + sources.size());
        logger.info("Activities       : " + stats.activities);
        logger.info("Fragments        : " + stats.fragments);
        logger.info("Custom Views     : " + stats.customViews);
        logger.info("Services         : " + stats.services);
        logger.info("Receivers        : " + stats.receivers);
        logger.info("Providers        : " + stats.providers);
        logger.info("Application class: " + stats.applicationClasses);
        logger.info("Java files       : " + stats.javaFiles);
        logger.info("Kotlin files     : " + stats.kotlinFiles);
        for (ClassifiedSource cs : sources) {
            logger.info("  " + cs.kind.name().toLowerCase()
                    + "  " + cs.simpleClassName
                    + (cs.associatedLayout != null ? "  [layout: " + cs.associatedLayout + "]" : ""));

            if (cs.kind == ClassifiedSource.Kind.SERVICE || cs.kind == ClassifiedSource.Kind.RECEIVER) {
                logger.warning(cs.kind.name() + " detected and copied as Java: " + cs.simpleClassName
                        + " — its <" + (cs.kind == ClassifiedSource.Kind.SERVICE ? "service" : "receiver")
                        + "> entry (exported flag, permission, intent-filters) is NOT auto-registered yet"
                        + " and must be added manually in Manifest Manager.");
            } else if (cs.kind == ClassifiedSource.Kind.PROVIDER) {
                logger.warning("PROVIDER detected and copied as Java: " + cs.simpleClassName
                        + " — Sketchware Neo has no dedicated provider-registration list;"
                        + " its <provider> entry must be added manually.");
            } else if (cs.kind == ClassifiedSource.Kind.APPLICATION) {
                logger.warning("Custom Application class detected: " + cs.simpleClassName
                        + " — copied as Java only. Sketchware Neo's own Application class is fixed and was"
                        + " NOT replaced; merge any needed logic from this class manually.");
            }
        }
        logger.stepDone(true);

        // ── [7] Import layouts ────────────────────────────────────────────────
        progress("Importing layout files...");
        logger.step(7, "Import Layouts");
        LayoutImporter layoutImporter = new LayoutImporter();
        File resDir        = new File(srcMain, "res");
        File baseLayoutDir = new File(resDir, "layout");

        logger.info("res dir   : " + resDir.getAbsolutePath());
        logger.info("layout dir: " + baseLayoutDir.getAbsolutePath()
                + (baseLayoutDir.exists() ? "" : "  [NOT FOUND]"));

        List<LayoutImporter.ImportedLayout> layouts =
                layoutImporter.importLayouts(baseLayoutDir, sources, manifest, manifest.appTheme);
        String fileData = layoutImporter.buildFileData(layouts);
        String viewData = layoutImporter.buildViewData(layouts);

        stats.layouts = layouts.size();
        logger.info("Layouts imported : " + layouts.size());
        for (LayoutImporter.ImportedLayout il : layouts) {
            logger.info("  " + il.sketchwareFileName
                    + "  (fileType=" + il.fileType + ")");
        }
        logger.stepDone(true);
        Log.d(TAG, "Imported layouts: " + layouts.size());

        // ── [8] Detect libraries ──────────────────────────────────────────────
        progress("Detecting libraries...");
        logger.step(9, "Detect Libraries");
        LibraryDetector libDetector = new LibraryDetector();
        String libraryData = libDetector.buildLibraryData(gradle, appModuleDir);

        int libCount = 0;
        if (gradle.hasFirebase)   { libCount++; logger.info("Firebase        : detected"); }
        logger.info("AppCompat       : always enabled");  libCount++;
        if (gradle.hasAdMob)      { libCount++; logger.info("AdMob           : detected"); }
        if (gradle.hasGoogleMaps) { libCount++; logger.info("Google Maps     : detected"); }
        if (!gradle.localLibPaths.isEmpty()) {
            libCount += gradle.localLibPaths.size();
            logger.info("Local libs      : " + gradle.localLibPaths.size());
            for (String lp : gradle.localLibPaths) logger.info("  " + new File(lp).getName());
        }
        stats.libraries = libCount;
        logger.stepDone(true);

        // ── [9] Write all Sketchware files ────────────────────────────────────
        progress("Creating Sketchware project...");
        logger.step(10, "Write Sketchware Project Files");
        writer = new SketchwareWriter();
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
            logger.error("SketchwareWriter.write() failed", e,
                    new ImportLogger.ErrorContext().module(appModuleDir.getName()));
            writer.rollback();
            logger.stepDone(false);
            return failure("Failed to write project files: " + e.getMessage());
        }

        String scId      = writer.getGeneratedScId();
        String dataPath  = wq.b(scId);
        String filesPath = dataPath + "/files";
        logger.info("Generated sc_id : " + scId);
        logger.info("Data path       : " + dataPath);
        logger.stepDone(true);

        // ── [10] Resource copy ────────────────────────────────────────────────
        progress("Copying image and media resources...");
        logger.step(8, "Map Resources");
        logger.info("res dir : " + resDir.getAbsolutePath());

        ResourceMapper resMapper = new ResourceMapper();
        String swImagesPath = wq.n() + "/" + scId;
        String swSoundsPath = wq.e() + "/" + scId;
        String swFontsPath  = wq.g() + "/" + scId;

        ResourceMapper.ResourceResult resResult = resMapper.process(
                resDir, filesPath, swImagesPath, swSoundsPath, swFontsPath,
                manifest.iconResName);

        stats.drawables = countFilesInPrefixedDirs(resDir, "drawable", "mipmap");
        stats.fonts     = countFilesInPrefixedDirs(resDir, "font");
        logger.info("Drawables copied : " + stats.drawables);
        logger.info("Fonts copied     : " + stats.fonts);
        logger.info("Custom icon      : " + resResult.hasCustomIcon);

        if (!resResult.resourceData.equals("@images\n@sounds\n@fonts\n")) {
            try {
                a.a.a.oB enc = new a.a.a.oB();
                enc.a(dataPath + "/resource", enc.d(resResult.resourceData));
                logger.info("Resource data file updated.");
                Log.d(TAG, "Re-wrote resource file with " + resResult.resourceData.length() + " chars.");
            } catch (Exception e) {
                Log.w(TAG, "Could not update resource data after copy", e);
                logger.warning("Could not update resource data file: " + e.getMessage());
            }
        }
        if (resResult.hasCustomIcon) {
            try {
                java.util.HashMap<String, Object> patch = new java.util.HashMap<>();
                patch.put("custom_icon", true);
                a.a.a.lC.b(scId, patch);
                logger.info("custom_icon flag patched in project map.");
            } catch (Exception e) {
                Log.w(TAG, "Could not patch custom_icon flag", e);
                logger.warning("Could not patch custom_icon flag: " + e.getMessage());
            }
        }
        logger.stepDone(true);

        // ── [11] Copy Java / Kotlin source files ──────────────────────────────
        progress("Copying source files...");
        logger.step(11, "Copy Source Files");
        copySourceFiles(sources, filesPath + "/java");
        if (kotlinSrc.exists() && !kotlinSrc.equals(javaSrc)) {
            copyAllSourceFiles(kotlinSrc, filesPath + "/java");
            logger.info("Kotlin source directory also copied.");
        }
        int copiedSources = 0;
        for (ClassifiedSource cs : sources) if (cs.file != null && cs.file.exists()) copiedSources++;
        logger.info("Source files copied : " + copiedSources);
        logger.stepDone(true);
        Log.d(TAG, "Source files copied: " + copiedSources);

        // ── [12] Copy assets/ ─────────────────────────────────────────────────
        logger.step(12, "Copy Assets");
        File assetsDir = new File(srcMain, "assets");
        if (assetsDir.exists() && assetsDir.isDirectory()) {
            progress("Copying assets...");
            copyDirectory(assetsDir, new File(filesPath + "/assets"));
            stats.assets = countFiles(assetsDir);
            logger.info("Assets copied : " + stats.assets);
            logger.stepDone(true);
            Log.d(TAG, "Assets copied.");
        } else {
            logger.stepSkipped("no assets directory");
        }

        // ── [13] Copy native libraries + source-level JNI ────────────────────
        logger.step(13, "Copy Native Libraries and JNI Source");
        if (gradle.hasNativeLibs) {
            boolean anyNativeContentCopied = false;

            // Prebuilt .so files, preserving ABI folders (arm64-v8a, armeabi-v7a, etc).
            // NOTE: target folder name follows the spec (files/native_libs/), which
            // differs from this importer's previous "jniLibs" naming — flagged as a
            // naming mismatch against Sketchware Neo's own convention; verify before
            // relying on this if native libs still don't load after import.
            File jniLibsSrc = new File(srcMain, "jniLibs");
            if (jniLibsSrc.exists()) {
                progress("Copying prebuilt native libraries...");
                copyDirectory(jniLibsSrc, new File(filesPath + "/native_libs"));
                stats.jniLibs = countFiles(jniLibsSrc);
                logger.info("Prebuilt native libs copied : " + stats.jniLibs);
                anyNativeContentCopied = true;
            } else {
                logger.info("No prebuilt jniLibs/ directory (expected if this module builds from source).");
            }

            // Source-level JNI: only copy actual source, never build/.cxx/intermediates output.
            File jniSourceDir = locateJniSourceDir(srcMain);
            if (jniSourceDir != null) {
                progress("Copying JNI source (" + gradle.nativeBuildSystem + ")...");
                File jniDest = new File(filesPath + "/jni");
                copyJniSourceOnly(jniSourceDir, jniDest);
                int jniSourceFileCount = countJniSourceFiles(jniSourceDir);
                logger.info("JNI source files copied : " + jniSourceFileCount
                        + " (" + gradle.nativeBuildSystem + ") from " + jniSourceDir.getAbsolutePath());
                anyNativeContentCopied = true;

                if (gradle.nativeBuildSystem == ParsedGradle.NativeBuildSystem.NDK_BUILD) {
                    logger.warning("This module uses Android.mk/ndk-build. Sketchware Neo's C/C++ Manager"
                            + " is CMake-based — Android.mk was copied as reference but will need to be"
                            + " ported to a CMakeLists.txt manually before it will compile.");
                }
            } else {
                logger.info("No JNI source directory found (expected if this module only ships prebuilt .so files).");
            }

            if (anyNativeContentCopied) {
                logger.stepDone(true);
            } else {
                logger.warning("hasNativeLibs=true but neither jniLibs/ nor a JNI source directory was found.");
                logger.stepSkipped("no native content found");
            }
        } else {
            logger.stepSkipped("no native libraries declared");
        }

        // ── [14] Copy local .aar / .jar ───────────────────────────────────────
        logger.step(14, "Copy Local Libraries");
        if (!gradle.localLibPaths.isEmpty()) {
            progress("Copying local libraries...");
            String localLibDest = filesPath + "/libs";
            FileUtil.makeDir(localLibDest);
            for (String localLibPath : gradle.localLibPaths) {
                File libFile = new File(localLibPath);
                FileUtil.copyFile(localLibPath, localLibDest + "/" + libFile.getName());
                logger.info("Copied : " + libFile.getName());
                Log.d(TAG, "Copied local lib: " + libFile.getName());
            }
            logger.stepDone(true);
        } else {
            logger.stepSkipped("no local .aar / .jar libraries");
        }

        // ── [15] Cleanup is handled by finally block in doInBackground ─────────
        logger.step(15, "Cleanup Temp Directory");
        // Actual deletion happens in cleanupTemp() → finally block.
        // We pre-log here so the step appears in order; stepDone is not called
        // for cleanup since it runs in finally and the logger may be finishing.
        logger.info("Temp dir will be deleted in finally block.");

        // ── File statistics block ─────────────────────────────────────────────
        logger.stats(stats);

        progress("Import complete!");
        Log.d(TAG, "Import succeeded. sc_id=" + scId);
        return new Result(scId);
    }

    // ── Manifest reconciliation ───────────────────────────────────────────────

    /**
     * Ensures every activity declared in the manifest has a ClassifiedSource entry.
     * (Same logic as original — no behaviour change.)
     */
    private void reconcileWithManifest(List<ClassifiedSource> sources,
                                       ParsedManifest manifest) {
        for (ParsedManifest.ActivityEntry entry : manifest.activities) {
            boolean found = false;
            for (ClassifiedSource cs : sources) {
                if (cs.simpleClassName.equals(entry.simpleClassName)) {
                    if (cs.kind != ClassifiedSource.Kind.ACTIVITY) {
                        Log.d(TAG, "Upgrading " + cs.simpleClassName
                                + " to ACTIVITY (found in manifest).");
                        logger.info("Upgraded to ACTIVITY (manifest): " + cs.simpleClassName);
                        ClassifiedSource upgraded = new ClassifiedSource(
                                cs.file, cs.packageName, cs.simpleClassName,
                                ClassifiedSource.Kind.ACTIVITY);
                        upgraded.associatedLayout   = cs.associatedLayout;
                        upgraded.sketchwareFileName = cs.sketchwareFileName;
                        sources.set(sources.indexOf(cs), upgraded);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                Log.w(TAG, "Activity in manifest but no source found: "
                        + entry.simpleClassName + " — creating placeholder.");
                logger.warning("No source for manifest activity — placeholder created: "
                        + entry.simpleClassName);
                ClassifiedSource placeholder = new ClassifiedSource(
                        null, manifest.packageName, entry.simpleClassName,
                        ClassifiedSource.Kind.ACTIVITY);
                placeholder.sketchwareFileName =
                        SourceClassifier.classNameToSketchwareFileName(entry.simpleClassName);
                sources.add(placeholder);
            }
        }

        reconcileComponents(sources, manifest.services, ClassifiedSource.Kind.SERVICE, "service");
        reconcileComponents(sources, manifest.receivers, ClassifiedSource.Kind.RECEIVER, "receiver");
        reconcileComponents(sources, manifest.providers, ClassifiedSource.Kind.PROVIDER, "provider");

        if (!manifest.permissions.isEmpty()) {
            logger.info("Permissions declared in manifest: " + manifest.permissions.size());
            for (String permission : manifest.permissions) {
                logger.info("  uses-permission: " + permission);
            }
            logger.warning(manifest.permissions.size() + " permission(s) found in manifest — "
                    + "must be added manually in Manifest Manager (no auto-registration path yet).");
        }

        if (manifest.applicationClassName != null) {
            logger.info("Custom Application class in manifest: " + manifest.applicationClassName);
        }
    }

    /**
     * Cross-checks a manifest component list (service/receiver/provider) against
     * classified sources, attaching real exported/permission/authorities data
     * where the Java class was found, and warning when a manifest entry has no
     * matching source (can't be copied — only reported).
     */
    private void reconcileComponents(List<ClassifiedSource> sources,
                                      List<ParsedManifest.ComponentEntry> manifestEntries,
                                      ClassifiedSource.Kind expectedKind,
                                      String tagLabel) {
        for (ParsedManifest.ComponentEntry entry : manifestEntries) {
            boolean found = false;
            for (ClassifiedSource cs : sources) {
                if (cs.simpleClassName.equals(entry.simpleClassName)) {
                    found = true;
                    if (cs.kind != expectedKind) {
                        Log.d(TAG, "Note: " + cs.simpleClassName + " is declared as <" + tagLabel
                                + "> in manifest but classified as " + cs.kind
                                + " by superclass scan — keeping superclass-based classification.");
                    }
                    logger.info("  <" + tagLabel + "> " + entry.simpleClassName
                            + "  exported=" + entry.exported
                            + (entry.permission.isEmpty() ? "" : "  permission=" + entry.permission)
                            + (entry.authorities.isEmpty() ? "" : "  authorities=" + entry.authorities));
                    break;
                }
            }
            if (!found) {
                logger.warning("<" + tagLabel + "> in manifest but no matching source found: "
                        + entry.simpleClassName);
            }
        }
    }

    // ── Source file copy ──────────────────────────────────────────────────────

    private void copySourceFiles(List<ClassifiedSource> sources, String javaDestPath) {
        for (ClassifiedSource cs : sources) {
            if (cs.file == null || !cs.file.exists()) continue;
            String packageSubDir = cs.packageName.replace('.', '/');
            String destDir = javaDestPath + "/" + packageSubDir;
            FileUtil.makeDir(destDir);
            FileUtil.copyFile(cs.file.getAbsolutePath(), destDir + "/" + cs.file.getName());
        }
    }

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

    // ── JNI source import (source-level, not prebuilt .so) ────────────────────

    private static final String[] JNI_SOURCE_EXTENSIONS = {
            ".cpp", ".cc", ".cxx", ".c", ".h", ".hpp", ".hxx"
    };
    private static final String[] JNI_BUILD_FILE_NAMES = {
            "CMakeLists.txt", "Android.mk", "Application.mk"
    };

    /**
     * Finds the module's JNI source root. Checks the CMake-conventional
     * src/main/cpp first, then the classic ndk-build src/main/jni, then a
     * bare jni/ at the module root (some older/migrated projects use this).
     * Returns null if neither exists — a project can validly ship only
     * prebuilt .so files with no source at all.
     */
    private File locateJniSourceDir(File srcMain) {
        File cpp = new File(srcMain, "cpp");
        if (cpp.exists() && cpp.isDirectory()) return cpp;

        File jniUnderSrcMain = new File(srcMain, "jni");
        if (jniUnderSrcMain.exists() && jniUnderSrcMain.isDirectory()) return jniUnderSrcMain;

        // Classic ndk-build convention: <module>/jni/, i.e. a sibling of src/,
        // not nested under src/main/ — srcMain is .../<module>/src/main.
        File moduleRoot = srcMain.getParentFile() != null ? srcMain.getParentFile().getParentFile() : null;
        if (moduleRoot != null) {
            File jniAtModuleRoot = new File(moduleRoot, "jni");
            if (jniAtModuleRoot.exists() && jniAtModuleRoot.isDirectory()) return jniAtModuleRoot;
        }

        return null;
    }

    /**
     * Copies only real JNI source + build-script files (never build/.cxx/
     * intermediates output, since those are computed from this same source
     * and would just be dead weight — or worse, stale/wrong once re-built
     * inside Sketchware Neo).
     */
    private void copyJniSourceOnly(File src, File dest) {
        FileUtil.makeDir(dest.getAbsolutePath());
        File[] files = src.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.equals("build") || name.equals(".cxx") || name.equals("intermediates")) {
                    continue; // generated output — never a real source directory under cpp/jni
                }
                copyJniSourceOnly(f, new File(dest, name));
            } else if (isJniSourceFile(f)) {
                FileUtil.copyFile(f.getAbsolutePath(), dest.getAbsolutePath() + "/" + f.getName());
            }
        }
    }

    private boolean isJniSourceFile(File f) {
        String name = f.getName();
        for (String buildFile : JNI_BUILD_FILE_NAMES) {
            if (name.equals(buildFile)) return true;
        }
        String lower = name.toLowerCase();
        for (String ext : JNI_SOURCE_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    private int countJniSourceFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int count = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.equals("build") || name.equals(".cxx") || name.equals("intermediates")) continue;
                count += countJniSourceFiles(f);
            } else if (isJniSourceFile(f)) {
                count++;
            }
        }
        return count;
    }

    // ── ZIP extraction ────────────────────────────────────────────────────────

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
            int skippedEntries = 0;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.contains("..")) {
                    Log.w(TAG, "Skipping suspicious ZIP entry: " + entryName);
                    logger.warning("Skipped path-traversal ZIP entry: " + entryName);
                    zis.closeEntry();
                    skippedEntries++;
                    continue;
                }
                File dest = new File(tempDest, entryName);
                if (entry.isDirectory()) {
                    FileUtil.makeDir(dest.getAbsolutePath());
                } else {
                    FileUtil.makeDir(dest.getParentFile().getAbsolutePath());
                    try (FileOutputStream fos = new FileOutputStream(dest)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }

            if (skippedEntries > 0) {
                logger.warning("Skipped " + skippedEntries + " suspicious ZIP entries (path traversal).");
            }
            Log.d(TAG, "ZIP extracted to: " + tempDest.getAbsolutePath());
            return tempDest;

        } catch (IOException e) {
            Log.e(TAG, "ZIP extraction failed", e);
            logger.error("ZIP extraction failed", e,
                    new ImportLogger.ErrorContext().file(zipPath));
            FileUtil.deleteFile(tempDest.getAbsolutePath());
            return null;
        }
    }

    // ── Custom manifest builder ───────────────────────────────────────────────

    private String buildCustomManifest(ParsedManifest manifest) {
        Log.i(TAG, "NOTE: Permissions, services, and receivers from AndroidManifest.xml " +
                "must be added manually via Sketchware's Manifest editor.");
        logger.warning("Permissions, services, and receivers must be added manually " +
                "in Sketchware's Manifest editor. They were not automatically imported.");
        return "";
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private void cleanupTemp() {
        if (tempDir != null && tempDir.exists()) {
            FileUtil.deleteFile(tempDir.getAbsolutePath());
            logger.info("Temp directory deleted: " + tempDir.getAbsolutePath());
            Log.d(TAG, "Temp directory cleaned up.");
        }
    }

    // ── Stat helpers ──────────────────────────────────────────────────────────

    /** Recursively counts regular files in a directory tree. */
    private int countFiles(File dir) {
        if (dir == null || !dir.exists()) return 0;
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) count += countFiles(f);
            else count++;
        }
        return count;
    }

    /**
     * Counts files in all subdirectories whose name starts with any of the given prefixes.
     * Used to count drawables (drawable*, mipmap*) and fonts (font*).
     */
    private int countFilesInPrefixedDirs(File resDir, String... prefixes) {
        if (resDir == null || !resDir.exists()) return 0;
        int count = 0;
        File[] children = resDir.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName().toLowerCase(java.util.Locale.getDefault());
            for (String prefix : prefixes) {
                if (name.startsWith(prefix)) {
                    count += countFiles(child);
                    break;
                }
            }
        }
        return count;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024));
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
