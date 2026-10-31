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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import com.google.gson.Gson;
import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.hey.studios.project.proguard.ProguardHandler;

import a.a.a.wq;
import a.a.a.Lx;
import a.a.a.jq;
import dev.sketchlibx.filepicker.FilePickerCallback;
import dev.sketchlibx.filepicker.FilePickerDialogFragment;
import dev.sketchlibx.filepicker.FilePickerOptions;
import pro.sketchware.activities.main.fragments.projects.ProjectsFragment;
import pro.sketchware.databinding.ProgressMsgBoxBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.FilePathUtil;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.build.BuildSettings;
import pro.sketchware.activities.editor.gradle.CustomGradleBuildManager;
import pro.sketchware.utility.SketchwareUtil;

/**
 * Imports an Android Studio project into Sketchware Neo. The pipeline extracts and
 * checks the ZIP, reads the manifest and Gradle files, imports source and layouts,
 * writes the project, then copies resources, assets and native files.
 *
 * SketchwareWriter owns the initial write and rollback. Later resource changes use
 * its patch methods. Resource paths must use wq.g() for images, wq.t() for sounds
 * and wq.d() for fonts; wq.n() is the project registry path. New project IDs are
 * assigned by the existing project store. Existing projects are not modified.
 */
public class ASProjectImporter extends AsyncTask<Void, String, ASProjectImporter.Result> {

     

    /** Bumped here so the verbose log header always shows the correct version. */
    static final String IMPORTER_VERSION = "2.0.0";

    private static final String TAG = "ASProjectImporter";

     

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

     

    static class Result {
        final boolean success;
        final String  scId;
        final String  failReason;

        Result(String scId)          { this.success = true;  this.scId = scId; this.failReason = null; }
        Result(String r, boolean f)  { this.success = false; this.scId = null; this.failReason = r; }
    }

     

    private final Context      context;
    private final String       zipFilePath;
    private final Callback     callback;
    private final ImportLogger logger;       // never null — disabled logger has zero overhead
    private final ImportOptions options;

    /** Temp directory where ZIP is extracted. Deleted after import regardless of outcome. */
    private File tempDir;

    /** Writer instance kept so rollback() can be called on failure. */
    private SketchwareWriter writer;

     

    /** Shows the ZIP picker and asks whether to enable verbose logging before import. */
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

    /** Starts the import after the logging choice, without duplicating callback setup. */
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

     

    /** Original constructor. Verbose logging is disabled. Existing callers unaffected. */
    public ASProjectImporter(Context context, String zipFilePath, Callback callback) {
        this(context, zipFilePath, ImportLogger.create(false), new ImportOptions(), callback);
    }

    /** Full constructor — pass a configured logger while retaining default conversion settings. */
    public ASProjectImporter(Context context, String zipFilePath,
                             ImportLogger logger, Callback callback) {
        this(context, zipFilePath, logger, new ImportOptions(), callback);
    }

    /** Full constructor used by the dedicated Import UI. */
    public ASProjectImporter(Context context, String zipFilePath, ImportLogger logger,
                             ImportOptions options, Callback callback) {
        this.context = context.getApplicationContext();
        this.zipFilePath = zipFilePath;
        this.callback = callback;
        this.logger = logger == null ? ImportLogger.create(false) : logger;
        this.options = options == null ? new ImportOptions() : options.copy();
    }

     

    @Override
    protected Result doInBackground(Void... voids) {
        // Open the logger session here, on the background thread, so timing is
        // accurate from the very first operation.
        logger.start("AS Import", context, zipFilePath, IMPORTER_VERSION);
        logger.info("Convert Java to Blocks: " + options.convertJavaToBlocks);
        logger.info("Convert XML layouts to visual views: " + options.convertXmlLayoutsToVisualViews);
        logger.info("Extract custom methods as MoreBlocks: " + options.extractCustomMethodsToMoreBlocks);
        logger.info("Import Gradle dependencies: " + options.importGradleDependencies);
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

     

    private Result runPipeline() throws Exception {

         
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

         
        progress("Parsing build configuration...");
        logger.step(5, "Parse build.gradle");
        GradleParser gradleParser = new GradleParser();
        ParsedGradle gradle = gradleParser.parse(appModuleDir);

        if ("com.imported.project".equals(gradle.applicationId)) {
            // AGP's default applicationId follows the Gradle namespace on modern
            // projects. Prefer that over a legacy manifest package that can differ.
            String fallbackPackage = gradle.namespace == null ? "" : gradle.namespace.trim();
            String fallbackSource = "Gradle namespace";
            if (fallbackPackage.isEmpty()) {
                fallbackPackage = manifest.packageName == null ? "" : manifest.packageName.trim();
                fallbackSource = "manifest package";
            }
            if (!fallbackPackage.isEmpty()) {
                gradle.applicationId = fallbackPackage;
                logger.warning("applicationId not found as a literal in Gradle; using "
                        + fallbackSource + ": " + fallbackPackage);
            }
        }
        if (gradle.applicationId == null || !gradle.applicationId.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")) {
            logger.error("The resolved applicationId is not a valid Java/Android package name: " + gradle.applicationId, null, null);
            logger.stepDone(false);
            return failure("Invalid applicationId '" + gradle.applicationId + "'. Use a valid dotted Android package name before importing.");
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
                logger.info(cs.kind.name() + " source detected: " + cs.simpleClassName
                        + " — manifest declaration and nested metadata/intent-filters are copied through"
                        + " Injection/androidmanifest/app_components.txt. Check references to external Android Studio"
                        + " classes because class relocation/package mapping may still need review.");
            } else if (cs.kind == ClassifiedSource.Kind.PROVIDER) {
                logger.info("PROVIDER source detected: " + cs.simpleClassName
                        + " — provider declaration is preserved through app_components.txt;"
                        + " review authority values for applicationId placeholders.");
            } else if (cs.kind == ClassifiedSource.Kind.APPLICATION) {
                logger.warning("Custom Application class detected: " + cs.simpleClassName
                        + " — copied as Java only. Sketchware Neo's own Application class is fixed and was"
                        + " NOT replaced; merge any needed logic from this class manually.");
            }
        }
        logger.stepDone(true);

         
        progress("Importing layout files...");
        logger.step(7, "Import Layouts");
        LayoutImporter layoutImporter = new LayoutImporter();
        File resDir        = new File(srcMain, "res");
        File baseLayoutDir = new File(resDir, "layout");

        logger.info("res dir   : " + resDir.getAbsolutePath());
        logger.info("layout dir: " + baseLayoutDir.getAbsolutePath()
                + (baseLayoutDir.exists() ? "" : "  [NOT FOUND]"));

        List<LayoutImporter.ImportedLayout> layouts;
        if (options.convertXmlLayoutsToVisualViews) {
            layouts = layoutImporter.importLayouts(baseLayoutDir, sources, manifest, manifest.appTheme);
        } else {
            layouts = layoutImporter.createEmptyScreens(sources, manifest);
            logger.info("XML conversion disabled by user; registered blank activity shells instead of guessing XML widgets.");
        }
        String fileData     = layoutImporter.buildFileData(layouts);
        String viewData     = layoutImporter.buildViewData(layouts);
        String viewRootData = layoutImporter.buildViewRootData(layouts);

        stats.layouts = layouts.size();
        logger.info("Layouts imported : " + layouts.size());
        int rootsRead = 0;
        for (LayoutImporter.ImportedLayout il : layouts) {
            boolean gotRoot = il.rootClassName != null;
            if (gotRoot) rootsRead++;
            logger.info("  " + il.sketchwareFileName
                    + "  (fileType=" + il.fileType + ")"
                    + (gotRoot ? "  root=" + il.rootClassName : "  [root tag unreadable]"));
        }
        logger.info("Root containers captured (data/view_root) : " + rootsRead + "/" + layouts.size());
        logger.stepDone(true);
        Log.d(TAG, "Imported layouts: " + layouts.size());

         
        progress("Detecting libraries...");
        logger.step(8, "Detect Libraries");
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

         
        progress("Creating Sketchware project...");
        logger.step(9, "Write Sketchware Project Files");
        writer = new SketchwareWriter();
        SketchwareWriter.WriteInput writeInput = new SketchwareWriter.WriteInput();
        writeInput.appName       = manifest.appName;
        writeInput.packageName   = gradle.applicationId;
        writeInput.versionCode   = gradle.versionCode;
        writeInput.versionName   = gradle.versionName;
        writeInput.minSdk        = gradle.minSdk;
        writeInput.targetSdk     = gradle.targetSdk;
        writeInput.compileSdk    = gradle.compileSdk;
        writeInput.hasKotlin     = gradle.hasKotlin;
        writeInput.viewBindingEnabled = gradle.viewBindingEnabled;
        writeInput.customJavaEnabled = !options.convertJavaToBlocks;
        writeInput.multiDexEnabled = gradle.multiDexEnabled;
        writeInput.javaVersion = supportedJavaVersion(gradle.javaVersion);
        writeInput.proguardEnabled = gradle.minifyEnabledInRelease;
        writeInput.proguardRules = readImportedProguardRules(gradle, appModuleDir);
        writeInput.fileData      = fileData;
        writeInput.viewData      = viewData;
        writeInput.viewRootData  = viewRootData;
        writeInput.libraryData   = libraryData;
        writeInput.manifestContent = buildCustomManifest(manifest);
        writeInput.launcherFileName = resolveLauncherFileName(sources, manifest);
        writeInput.permissionsJson  = buildPermissionsJson(manifest.permissions);
        writeInput.manifestAttributesJson = buildManifestAttributesJson(manifest, sources);
        writeInput.activityComponentsJson = buildActivityComponentsJson(manifest, sources);
        writeInput.appComponentsXml = buildApplicationComponentsXml(manifest);
        writeInput.logicData        = options.convertJavaToBlocks
                ? buildFullLogicData(sources, layouts, options.extractCustomMethodsToMoreBlocks)
                : "";

        logger.info("Launcher screen (Injection/androidmanifest/activity_launcher.txt) : "
                + writeInput.launcherFileName);
        logger.info("Permissions written to data/permission : " + manifest.permissions.size());

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
        try {
            applyImportedBuildSettings(scId, gradle, writeInput, appModuleDir, detection.projectRoot);
        } catch (Exception e) {
            logger.warning("Some Gradle settings could not be mapped to Sketchware Neo: " + e.getMessage());
            Log.w(TAG, "Could not fully apply imported Gradle settings", e);
        }
        logger.stepDone(true);

         
        // Resource paths are based on the generated sc_id; use SketchwareWriter's patch methods for later updates.
         
         
         
         
        progress("Copying image and media resources...");
        logger.step(10, "Map Resources");
        logger.info("res dir : " + resDir.getAbsolutePath());

        ResourceMapper resMapper = new ResourceMapper();
        // Use wq.g(), wq.t() and wq.d() for image, sound and font paths; wq.n() is the project registry.
         
         
         
         
        String swImagesPath = wq.g() + "/" + scId;
        String swSoundsPath = wq.t() + "/" + scId;
        String swFontsPath  = wq.d() + "/" + scId;

        String projectIconPath = wq.e() + File.separator + scId + File.separator + "icon.png";
        ResourceMapper.ResourceResult resResult = resMapper.process(
                resDir, filesPath, swImagesPath, swSoundsPath, swFontsPath,
                manifest.iconResName, projectIconPath);

        stats.drawables = countFilesInPrefixedDirs(resDir, "drawable", "mipmap");
        stats.fonts     = countFilesInPrefixedDirs(resDir, "font");
        logger.info("Drawables copied : " + stats.drawables);
        logger.info("Fonts copied     : " + stats.fonts);
        logger.info("Custom icon      : " + resResult.hasCustomIcon);

        if (!resResult.resourceData.equals("@images\n@sounds\n@fonts\n")) {
            try {
                writer.patchResourceData(scId, resResult.resourceData);
                logger.info("Resource data file updated.");
                Log.d(TAG, "Re-wrote resource file with " + resResult.resourceData.length() + " chars.");
            } catch (Exception e) {
                Log.w(TAG, "Could not update resource data after copy", e);
                logger.warning("Could not update resource data file: " + e.getMessage());
            }
        }
        if (resResult.hasCustomIcon) {
            try {
                // Adaptive icon XML is not handled yet.
                 
                 
                 
                boolean patched = writer.patchIconFlags(scId, true, false);
                if (patched) {
                    logger.info("custom_icon flag patched in project map.");
                } else {
                    logger.warning("Could not patch custom_icon flag: project not found for sc_id=" + scId);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not patch custom_icon flag", e);
                logger.warning("Could not patch custom_icon flag: " + e.getMessage());
            }
        }
        logger.stepDone(true);

        // Keep original XML and build files so unsupported details are not lost.
         
         
        preserveOriginalProjectReferences(detection.projectRoot, appModuleDir, srcMain, filesPath);

         
        progress("Copying source files...");
        logger.step(11, "Copy Source Files");
        // Keep the original source tree, but do not place block-converted Activities in files/java.
         
         
         
        int copiedSources = copySourcesForSketchware(javaSrc, kotlinSrc, filesPath, scId,
                sources, manifest.packageName, gradle.applicationId);
        preserveOriginalGradleConfiguration(detection.projectRoot, appModuleDir, filesPath);
        logger.info("Java/Kotlin source files preserved/reference-copied : " + copiedSources);
        logger.stepDone(true);
        Log.d(TAG, "Source files copied: " + copiedSources);

         
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

         
        logger.step(13, "Copy Native Libraries and JNI Source");
        if (gradle.hasNativeLibs) {
            boolean anyNativeContentCopied = false;

            // Copy prebuilt .so files and keep their ABI folders.
             
             
            File nativeLibsDest = new File(filesPath + "/native_libs");
            File jniLibsSrc = new File(srcMain, "jniLibs");
            if (jniLibsSrc.exists()) {
                progress("Copying prebuilt native libraries...");
                copyDirectory(jniLibsSrc, nativeLibsDest);
                stats.jniLibs += countFiles(jniLibsSrc);
                logger.info("Prebuilt jniLibs copied : " + countFiles(jniLibsSrc));
                anyNativeContentCopied = true;
            } else {
                logger.info("No prebuilt src/main/jniLibs/ directory found.");
            }
            File moduleLibsDir = new File(appModuleDir, "libs");
            int moduleSharedLibraries = copySharedLibrariesOnly(moduleLibsDir, nativeLibsDest, moduleLibsDir);
            if (moduleSharedLibraries > 0) {
                stats.jniLibs += moduleSharedLibraries;
                logger.info("Additional prebuilt .so files copied from app module libs/: " + moduleSharedLibraries);
                anyNativeContentCopied = true;
            }

            // Copy native source files, not build output.
            File jniSourceDir = locateJniSourceDir(srcMain);
            if (jniSourceDir != null) {
                progress("Copying JNI source (" + gradle.nativeBuildSystem + ")...");
                File jniDest = new File(filesPath + "/jni");
                copyJniSourceOnly(jniSourceDir, jniDest);
                int jniSourceFileCount = countJniSourceFiles(jniSourceDir);
                logger.info("JNI/native support files copied : " + countFiles(jniSourceDir)
                        + " total file(s), including " + jniSourceFileCount
                        + " C/C++ source/header/build files (" + gradle.nativeBuildSystem + ") from "
                        + jniSourceDir.getAbsolutePath());
                ensureNeoNativeCmakeBuild(jniDest, appModuleDir);
                anyNativeContentCopied = true;

                if (gradle.nativeBuildSystem == ParsedGradle.NativeBuildSystem.NDK_BUILD) {
                    logger.warning("This module uses Android.mk/ndk-build. Neo's on-device NativeCompiler"
                            + " invokes CMake directly and does not run ndk-build; Android.mk/Application.mk and"
                            + " all neighboring support files were preserved, but the build needs a verified"
                            + " CMakeLists.txt/port before native compilation can succeed.");
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

         
        logger.step(14, "Copy Local Libraries");
        if (!gradle.localLibPaths.isEmpty()) {
            progress("Copying and registering local libraries...");
            String localLibDest = filesPath + "/libs";
            FileUtil.makeDir(localLibDest);
            for (String localLibPath : gradle.localLibPaths) {
                File libFile = new File(localLibPath);
                File referenceCopy = new File(localLibDest, libFile.getName());
                try {
                    java.nio.file.Files.copy(libFile.toPath(), referenceCopy.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    logger.info("Preserved original local library: " + libFile.getName());
                } catch (Exception e) {
                    logger.warning("Could not preserve local library archive " + libFile.getName() + ": " + e.getMessage());
                }
                try {
                    String registeredName = installLocalLibraryArchive(scId, libFile, filesPath);
                    if (registeredName != null) logger.info("Registered local library '" + registeredName + "' in data/local_library.");
                    else logger.warning("Local library " + libFile.getName() + " is preserved as a reference, but its Neo local-library registration failed.");
                } catch (Exception e) {
                    logger.warning("Could not register local library " + libFile.getName() + ": " + e.getMessage());
                    Log.w(TAG, "Local library import failed: " + libFile.getName(), e);
                }
            }
            logger.stepDone(true);
        } else {
            logger.stepSkipped("no local .aar / .jar libraries");
        }

         
        logger.step(15, "Cleanup Temp Directory");
        // Cleanup runs in finally, after the import work is complete.
         
         
        logger.info("Temp dir will be deleted in finally block.");

         
        logger.stats(stats);

        progress("Import complete!");
        Log.d(TAG, "Import succeeded. sc_id=" + scId);
        return new Result(scId);
    }

     

    /** Adds a ClassifiedSource entry for each Activity declared in the manifest. */
    private void reconcileWithManifest(List<ClassifiedSource> sources,
                                       ParsedManifest manifest) {
        for (ParsedManifest.ActivityEntry entry : manifest.activities) {
            String manifestClass = resolveManifestClassName(entry.rawClassName, manifest.packageName);
            ClassifiedSource match = null;
            for (ClassifiedSource source : sources) {
                String sourceClass = source.packageName == null || source.packageName.isEmpty()
                        ? source.simpleClassName : source.packageName + "." + source.simpleClassName;
                if (manifestClass.equals(sourceClass)) {
                    match = source;
                    break;
                }
            }
            if (match == null) {
                ClassifiedSource uniqueSimpleMatch = null;
                int simpleMatches = 0;
                for (ClassifiedSource source : sources) {
                    if (entry.simpleClassName.equals(source.simpleClassName)) {
                        uniqueSimpleMatch = source;
                        simpleMatches++;
                    }
                }
                if (simpleMatches == 1) match = uniqueSimpleMatch;
            }

            if (match != null) {
                if (match.kind != ClassifiedSource.Kind.ACTIVITY) {
                    Log.d(TAG, "Upgrading " + match.simpleClassName + " to ACTIVITY (manifest declaration). ");
                    logger.info("Upgraded to ACTIVITY (manifest): " + match.simpleClassName);
                    ClassifiedSource upgraded = new ClassifiedSource(match.file, match.packageName,
                            match.simpleClassName, ClassifiedSource.Kind.ACTIVITY);
                    upgraded.associatedLayout = match.associatedLayout;
                    upgraded.sketchwareFileName = match.sketchwareFileName;
                    sources.set(sources.indexOf(match), upgraded);
                }
                continue;
            }

            if (!isApplicationOwnedManifestClass(manifestClass, manifest.packageName)) {
                logger.info("Skipped external manifest Activity without project source: " + manifestClass);
                continue;
            }

            Log.w(TAG, "Activity in manifest but no source found: " + manifestClass
                    + " — creating a placeholder screen for manual implementation.");
            logger.warning("No source for manifest Activity — placeholder created: " + manifestClass);
            ClassifiedSource placeholder = new ClassifiedSource(
                    null, packageOf(manifestClass), entry.simpleClassName,
                    ClassifiedSource.Kind.ACTIVITY);
            placeholder.sketchwareFileName = SourceClassifier.classNameToSketchwareFileName(entry.simpleClassName);
            sources.add(placeholder);
        }

        reconcileComponents(sources, manifest.services, ClassifiedSource.Kind.SERVICE, "service");
        reconcileComponents(sources, manifest.receivers, ClassifiedSource.Kind.RECEIVER, "receiver");
        reconcileComponents(sources, manifest.providers, ClassifiedSource.Kind.PROVIDER, "provider");

        if (!manifest.permissions.isEmpty()) {
            logger.info("Permissions declared in manifest: " + manifest.permissions.size());
            for (String permission : manifest.permissions) logger.info("  uses-permission: " + permission);
            logger.info(manifest.permissions.size() + " standard permission(s) will be written to data/permission "
                    + "and injected with their supported attributes using Neo's attributes.json mechanism.");
        }
        if (manifest.unsupportedManifestItems != null) {
            for (String item : manifest.unsupportedManifestItems) logger.warning("Manifest item needs manual review: " + item);
        }
        if (manifest.applicationClassName != null) {
            logger.info("Custom Application class in manifest: " + manifest.applicationClassName);
        }
    }

    private String resolveManifestClassName(String rawName, String manifestPackage) {
        if (rawName == null || rawName.trim().isEmpty()) return "";
        String value = rawName.trim();
        if (value.startsWith(".")) return (manifestPackage == null ? "" : manifestPackage) + value;
        if (value.indexOf('.') < 0) return manifestPackage == null || manifestPackage.isEmpty()
                ? value : manifestPackage + "." + value;
        return value;
    }

    private String packageOf(String className) {
        if (className == null) return "";
        int index = className.lastIndexOf('.');
        return index < 0 ? "" : className.substring(0, index);
    }

    private boolean isApplicationOwnedManifestClass(String className, String manifestPackage) {
        if (className == null || className.isEmpty()) return false;
        if (manifestPackage == null || manifestPackage.isEmpty()) return true;
        return className.equals(manifestPackage) || className.startsWith(manifestPackage + ".");
    }

    /** Matches manifest components to source files and keeps their manifest settings. */
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

     

    private void preserveOriginalProjectReferences(File projectRoot, File appModuleDir,
                                                    File srcMain, String filesPath) {
        File referenceRoot = new File(filesPath, "android_studio_original");
        int copied = 0;
        File resDir = new File(srcMain, "res");
        if (resDir.isDirectory()) {
            copied += copyXmlReferencesRecursively(resDir, resDir,
                    new File(referenceRoot, "res"));
        }
        copied += copyReferenceFile(new File(srcMain, "AndroidManifest.xml"), referenceRoot);
        copied += copyReferenceFile(new File(appModuleDir, "build.gradle"), new File(referenceRoot, "app_build"));
        copied += copyReferenceFile(new File(appModuleDir, "build.gradle.kts"), new File(referenceRoot, "app_build"));
        copied += copyReferenceFile(new File(projectRoot, "settings.gradle"), new File(referenceRoot, "root_build"));
        copied += copyReferenceFile(new File(projectRoot, "settings.gradle.kts"), new File(referenceRoot, "root_build"));
        copied += copyReferenceFile(new File(projectRoot, "build.gradle"), new File(referenceRoot, "root_build"));
        copied += copyReferenceFile(new File(projectRoot, "build.gradle.kts"), new File(referenceRoot, "root_build"));
        copied += copyReferenceFile(new File(projectRoot, "gradle.properties"), new File(referenceRoot, "root_build"));
        copied += copyReferenceFile(new File(new File(projectRoot, "gradle"), "libs.versions.toml"),
                new File(referenceRoot, "root_build/gradle"));
        logger.info("Preserved " + copied + " original XML/build reference file(s) under files/android_studio_original/.");
    }

    private int copyXmlReferencesRecursively(File current, File sourceRoot, File targetRoot) {
        if (current == null || !current.exists()) return 0;
        int copied = 0;
        File[] children = current.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (child.isDirectory()) {
                copied += copyXmlReferencesRecursively(child, sourceRoot, targetRoot);
            } else if (child.isFile() && child.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
                try {
                    String relative = sourceRoot.toPath().relativize(child.toPath()).toString();
                    File out = new File(targetRoot, relative);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                        throw new IOException("Could not create " + parent.getAbsolutePath());
                    }
                    java.nio.file.Files.copy(child.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                } catch (Exception e) {
                    logger.warning("Could not preserve original XML reference " + child.getName() + ": " + e.getMessage());
                }
            }
        }
        return copied;
    }

    private int copyReferenceFile(File source, File targetDir) {
        if (source == null || !source.isFile()) return 0;
        try {
            if (!targetDir.exists() && !targetDir.mkdirs() && !targetDir.isDirectory()) {
                throw new IOException("Could not create " + targetDir.getAbsolutePath());
            }
            java.nio.file.Files.copy(source.toPath(), new File(targetDir, source.getName()).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return 1;
        } catch (Exception e) {
            logger.warning("Could not preserve original reference " + source.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private int countSourceFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File child : children) {
            if (child.isDirectory()) count += countSourceFiles(child);
            else if (child.isFile() && (child.getName().endsWith(".java") || child.getName().endsWith(".kt"))) count++;
        }
        return count;
    }

    private void copySourceFiles(List<ClassifiedSource> sources, String javaDestPath) {
        for (ClassifiedSource cs : sources) {
            if (cs.file == null || !cs.file.exists()) continue;
            String packageSubDir = cs.packageName.replace('.', '/');
            String destDir = javaDestPath + "/" + packageSubDir;
            FileUtil.makeDir(destDir);
            FileUtil.copyFile(cs.file.getAbsolutePath(), destDir + "/" + cs.file.getName());
        }
    }

    private int copySourcesForSketchware(File javaSrc, File kotlinSrc, String filesPath, String scId,
                                         List<ClassifiedSource> sources, String manifestPackage,
                                         String applicationId) {
        File referenceRoot = new File(filesPath, "android_studio_original/source");
        if (javaSrc != null && javaSrc.isDirectory()) copyDirectory(javaSrc, new File(referenceRoot, "java"));
        if (kotlinSrc != null && kotlinSrc.isDirectory() && !kotlinSrc.equals(javaSrc)) {
            copyDirectory(kotlinSrc, new File(referenceRoot, "kotlin"));
        }

        Map<String, ClassifiedSource> byAbsolutePath = new LinkedHashMap<>();
        Map<String, String> activityNameMap = new LinkedHashMap<>();
        Map<String, String> layoutNameMap = new LinkedHashMap<>();
        if (sources != null) for (ClassifiedSource source : sources) {
            if (source == null || source.file == null) continue;
            try { byAbsolutePath.put(source.file.getCanonicalPath(), source); }
            catch (Exception ignored) { byAbsolutePath.put(source.file.getAbsolutePath(), source); }
            if (source.kind == ClassifiedSource.Kind.ACTIVITY) {
                String fileName = source.sketchwareFileName;
                if (fileName == null || fileName.trim().isEmpty()) fileName = SourceClassifier.classNameToSketchwareFileName(source.simpleClassName);
                String generated = ProjectFileBean.getActivityName(fileName);
                activityNameMap.put(source.simpleClassName, generated);
                if (source.associatedLayout != null && !source.associatedLayout.trim().isEmpty()) {
                    layoutNameMap.put(source.associatedLayout, fileName);
                }
            }
        }

        File javaDestination = new File(filesPath, "java");
        if (!javaDestination.exists()) javaDestination.mkdirs();
        File customJavaDirectory = new File(wq.b(scId), "custom_java");
        if (!options.convertJavaToBlocks && !customJavaDirectory.exists()) customJavaDirectory.mkdirs();

        java.util.List<File> allFiles = new ArrayList<>();
        collectSourceFiles(javaSrc, allFiles);
        if (kotlinSrc != null && kotlinSrc.isDirectory() && !kotlinSrc.equals(javaSrc)) collectSourceFiles(kotlinSrc, allFiles);
        int copied = 0;
        Map<String, String> firstPackageByName = new HashMap<>();
        for (File file : allFiles) {
            ClassifiedSource classified = null;
            try { classified = byAbsolutePath.get(file.getCanonicalPath()); }
            catch (Exception ignored) { classified = byAbsolutePath.get(file.getAbsolutePath()); }
            boolean activity = classified != null && classified.kind == ClassifiedSource.Kind.ACTIVITY;

            if (activity) {
                if (options.convertJavaToBlocks) {
                    // Keep block-converted Activity source only in android_studio_original to avoid overriding generated logic.
                     
                     
                    continue;
                }
                if (file.getName().endsWith(".kt")) {
                    logger.warning("Kotlin Activity " + file.getName() + " was preserved under android_studio_original but not installed as custom Java: the custom_java override only accepts Java source.");
                    continue;
                }
                String logicalName = classified.sketchwareFileName;
                if (logicalName == null || logicalName.trim().isEmpty()) logicalName = SourceClassifier.classNameToSketchwareFileName(classified.simpleClassName);
                String outputClass = ProjectFileBean.getActivityName(logicalName);
                String adapted;
                try {
                    adapted = adaptActivitySource(readUtf8(file), classified, activityNameMap, layoutNameMap,
                            manifestPackage, applicationId, outputClass);
                } catch (Exception e) {
                    logger.warning("Could not adapt Activity source " + file.getName() + " for Neo custom_java: " + e.getMessage());
                    continue;
                }
                File target = new File(customJavaDirectory, outputClass + ".java");
                try {
                    writeUtf8(target, adapted);
                    copied++;
                    logger.info("Installed Activity override at custom_java/" + target.getName());
                } catch (Exception e) {
                    logger.warning("Could not write custom Java Activity " + target.getName() + ": " + e.getMessage());
                }
                continue;
            }

            // Neo reads Java files directly from data/<id>/files/java; keep package names in the source declaration.
             
             
            File target = new File(javaDestination, file.getName());
            if (target.exists()) {
                try {
                    if (java.util.Arrays.equals(java.nio.file.Files.readAllBytes(target.toPath()),
                            java.nio.file.Files.readAllBytes(file.toPath()))) continue;
                } catch (Exception ignored) { }
                String packageName = classified == null ? "" : classified.packageName;
                String previousPackage = firstPackageByName.get(file.getName());
                logger.warning("Name collision for source file " + file.getName() + " from package '" + packageName
                        + "'" + (previousPackage == null ? "" : " (first package '" + previousPackage + "')")
                        + ". Original copies are preserved under files/android_studio_original/source; only the first basename can be installed in Neo's flat files/java folder.");
                continue;
            }
            try {
                java.nio.file.Files.copy(file.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                firstPackageByName.put(file.getName(), classified == null ? "" : classified.packageName);
                copied++;
            } catch (Exception e) {
                logger.warning("Could not install support source " + file.getName() + ": " + e.getMessage());
            }
        }
        if (!options.convertJavaToBlocks) {
            logger.info("custom_java is enabled for Activities because Java-to-block conversion is disabled.");
        }
        return copied;
    }

    private void collectSourceFiles(File root, List<File> out) {
        if (root == null || !root.isDirectory()) return;
        File[] children = root.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File child : children) {
            if (child.isDirectory()) collectSourceFiles(child, out);
            else if (child.isFile() && (child.getName().endsWith(".java") || child.getName().endsWith(".kt"))) out.add(child);
        }
    }

    private String readUtf8(File file) throws IOException {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private String adaptActivitySource(String source, ClassifiedSource activity,
                                       Map<String, String> activityNameMap, Map<String, String> layoutNameMap,
                                       String manifestPackage, String applicationId, String outputClassName) {
        String adapted = source;
        // Update Activity names so imported Intent targets use the generated screen names.
        if (activityNameMap != null) for (Map.Entry<String, String> mapping : activityNameMap.entrySet()) {
            if (mapping.getKey() == null || mapping.getValue() == null) continue;
            String oldName = mapping.getKey();
            String newName = mapping.getValue();
            if (!oldName.equals(newName)) {
                adapted = adapted.replaceAll("\\b" + Pattern.quote(oldName) + "\\b",
                        java.util.regex.Matcher.quoteReplacement(newName));
            }
        }
        // Update layout references too; XML names and Sketchware screen names may differ.
         
        if (layoutNameMap != null) for (Map.Entry<String, String> mapping : layoutNameMap.entrySet()) {
            String oldLayout = mapping.getKey();
            String newLayout = mapping.getValue();
            if (oldLayout == null || newLayout == null || oldLayout.equals(newLayout)) continue;
            adapted = adapted.replaceAll("R\\.layout\\." + Pattern.quote(oldLayout) + "\\b",
                    java.util.regex.Matcher.quoteReplacement("R.layout." + newLayout));
            String oldBinding = bindingClassForLayout(oldLayout);
            String newBinding = bindingClassForLayout(newLayout);
            if (!oldBinding.equals(newBinding)) {
                adapted = adapted.replaceAll("\\b" + Pattern.quote(oldBinding) + "\\b",
                        java.util.regex.Matcher.quoteReplacement(newBinding));
            }
        }
        String originalPackage = activity == null ? "" : activity.packageName;
        String targetPackage = applicationId == null ? "" : applicationId.trim();
        if (!targetPackage.isEmpty() && !targetPackage.equals(originalPackage)) {
            // Keep access to helper classes in the original package.
             
            java.util.regex.Pattern packagePattern = java.util.regex.Pattern.compile("(?m)^\\s*package\\s+[A-Za-z0-9_.]+\\s*;?");
            java.util.regex.Matcher packageMatcher = packagePattern.matcher(adapted);
            if (packageMatcher.find()) {
                adapted = packageMatcher.replaceFirst(java.util.regex.Matcher.quoteReplacement("package " + targetPackage + ";"));
                if (originalPackage != null && !originalPackage.isEmpty() && !originalPackage.equals(targetPackage)) {
                    adapted = adapted.replaceFirst("(?m)^package " + Pattern.quote(targetPackage) + ";\\s*",
                            java.util.regex.Matcher.quoteReplacement("package " + targetPackage + ";\\nimport " + originalPackage + ".*;\\n"));
                }
            } else {
                adapted = "package " + targetPackage + ";\\n" + adapted;
                if (originalPackage != null && !originalPackage.isEmpty() && !originalPackage.equals(targetPackage)) {
                    adapted = adapted.replaceFirst("(?m)^package " + Pattern.quote(targetPackage) + ";\\s*",
                            java.util.regex.Matcher.quoteReplacement("package " + targetPackage + ";\\nimport " + originalPackage + ".*;\\n"));
                }
            }
        }
        // Keep each public class name in sync with its generated Java filename.
         
        if (activity != null && activity.simpleClassName != null && !activity.simpleClassName.equals(outputClassName)) {
            adapted = adapted.replaceAll("\\b" + Pattern.quote(activity.simpleClassName) + "\\b",
                    java.util.regex.Matcher.quoteReplacement(outputClassName));
        }
        return adapted;
    }

    private String bindingClassForLayout(String layoutName) {
        if (layoutName == null) return "Binding";
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < layoutName.length(); i++) {
            char c = layoutName.charAt(i);
            if (!Character.isLetterOrDigit(c)) { upper = true; continue; }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.append("Binding").toString();
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

     

    private static final String[] JNI_SOURCE_EXTENSIONS = {
            ".cpp", ".cc", ".cxx", ".c", ".h", ".hpp", ".hxx"
    };
    private static final String[] JNI_BUILD_FILE_NAMES = {
            "CMakeLists.txt", "Android.mk", "Application.mk"
    };

    /** Checks src/main/cpp, src/main/jni, then module-level jni/. Returns null if none exists. */
    private File locateJniSourceDir(File srcMain) {
        File cpp = new File(srcMain, "cpp");
        if (cpp.exists() && cpp.isDirectory()) return cpp;

        File jniUnderSrcMain = new File(srcMain, "jni");
        if (jniUnderSrcMain.exists() && jniUnderSrcMain.isDirectory()) return jniUnderSrcMain;

        // The classic ndk-build jni folder is next to src/, not inside src/main/.
         
        File moduleRoot = srcMain.getParentFile() != null ? srcMain.getParentFile().getParentFile() : null;
        if (moduleRoot != null) {
            File jniAtModuleRoot = new File(moduleRoot, "jni");
            if (jniAtModuleRoot.exists() && jniAtModuleRoot.isDirectory()) return jniAtModuleRoot;
        }

        return null;
    }

    /** Copies native source files and their support files, skipping build and VCS folders. */
    private void copyJniSourceOnly(File src, File dest) {
        FileUtil.makeDir(dest.getAbsolutePath());
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                String name = f.getName();
                if (name.equals("build") || name.equals(".cxx") || name.equals("intermediates")
                        || name.equals(".git") || name.equals(".gradle")) continue;
                copyJniSourceOnly(f, new File(dest, name));
            } else if (f.isFile()) {
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

    /** Keeps the original Gradle files for review; they are not safe to use as Neo's build script. */
    private void preserveOriginalGradleConfiguration(File projectRoot, File appModuleDir, String filesPath) {
        if (projectRoot == null || !projectRoot.isDirectory()) return;
        File outRoot = new File(filesPath, "android_studio_original/gradle_project");
        java.util.LinkedHashSet<File> preserved = new java.util.LinkedHashSet<>();
        String[] rootFiles = {"build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts",
                "gradle.properties", "gradle/libs.versions.toml"};
        for (String relative : rootFiles) {
            File f = new File(projectRoot, relative);
            if (f.isFile()) preserved.add(f);
        }
        String[] rootDirs = {"gradle", "buildSrc", "build-logic"};
        for (String relative : rootDirs) {
            File f = new File(projectRoot, relative);
            if (f.isDirectory()) collectPreservedBuildFiles(f, preserved, 0);
        }
        if (appModuleDir != null && appModuleDir.isDirectory()) {
            String[] appFiles = {"build.gradle", "build.gradle.kts", "gradle.properties", "proguard-rules.pro",
                    "consumer-rules.pro", "CMakeLists.txt", "Android.mk", "Application.mk"};
            for (String relative : appFiles) {
                File f = new File(appModuleDir, relative);
                if (f.isFile()) preserved.add(f);
            }
        }
        int copied = 0;
        for (File source : preserved) {
            try {
                String relative = projectRoot.toURI().relativize(source.toURI()).getPath();
                if (relative == null || relative.isEmpty() || relative.startsWith("../")) {
                    relative = (appModuleDir != null && source.getAbsolutePath().startsWith(appModuleDir.getAbsolutePath())
                            ? "app_module/" + source.getName() : "external/" + source.getName());
                }
                File dest = new File(outRoot, relative);
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) continue;
                java.nio.file.Files.copy(source.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied++;
            } catch (Exception e) {
                logger.warning("Could not preserve original Gradle file " + source.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        logger.info("Preserved " + copied + " original Gradle/configuration file(s) under files/android_studio_original/gradle_project/");
        if (copied > 0) {
            logger.warning("Review files/android_studio_original/gradle_project/: the runnable Neo Custom Gradle is a safe translated build, not a verbatim replacement for all Android Studio plugin/flavor/convention-plugin logic.");
        }
    }

    private void collectPreservedBuildFiles(File directory, Set<File> result, int depth) {
        if (depth > 12) return;
        File[] children = directory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            if (child.isDirectory()) {
                if (name.equals(".git") || name.equals(".gradle") || name.equals("build") || name.equals(".idea")) continue;
                collectPreservedBuildFiles(child, result, depth + 1);
            } else if (child.isFile()) {
                String lower = name.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".gradle") || lower.endsWith(".gradle.kts")
                        || lower.endsWith(".properties") || lower.endsWith(".toml")
                        || lower.endsWith(".kt") || lower.endsWith(".java")
                        || lower.equals("manifest.json")) {
                    result.add(child);
                }
            }
        }
    }

    /** Copies prebuilt .so files and keeps their ABI folder paths. */
    private int copySharedLibrariesOnly(File source, File destination, File relativeRoot) {
        if (source == null || !source.isDirectory()) return 0;
        File[] files = source.listFiles();
        if (files == null) return 0;
        int copied = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                copied += copySharedLibrariesOnly(file, destination, relativeRoot);
            } else if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".so")) {
                try {
                    String relative = relativeRoot.toURI().relativize(file.toURI()).getPath();
                    File out = new File(destination, relative);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.isDirectory()) {
                        logger.warning("Could not create native library directory: " + parent);
                        continue;
                    }
                    java.nio.file.Files.copy(file.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copied++;
                    if (!relative.matches("(?i).*(armeabi|armeabi-v7a|arm64-v8a|x86|x86_64)/[^/]+\\.so$")) {
                        logger.warning("Prebuilt .so has no recognizable ABI directory: " + relative
                                + ". Keep a valid ABI directory for Android packaging.");
                    }
                } catch (Exception e) {
                    logger.warning("Could not copy native .so " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        return copied;
    }

    /** Adds a simple CMake fallback only when no project CMake file exists. Original build files stay preserved. */
    private void ensureNeoNativeCmakeBuild(File nativeRoot, File appModuleDir) {
        if (nativeRoot == null || !nativeRoot.isDirectory()) return;
        File cmake = new File(nativeRoot, "CMakeLists.txt");
        if (cmake.isFile() && cmake.length() > 0) return;
        if (!containsCompilableNativeSource(nativeRoot)) {
            logger.warning("Native folder contains no .c/.cc/.cpp/.cxx source files. A .h/.hpp or .a file alone is not a compilable native target.");
            return;
        }
        // Do not create a generic CMake target: it can drop project-specific flags and dependencies.
         
         
         
        File moduleLevelCmake = appModuleDir == null ? null : new File(appModuleDir, "CMakeLists.txt");
        File cppModuleCmake = appModuleDir == null ? null : new File(appModuleDir, "src/main/cpp/CMakeLists.txt");
        if ((moduleLevelCmake != null && moduleLevelCmake.isFile())
                || (cppModuleCmake != null && cppModuleCmake.isFile())) {
            logger.warning("An original module-level CMakeLists.txt exists outside the selected files/jni root. It is preserved under files/android_studio_original; a generated generic target was not substituted because the original script may define project-specific paths/options. Port or relocate it to the files/jni root after reviewing paths.");
            return;
        }
        if (containsNativeBuildScript(nativeRoot) || containsNativeStaticArchive(nativeRoot)
                || containsNativeMetadata(nativeRoot)) {
            logger.warning("Generic CMake fallback was not generated because this native tree contains Android.mk/other native build scripts, a static .a archive, pkg-config/Libtool metadata, or an unrecognized extensionless dependency file. All native files were preserved, but the build must be ported to a valid root CMakeLists.txt; Neo's on-device compiler does not execute ndk-build or parse .pc/.la metadata.");
            return;
        }
        Set<String> loadLibraryNames = findSystemLoadLibraryNames(appModuleDir);
        if (loadLibraryNames.size() > 1) {
            logger.warning("Multiple different System.loadLibrary names were found " + loadLibraryNames
                    + ". A single generic CMake target would be incorrect, so no fallback CMakeLists.txt was generated; provide a project-specific files/jni/CMakeLists.txt.");
            return;
        }
        String targetName = loadLibraryNames.isEmpty()
                ? "sketchware_imported_native" : loadLibraryNames.iterator().next();
        if (!targetName.matches("[A-Za-z0-9_-]+")) {
            logger.warning("The inferred System.loadLibrary target name '" + targetName
                    + "' is not safe to place into a generated CMake target. Native sources were preserved; provide a project-specific files/jni/CMakeLists.txt.");
            return;
        }
        String generated = "cmake_minimum_required(VERSION 3.22.1)\n"
                + "project(sketchware_imported_native LANGUAGES C CXX)\n"
                + "set(CMAKE_C_STANDARD 11)\nset(CMAKE_C_STANDARD_REQUIRED ON)\n"
                + "set(CMAKE_CXX_STANDARD 17)\nset(CMAKE_CXX_STANDARD_REQUIRED ON)\n"
                + "file(GLOB_RECURSE IMPORTED_C CONFIGURE_DEPENDS \"${CMAKE_CURRENT_SOURCE_DIR}/*.c\")\n"
                + "file(GLOB_RECURSE IMPORTED_CXX CONFIGURE_DEPENDS \"${CMAKE_CURRENT_SOURCE_DIR}/*.cc\" \"${CMAKE_CURRENT_SOURCE_DIR}/*.cpp\" \"${CMAKE_CURRENT_SOURCE_DIR}/*.cxx\")\n"
                + "set(IMPORTED_SOURCES ${IMPORTED_C} ${IMPORTED_CXX})\n"
                + "if(NOT IMPORTED_SOURCES)\n  message(FATAL_ERROR \"No C/C++ sources found in imported project\")\nendif()\n"
                + "add_library(" + targetName + " SHARED ${IMPORTED_SOURCES})\n"
                + "find_library(log-lib log)\n"
                + "target_link_libraries(" + targetName + " PRIVATE ${log-lib})\n";
        try {
            writeUtf8(cmake, generated);
            logger.warning("No CMakeLists.txt existed at the Neo native root; generated a minimal CMake build target '" + targetName
                    + "' for discovered C/C++ sources. This does not translate Android.mk, pkg-config (.pc), Libtool (.la), project-specific compile flags or .a link dependencies.");
            if (loadLibraryNames.isEmpty()) {
                logger.warning("No literal System.loadLibrary(\"name\") was detected in Java/Kotlin sources. The generated target may need a matching loadLibrary call before its native methods can run.");
            } else {
                logger.info("Generated CMake target matches System.loadLibrary(\"" + targetName + "\").");
            }
            if (appModuleDir != null && new File(appModuleDir, "CMakeLists.txt").isFile()) {
                logger.warning("The module-level CMakeLists.txt is retained under files/android_studio_original/gradle_project, but its module-relative paths were not assumed compatible with files/jni.");
            }
            if (containsNativeStaticArchive(nativeRoot)) {
                logger.warning("Static .a archive(s) are preserved but not automatically linked. Add the required link directives and ABI-specific paths to the native CMakeLists.txt.");
            }
            if (containsNativeMetadata(nativeRoot)) {
                logger.warning("Native .pc/.la/.cnf/.tl or extensionless helper files were preserved. CMake will not interpret them automatically; port relevant flags/dependency logic explicitly.");
            }
        } catch (Exception e) {
            logger.warning("Could not write fallback native CMakeLists.txt: " + e.getMessage());
        }
    }

    /** Finds literal System.loadLibrary names so the generated target uses the right library name. */
    private Set<String> findSystemLoadLibraryNames(File appModuleDir) {
        Set<String> names = new LinkedHashSet<>();
        if (appModuleDir == null) return names;
        File srcMain = new File(appModuleDir, "src/main");
        collectSystemLoadLibraryNames(new File(srcMain, "java"), names, 0);
        collectSystemLoadLibraryNames(new File(srcMain, "kotlin"), names, 0);
        return names;
    }

    private void collectSystemLoadLibraryNames(File dir, Set<String> names, int depth) {
        if (dir == null || depth > 32 || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        Pattern pattern = Pattern.compile("System\\s*\\.\\s*loadLibrary\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
        for (File child : children) {
            if (child.isDirectory()) {
                String n = child.getName();
                if (!"build".equals(n) && !".git".equals(n)) collectSystemLoadLibraryNames(child, names, depth + 1);
            } else if (child.isFile() && (child.getName().endsWith(".java") || child.getName().endsWith(".kt"))) {
                try {
                    String source = new String(java.nio.file.Files.readAllBytes(child.toPath()), StandardCharsets.UTF_8);
                    Matcher matcher = pattern.matcher(source);
                    while (matcher.find()) names.add(matcher.group(1));
                } catch (Exception e) {
                    logger.warning("Could not inspect native library load calls in " + child.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    private boolean containsCompilableNativeSource(File root) {
        File[] files = root.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (file.getName().equals(".git") || file.getName().equals("build") || file.getName().equals(".cxx")) continue;
                if (containsCompilableNativeSource(file)) return true;
            } else {
                String n = file.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith(".c") || n.endsWith(".cc") || n.endsWith(".cpp") || n.endsWith(".cxx")) return true;
            }
        }
        return false;
    }

    private boolean containsNativeBuildScript(File root) {
        File[] files = root.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (containsNativeBuildScript(file)) return true;
            } else {
                String n = file.getName().toLowerCase(Locale.ROOT);
                if ("android.mk".equals(n) || "application.mk".equals(n) || "makefile".equals(n)
                        || n.endsWith(".mk") || n.endsWith(".cmake") || "cmakelists.txt".equals(n)) return true;
            }
        }
        return false;
    }

    private boolean containsNativeStaticArchive(File root) {
        File[] files = root.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (containsNativeStaticArchive(file)) return true;
            } else if (file.getName().toLowerCase(Locale.ROOT).endsWith(".a")) return true;
        }
        return false;
    }

    private boolean containsNativeMetadata(File root) {
        File[] files = root.listFiles();
        if (files == null) return false;
        for (File file : files) {
            if (file.isDirectory()) {
                if (containsNativeMetadata(file)) return true;
            } else {
                String n = file.getName().toLowerCase(Locale.ROOT);
                boolean ordinaryDocument = "readme".equals(n) || "license".equals(n) || "licence".equals(n)
                        || "notice".equals(n) || "authors".equals(n) || "copying".equals(n) || "changelog".equals(n);
                if (n.endsWith(".pc") || n.endsWith(".la") || n.endsWith(".cnf") || n.endsWith(".tl")
                        || (!n.contains(".") && !ordinaryDocument)) return true;
            }
        }
        return false;
    }

    private String installLocalLibraryArchive(String scId, File archive, String filesPath) throws Exception {
        if (archive == null || !archive.isFile()) return null;
        String archiveName = archive.getName();
        String lower = archiveName.toLowerCase(Locale.ROOT);
        boolean aar = lower.endsWith(".aar");
        boolean jar = lower.endsWith(".jar");
        if (!aar && !jar) return null;

        String base = archiveName.substring(0, archiveName.lastIndexOf('.'))
                .replaceAll("[^A-Za-z0-9_.-]", "_");
        if (base.isEmpty()) base = "imported_library";
        File libraryRoot = new File(FileUtil.getExternalStorageDir(), ".sketchware/libs/local_libs");
        if (!libraryRoot.exists() && !libraryRoot.mkdirs() && !libraryRoot.isDirectory()) {
            throw new IOException("Could not create Sketchware local-library directory: " + libraryRoot);
        }
        String name = base;
        File target = new File(libraryRoot, name);
        if (target.exists()) {
            String digest = sha256File(archive).substring(0, 10);
            name = base + "_import_" + digest;
            target = new File(libraryRoot, name);
            int suffix = 2;
            while (target.exists()) target = new File(libraryRoot, name + "_" + suffix++);
            name = target.getName();
        }
        if (!target.mkdirs() && !target.isDirectory()) throw new IOException("Could not create local library folder: " + target);

        boolean success = false;
        try {
            if (jar) {
                java.nio.file.Files.copy(archive.toPath(), new File(target, "classes.jar").toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } else {
                extractLocalArchiveSafely(archive, target);
                int aarNativeCount = copySharedLibrariesOnly(new File(target, "jni"),
                        new File(filesPath, "native_libs"), new File(target, "jni"));
                if (aarNativeCount > 0) {
                    logger.info("Copied " + aarNativeCount + " native .so file(s) from " + archiveName
                            + " into files/native_libs/; ABI directories were preserved.");
                }
                File embeddedJar = new File(target, "classes.jar");
                if (!embeddedJar.isFile()) createEmptyJar(embeddedJar);
                File manifest = new File(target, "AndroidManifest.xml");
                if (manifest.isFile()) {
                    String packageName = extractManifestPackage(manifest);
                    if (!packageName.isEmpty()) writeUtf8(new File(target, "config"), packageName);
                }
                File consumer = new File(target, "consumer-rules.pro");
                File proguard = new File(target, "proguard.txt");
                if (!proguard.isFile() && consumer.isFile()) {
                    java.nio.file.Files.copy(consumer.toPath(), proguard.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }

            ArrayList<HashMap<String, Object>> existing = LocalLibrariesUtil.getLocalLibraries(scId);
            if (existing == null) existing = new ArrayList<>();
            boolean alreadyAdded = false;
            for (HashMap<String, Object> item : existing) {
                if (item != null && name.equals(String.valueOf(item.get("name")))) { alreadyAdded = true; break; }
            }
            if (!alreadyAdded) existing.add(LocalLibrariesUtil.createLibraryMap(name, null));
            LocalLibrariesUtil.rewriteLocalLibFile(scId, new Gson().toJson(existing));
            LocalLibrariesUtil.clearCache();
            success = true;
            return name;
        } finally {
            if (!success) deleteRecursively(target);
        }
    }

    private void extractLocalArchiveSafely(File archive, File destination) throws IOException {
        String root = destination.getCanonicalPath() + File.separator;
        long total = 0L;
        int count = 0;
        try (ZipFile zip = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buffer = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > 50000) throw new IOException("Local archive contains too many entries.");
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("/") || name.matches("^[A-Za-z]:.*")) throw new IOException("Unsafe local archive entry: " + name);
                for (String segment : name.split("/")) if ("..".equals(segment) || ".".equals(segment)) throw new IOException("Unsafe local archive path: " + name);
                File output = new File(destination, name);
                if (!output.getCanonicalPath().startsWith(root) && !output.getCanonicalPath().equals(destination.getCanonicalPath())) {
                    throw new IOException("Local archive entry escapes library destination: " + name);
                }
                if (entry.isDirectory()) {
                    if (!output.mkdirs() && !output.isDirectory()) throw new IOException("Cannot create local library directory: " + output);
                    continue;
                }
                File parent = output.getParentFile();
                if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Cannot create local library folder: " + parent);
                try (InputStream input = zip.getInputStream(entry); FileOutputStream outputStream = new FileOutputStream(output)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        total += read;
                        if (total > 512L * 1024L * 1024L) throw new IOException("Local archive expands beyond 512 MiB.");
                        outputStream.write(buffer, 0, read);
                    }
                }
            }
        }
    }

    private void createEmptyJar(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Cannot create directory for resource-only AAR.");
        try (java.util.jar.JarOutputStream ignored = new java.util.jar.JarOutputStream(new FileOutputStream(file))) { }
    }

    private String extractManifestPackage(File manifest) {
        try {
            String xml = readUtf8(manifest);
            java.util.regex.Matcher matcher = Pattern.compile("<manifest\\b[^>]*\\bpackage\\s*=\\s*[\\\"']([^\\\"']+)[\\\"']", Pattern.CASE_INSENSITIVE).matcher(xml);
            return matcher.find() ? matcher.group(1).trim() : "";
        } catch (Exception e) {
            logger.warning("Could not read package name from local AAR manifest: " + e.getMessage());
            return "";
        }
    }

    private String sha256File(File file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

     

    private File extractZip(String zipPath) {
        File zipFile = new File(zipPath);
        if (!zipFile.isFile() || zipFile.length() <= 0) {
            Log.e(TAG, "ZIP file not found or empty: " + zipPath);
            return null;
        }
        File tempDest = new File(context.getCacheDir(), "as_import_" + System.nanoTime());
        if (!tempDest.mkdirs() && !tempDest.isDirectory()) {
            logger.error("Cannot create temporary extraction directory.", null,
                    new ImportLogger.ErrorContext().file(tempDest.getAbsolutePath()));
            return null;
        }
        final int maxEntries = 50000;
        final long maxExpandedBytes = 1024L * 1024L * 1024L;
        byte[] buffer = new byte[16384];
        int entryCount = 0;
        long expandedBytes = 0;
        java.util.Set<String> seenPaths = new java.util.HashSet<>();
        String canonicalRoot;
        try {
            canonicalRoot = tempDest.getCanonicalPath() + File.separator;
            try (ZipInputStream zis = new ZipInputStream(new java.io.BufferedInputStream(
                    new java.io.FileInputStream(zipFile)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (++entryCount > maxEntries) throw new IOException("ZIP contains too many entries (limit " + maxEntries + ").");
                    String name = entry.getName();
                    if (name == null || name.trim().isEmpty() || name.startsWith("/")
                            || name.startsWith("\\") || name.matches("^[A-Za-z]:.*")) {
                        throw new IOException("ZIP contains an unsafe absolute entry path.");
                    }
                    for (String segment : name.replace('\\', '/').split("/")) {
                        if ("..".equals(segment) || ".".equals(segment)) throw new IOException("ZIP contains a traversal path segment.");
                    }
                    File dest = new File(tempDest, name);
                    String destPath = dest.getCanonicalPath();
                    if (!destPath.startsWith(canonicalRoot)) {
                        throw new IOException("ZIP entry attempts to escape the extraction directory.");
                    }
                    if (!seenPaths.add(destPath)) throw new IOException("ZIP contains duplicate entry paths.");
                    if (entry.isDirectory()) {
                        if (!dest.mkdirs() && !dest.isDirectory()) throw new IOException("Cannot create ZIP directory: " + name);
                    } else {
                        File parent = dest.getParentFile();
                        if (parent != null && !parent.mkdirs() && !parent.isDirectory()) {
                            throw new IOException("Cannot create ZIP directory for " + name);
                        }
                        try (FileOutputStream fos = new FileOutputStream(dest)) {
                            int len;
                            while ((len = zis.read(buffer)) != -1) {
                                expandedBytes += len;
                                if (expandedBytes > maxExpandedBytes) {
                                    throw new IOException("ZIP expands beyond the 1 GB import safety limit.");
                                }
                                fos.write(buffer, 0, len);
                            }
                        }
                    }
                    zis.closeEntry();
                }
            }
            if (entryCount == 0) throw new IOException("ZIP archive contains no entries.");
            Log.d(TAG, "ZIP extracted to: " + tempDest.getAbsolutePath() + " (" + entryCount + " entries)");
            return tempDest;
        } catch (IOException e) {
            Log.e(TAG, "ZIP extraction failed", e);
            logger.error("ZIP extraction failed", e, new ImportLogger.ErrorContext().file(zipPath));
            deleteRecursively(tempDest);
            return null;
        }
    }

     

    /** Finds the launcher screen name, preferring the manifest launcher Activity. */
    /** Converts Activity source into logic data; Custom Views do not have Activity logic sections. */
    private String buildFullLogicData(List<ClassifiedSource> sources,
                                      List<LayoutImporter.ImportedLayout> layouts,
                                      boolean extractCustomMethodsToMoreBlocks) {
        StringBuilder sb = new StringBuilder();
        JavaLogicConverter converter = new JavaLogicConverter();
        Set<String> convertedActivityScreens = new LinkedHashSet<>();

        for (ClassifiedSource cs : sources) {
            if (cs.kind != ClassifiedSource.Kind.ACTIVITY) continue;
            if (cs.file == null || !cs.file.exists()) continue;

            String fileName = cs.sketchwareFileName != null ? cs.sketchwareFileName
                    : SourceClassifier.classNameToSketchwareFileName(cs.simpleClassName);
            String activityJavaName = fileNameToActivityJavaName(fileName);

            ViewInfo viewInfo = extractImportedViewInfo(layouts, fileName);
            java.util.Set<String> knownViewIds = viewInfo.ids;
            Map<String, Integer> knownViewTypes = viewInfo.types;

            String javaSource;
            try {
                javaSource = new String(java.nio.file.Files.readAllBytes(cs.file.toPath()), "UTF-8");
            } catch (Exception e) {
                logger.warning("Could not read " + cs.file.getName() + " for logic conversion: " + e.getMessage());
                continue;
            }

            JavaLogicConverter.ConversionResult result;
            try {
                result = converter.convert(javaSource, knownViewIds, knownViewTypes, extractCustomMethodsToMoreBlocks);
            } catch (Exception e) {
                logger.warning("Logic conversion failed for " + cs.simpleClassName + ": " + e.getMessage()
                        + " — original source is still preserved as copied, unconverted.");
                Log.w(TAG, "JavaLogicConverter failed for " + cs.simpleClassName, e);
                continue;
            }

            convertedActivityScreens.add(fileName);
            sb.append("@").append(activityJavaName).append("_components\n");
            sb.append(result.componentsSection);
            sb.append("@").append(activityJavaName).append("_events\n");
            sb.append(result.eventsSection);
            if (!result.varLines.isEmpty()) {
                sb.append("@").append(activityJavaName).append("_var\n");
                for (String v : result.varLines) sb.append(v).append("\n");
            }
            if (extractCustomMethodsToMoreBlocks && result.functionDeclarations != null
                    && !result.functionDeclarations.trim().isEmpty()) {
                sb.append("@").append(activityJavaName).append("_func\n");
                sb.append(result.functionDeclarations);
            }
            // Write the empty initializeLogic section even when the source has no onCreate method.
             
             
            if (!result.sections.containsKey("onCreate_initializeLogic")) {
                sb.append("@").append(activityJavaName).append("_onCreate_initializeLogic\n");
            }
            for (Map.Entry<String, String> section : result.sections.entrySet()) {
                sb.append("@").append(activityJavaName).append("_").append(section.getKey()).append("\n");
                sb.append(section.getValue());
            }

            if (!result.unsupported.isEmpty()) {
                logger.warning(cs.simpleClassName + ": " + result.unsupported.size()
                        + " statement(s)/method(s) not converted to native blocks (preserved as "
                        + "addSourceDirectly or left in copied source only — see details below).");
                for (String u : result.unsupported) {
                    logger.info("  " + u);
                }
            }
        }

        // Add required empty lifecycle and event sections when source is missing.
         
        if (layouts != null) for (LayoutImporter.ImportedLayout layout : layouts) {
            if (layout == null || layout.fileType != ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    || convertedActivityScreens.contains(layout.sketchwareFileName)) continue;
            String activityJavaName = fileNameToActivityJavaName(layout.sketchwareFileName);
            sb.append("@").append(activityJavaName).append("_components\n");
            sb.append("@").append(activityJavaName).append("_events\n");
            sb.append("@").append(activityJavaName).append("_onCreate_initializeLogic\n");
            sb.append("@").append(activityJavaName).append("_onBackPressed_onBackPressed\n");
            logger.warning("Registered Activity '" + layout.sketchwareFileName
                    + "' has no convertible Java source; created valid empty logic sections.");
        }
        return sb.toString();
    }

    private static final class ViewInfo {
        final java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        final Map<String, Integer> types = new java.util.LinkedHashMap<>();
    }

    /** Reads imported widget data so event IDs and types match the actual screen. */
    private ViewInfo extractImportedViewInfo(List<LayoutImporter.ImportedLayout> layouts, String screenName) {
        ViewInfo info = new ViewInfo();
        if (layouts == null || screenName == null) return info;
        LayoutImporter.ImportedLayout match = null;
        for (LayoutImporter.ImportedLayout layout : layouts) {
            if (layout != null && screenName.equals(layout.sketchwareFileName)
                    && layout.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                match = layout;
                break;
            }
        }
        if (match == null || match.viewSection == null) return info;
        Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
        Pattern typePattern = Pattern.compile("\"type\"\\s*:\\s*(-?\\d+)");
        String[] lines = match.viewSection.split("\n");
        for (String line : lines) {
            if (line.startsWith("@" + screenName + ".xml_fab")) break;
            if (!line.startsWith("{")) continue;
            Matcher id = idPattern.matcher(line);
            Matcher type = typePattern.matcher(line);
            if (id.find() && type.find() && !"_fab".equals(id.group(1))) {
                try {
                    info.ids.add(id.group(1));
                    info.types.put(id.group(1), Integer.parseInt(type.group(1)));
                } catch (NumberFormatException ignored) { }
            }
        }
        return info;
    }

    /** ProjectFileBean.getActivityName(fileName) + ".java" — the same derivation SketchwareWriter uses. */
    private String fileNameToActivityJavaName(String fileName) {
        return ProjectFileBean.getActivityName(fileName) + ".java";
    }

    /** Applies SDK/build settings after the new project ID exists. Dependencies are added only when enabled. */
    private void applyImportedBuildSettings(String scId, ParsedGradle parsed,
                                            SketchwareWriter.WriteInput input, File appModuleDir, File projectRoot) throws Exception {
        ProjectSettings projectSettings = new ProjectSettings(scId);
        if (!parsed.explicitFields.contains(ParsedGradle.FIELD_MIN_SDK)) logger.warning("minSdk is not a literal Gradle value; using importer fallback " + parsed.minSdk + ". Review SDK aliases/flavors manually.");
        if (!parsed.explicitFields.contains(ParsedGradle.FIELD_TARGET_SDK)) logger.warning("targetSdk is not a literal Gradle value; using importer fallback " + parsed.targetSdk + ". Review SDK aliases/flavors manually.");
        if (!parsed.explicitFields.contains(ParsedGradle.FIELD_COMPILE_SDK)) logger.warning("compileSdk is not a literal Gradle value; using importer fallback " + parsed.compileSdk + ". Review SDK aliases/flavors manually.");
        projectSettings.setValue(ProjectSettings.SETTING_MINIMUM_SDK_VERSION, String.valueOf(parsed.minSdk));
        projectSettings.setValue(ProjectSettings.SETTING_TARGET_SDK_VERSION, String.valueOf(parsed.targetSdk));
        projectSettings.setValue(ProjectSettings.SETTING_COMPILE_SDK_VERSION, String.valueOf(parsed.compileSdk));
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING,
                parsed.viewBindingEnabled ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);

        String javaVersion = input.javaVersion;
        new BuildSettings(scId).setValue(BuildSettings.SETTING_JAVA_VERSION, javaVersion);
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA,
                options.convertJavaToBlocks ? ProjectSettings.SETTING_GENERIC_VALUE_FALSE
                        : ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        projectSettings.setValue("multidex", parsed.multiDexEnabled
                ? ProjectSettings.SETTING_GENERIC_VALUE_TRUE : ProjectSettings.SETTING_GENERIC_VALUE_FALSE);

        // Keep Neo's shrinker settings in sync with the imported Gradle release settings.
         
         
        ProguardHandler proguardHandler = new ProguardHandler(scId);
        proguardHandler.setProguardEnabled(parsed.minifyEnabledInRelease);
        if (parsed.explicitFields.contains(ParsedGradle.FIELD_R8_ENABLED)) {
            proguardHandler.setR8Enabled(parsed.r8Enabled);
        } else if (parsed.minifyEnabledInRelease) {
            proguardHandler.setR8Enabled(true);
        }

        File gradleFile = new File(appModuleDir, "build.gradle");
        if (!gradleFile.isFile()) gradleFile = new File(appModuleDir, "build.gradle.kts");
        String original = gradleFile.isFile()
                ? new String(java.nio.file.Files.readAllBytes(gradleFile.toPath()), StandardCharsets.UTF_8) : "";
        List<GradleDependency> dependencies = new ArrayList<>();
        if (options.importGradleDependencies) {
            dependencies.addAll(new GradleParser().parseDependencyList(original));
            dependencies.addAll(resolveVersionCatalogDependencies(original, projectRoot));
        } else {
            logger.info("Maven dependency transfer was disabled. SDK, Java compatibility, multidex, release minification and the compatible Custom Gradle template are still imported.");
        }
        jq metadata = new jq();
        metadata.sc_id = scId;
        metadata.packageName = input.packageName == null || input.packageName.trim().isEmpty()
                ? "com.imported.project" : input.packageName.trim();
        metadata.projectName = input.appName == null || input.appName.trim().isEmpty() ? "ImportedProject" : input.appName;
        metadata.versionCode = String.valueOf(input.versionCode);
        metadata.versionName = input.versionName == null ? "1.0" : input.versionName;
        metadata.g = true;
        String generated = Lx.getBuildGradleString(parsed.compileSdk, parsed.minSdk,
                String.valueOf(parsed.targetSdk), metadata, parsed.viewBindingEnabled);
        // Preserve the source minify setting in both the custom Gradle file and Neo's project settings.
        // Preserve the source project's explicit release setting in the imported custom
         
         
        if (parsed.minifyEnabledInRelease) {
            generated = generated.replace("minifyEnabled false", "minifyEnabled true");
        }
        if (parsed.multiDexEnabled) {
            generated = injectIntoDefaultConfig(generated, "multiDexEnabled true");
        }
        String javaVersionToken = toGradleJavaVersionToken(parsed.javaVersion);
        String compileOptions = "compileOptions {\r\n"
                + "    sourceCompatibility JavaVersion." + javaVersionToken + "\r\n"
                + "    targetCompatibility JavaVersion." + javaVersionToken + "\r\n"
                + "}\r\n\r\n";
        if (generated.contains("buildTypes {")) generated = generated.replace("buildTypes {", compileOptions + "buildTypes {");
        if (parsed.dataBindingEnabled) {
            logger.warning("Android dataBinding is enabled in the source project. Its generated binding classes and <layout> XML wrappers are not converted into Sketchware blocks; original sources/resources are preserved, but data-binding-specific code may require manual migration.");
        }
        if (parsed.hasKotlin) {
            logger.warning("Kotlin source/plugin declarations are detected and source files are preserved, but this importer does not claim to compile the original Kotlin/Compose plugin pipeline in Sketchware Neo's generated project. Review the retained Gradle files and Kotlin sources.");
        }

        Set<String> existing = new LinkedHashSet<>();
        Matcher existingCoordinates = Pattern.compile("['\\\"]([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::[^'\\\"]*)?['\\\"]").matcher(generated);
        while (existingCoordinates.find()) existing.add(existingCoordinates.group(1) + ":" + existingCoordinates.group(2));
        List<String> importedLines = new ArrayList<>();
        Set<String> added = new LinkedHashSet<>();
        Set<String> platformCoordinates = new LinkedHashSet<>();
        Matcher platformMatcher = Pattern.compile("(?m)\\b(?:implementation|api|runtimeOnly|compileOnly)\\s*\\(?\\s*platform\\s*\\(?\\s*['\"]([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::[^'\"]*)?['\"]").matcher(original);
        while (platformMatcher.find()) platformCoordinates.add(platformMatcher.group(1) + ":" + platformMatcher.group(2));
        for (GradleDependency dependency : dependencies) {
            if (dependency == null) continue;
            String cfg = dependency.configuration == null ? "" : dependency.configuration.trim();
            if (!("implementation".equals(cfg) || "api".equals(cfg) || "runtimeOnly".equals(cfg) || "compileOnly".equals(cfg))) {
                logger.warning("Skipped Gradle configuration '" + cfg + "' for " + dependency.coordinate() + " (tests/compiler plugins are not app runtime dependencies).");
                continue;
            }
            if (dependency.version == null || dependency.version.trim().isEmpty()) {
                logger.warning("Skipped dependency without an explicit version: " + dependency.coordinate() + ". Version-catalog/BOM-managed dependencies need manual review.");
                continue;
            }
            if (!dependency.group.matches("[A-Za-z0-9_.-]+") || !dependency.artifact.matches("[A-Za-z0-9_.-]+")
                    || !dependency.version.matches("[A-Za-z0-9_.+\\-]+")) {
                logger.warning("Skipped dependency with an unsupported coordinate/version: " + dependency.coordinate());
                continue;
            }
            String key = dependency.group + ":" + dependency.artifact;
            if (platformCoordinates.contains(key)) {
                logger.info("Kept BOM/platform dependency out of plain Maven declarations: " + key);
                continue;
            }
            if (existing.contains(key) || !added.add(key)) {
                logger.info("Dependency already supplied by Sketchware or duplicate; not added twice: " + key);
                continue;
            }
            String config = "api".equals(cfg) ? "implementation" : cfg;
            importedLines.add("    " + config + " '" + key + ":" + dependency.version + "'");
        }
        if (!importedLines.isEmpty()) {
            int end = generated.lastIndexOf("}");
            if (end < 0) throw new IOException("Generated Custom Gradle template is malformed: dependencies block was not found.");
            generated = generated.substring(0, end) + String.join("\r\n", importedLines) + "\r\n" + generated.substring(end);
        }

        File customGradleDir = new File(new FilePathUtil().getPathCustomGradle(scId));
        if (!customGradleDir.exists() && !customGradleDir.mkdirs()) {
            throw new IOException("Could not create Custom Gradle directory: " + customGradleDir.getAbsolutePath());
        }
        writeUtf8(new File(customGradleDir, "app_build.gradle"), generated);
        if (original.contains("plugins {") || original.contains("plugins{")) {
            java.util.regex.Matcher pluginMatcher = Pattern.compile("(?m)^\\s*id\\s+['\"]([^'\"]+)['\"](?:\\s+version\\s+['\"]([^'\"]+)['\"])?").matcher(original);
            while (pluginMatcher.find()) {
                String pluginId = pluginMatcher.group(1);
                if (!"com.android.application".equals(pluginId)
                        && !"org.jetbrains.kotlin.android".equals(pluginId)
                        && !"kotlin-android".equals(pluginId)) {
                    logger.warning("Custom Gradle plugin '" + pluginId + "' is retained in files/android_studio_original/gradle_project, but was not executed/translated automatically. Verify its plugin version and required root pluginManagement/buildscript configuration before enabling it.");
                }
            }
        }
        writeUtf8(new File(customGradleDir, "build.gradle"), Lx.c("8.12.0", "4.4.2"));
        writeUtf8(new File(customGradleDir, "settings.gradle"), Lx.a());
        writeUtf8(new File(customGradleDir, "gradle.properties"),
                "android.useAndroidX=true\nandroid.enableJetifier=true\norg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8\n");
        projectSettings.setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
        logger.info("Custom Gradle configured. Imported " + importedLines.size() + " direct dependency declaration(s); unsupported/BOM/catalog dependencies are recorded as warnings.");
    }


    private String injectIntoDefaultConfig(String buildScript, String statement) {
        if (buildScript == null || statement == null || statement.trim().isEmpty()) return buildScript;
        if (buildScript.contains(statement)) return buildScript;
        Matcher matcher = Pattern.compile("(?s)(defaultConfig\\s*\\{)(.*?)(\\n\\s*\\})").matcher(buildScript);
        if (!matcher.find()) {
            logger.warning("Could not add '" + statement + "' because generated Gradle has no recognizable defaultConfig block.");
            return buildScript;
        }
        String body = matcher.group(2);
        return buildScript.substring(0, matcher.start()) + matcher.group(1) + body
                + "\r\n    " + statement + matcher.group(3) + buildScript.substring(matcher.end());
    }

    private String supportedJavaVersion(String rawVersion) {
        String version = rawVersion == null ? "1.8"
                : rawVersion.trim().replace("JavaVersion.", "").replace("VERSION_", "").replace('_', '.');
        if ("8".equals(version)) version = "1.8";
        if ("1.8".equals(version) || "11".equals(version) || "17".equals(version)) return version;
        logger.warning("Source Java target '" + version + "' is not supported by this Sketchware Neo settings profile; using Java 17 while preserving the original Gradle scripts for review.");
        return "17";
    }

    private String toGradleJavaVersionToken(String rawVersion) {
        String version = rawVersion == null ? "17"
                : rawVersion.trim().replace("JavaVersion.", "").replace("VERSION_", "");
        if ("1.8".equals(version) || "8".equals(version)) return "VERSION_1_8";
        if ("11".equals(version)) return "VERSION_11";
        if ("17".equals(version)) return "VERSION_17";
        logger.warning("Source Java version '" + version + "' is not represented by this Sketchware Neo build-settings schema; using Java 17 in generated Gradle and Neo settings. Newer language features may need migration.");
        return "VERSION_17";
    }

    /** Reads simple version-catalog aliases without running Gradle scripts. Unresolved aliases are reported. */
    private List<GradleDependency> resolveVersionCatalogDependencies(String buildFile, File projectRoot) {
        List<GradleDependency> result = new ArrayList<>();
        if (buildFile == null || projectRoot == null) return result;
        java.util.regex.Pattern usePattern = Pattern.compile(
                "(?m)\\b(implementation|api|runtimeOnly|compileOnly)\\s*\\(?\\s*libs\\.([A-Za-z0-9_.-]+)");
        Matcher uses = usePattern.matcher(buildFile);
        if (!uses.find()) return result;
        File catalog = new File(new File(projectRoot, "gradle"), "libs.versions.toml");
        if (!catalog.isFile()) {
            logger.warning("Gradle Version Catalog aliases are used but gradle/libs.versions.toml was not found; aliases cannot be resolved safely.");
            return result;
        }
        try {
            Map<String, String> versions = new java.util.LinkedHashMap<>();
            Map<String, String> libraries = new java.util.LinkedHashMap<>();
            String section = "";
            String currentKey = null;
            StringBuilder currentValue = new StringBuilder();
            for (String raw : java.nio.file.Files.readAllLines(catalog.toPath(), StandardCharsets.UTF_8)) {
                String line = raw.replaceFirst("\\s+#.*$", "").trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    if (currentKey != null) {
                        if ("versions".equals(section)) versions.put(currentKey, currentValue.toString());
                        if ("libraries".equals(section)) libraries.put(currentKey, currentValue.toString());
                        currentKey = null; currentValue.setLength(0);
                    }
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                if (!("versions".equals(section) || "libraries".equals(section))) continue;
                Matcher declaration = Pattern.compile("^([A-Za-z0-9_.-]+)\\s*=\\s*(.*)$").matcher(line);
                if (declaration.find()) {
                    if (currentKey != null) {
                        if ("versions".equals(section)) versions.put(currentKey, currentValue.toString());
                        if ("libraries".equals(section)) libraries.put(currentKey, currentValue.toString());
                    }
                    currentKey = declaration.group(1);
                    currentValue.setLength(0);
                    currentValue.append(declaration.group(2));
                } else if (currentKey != null) currentValue.append(' ').append(line);
                if (currentKey != null && !line.endsWith(",") && currentValue.toString().trim().endsWith("}")) {
                    // Finish only when brace depth is balanced; supports basic multiline inline tables.
                    int depth = 0; boolean quoted = false; char prev = 0;
                    for (char c : currentValue.toString().toCharArray()) {
                        if (c == '"' && prev != '\\') quoted = !quoted;
                        if (!quoted) { if (c == '{') depth++; else if (c == '}') depth--; }
                        prev = c;
                    }
                    if (depth <= 0) {
                        if ("versions".equals(section)) versions.put(currentKey, currentValue.toString());
                        if ("libraries".equals(section)) libraries.put(currentKey, currentValue.toString());
                        currentKey = null; currentValue.setLength(0);
                    }
                }
            }
            if (currentKey != null) {
                if ("versions".equals(section)) versions.put(currentKey, currentValue.toString());
                if ("libraries".equals(section)) libraries.put(currentKey, currentValue.toString());
            }
            Map<String, String[]> resolved = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, String> entry : libraries.entrySet()) {
                String key = entry.getKey(); String value = entry.getValue();
                String group = tomlString(value, "group");
                String artifact = tomlString(value, "name");
                String module = tomlString(value, "module");
                if (module != null && module.contains(":")) { group = module.substring(0, module.indexOf(':')); artifact = module.substring(module.indexOf(':') + 1); }
                if (group == null || artifact == null || !group.matches("[A-Za-z0-9_.-]+") || !artifact.matches("[A-Za-z0-9_.-]+")) continue;
                String version = tomlString(value, "version");
                Matcher ref = Pattern.compile("version\\.ref\\s*=\\s*\"([^\"]+)\"").matcher(value);
                boolean hasRef = ref.find();
                if (!hasRef) {
                    ref = Pattern.compile("version\\s*=\\s*\\{\\s*ref\\s*=\\s*\"([^\"]+)\"").matcher(value);
                    hasRef = ref.find();
                }
                if (hasRef) version = tomlVersion(versions.get(ref.group(1)));
                else if (version == null) {
                    Matcher nested = Pattern.compile("version\\s*=\\s*\\{[^}]*\\b(?:strictly|require|prefer)\\s*=\\s*\"([^\"]+)\"").matcher(value);
                    if (nested.find()) version = nested.group(1);
                }
                String accessor = key.replace('-', '.').replace('_', '.').replaceAll("\\.+", ".").toLowerCase(java.util.Locale.ROOT);
                resolved.put(accessor, new String[]{group, artifact, version == null ? "" : version});
            }
            uses.reset();
            while (uses.find()) {
                String config = uses.group(1); String alias = uses.group(2).replaceAll("\\.+", ".").toLowerCase(java.util.Locale.ROOT);
                String[] coord = resolved.get(alias);
                if (coord == null) {
                    logger.warning("Unresolved Gradle Version Catalog alias libs." + alias + "; original source is preserved but dependency must be reviewed manually.");
                    continue;
                }
                String version = coord[2].isEmpty() ? null : coord[2];
                result.add(new GradleDependency(config, coord[0], coord[1], version));
            }
        } catch (Exception e) {
            logger.warning("Could not safely parse Gradle Version Catalog: " + e.getMessage());
        }
        return result;
    }

    private static String tomlString(String value, String key) {
        if (value == null) return null;
        Matcher matcher = Pattern.compile("(?:^|[,\\s{])" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\"").matcher(value);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String tomlVersion(String value) {
        if (value == null) return null;
        Matcher m = Pattern.compile("^\\s*\"([^\"]+)\"").matcher(value.trim());
        return m.find() ? m.group(1) : null;
    }

    private static void writeUtf8(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Could not create directory: " + parent);
        java.nio.file.Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private String resolveLauncherFileName(List<ClassifiedSource> sources, ParsedManifest manifest) {
        String launcherClassName = null;
        for (ParsedManifest.ActivityEntry entry : manifest.activities) {
            if (entry.isLauncher) {
                launcherClassName = entry.simpleClassName;
                break;
            }
        }

        if (launcherClassName != null) {
            for (ClassifiedSource cs : sources) {
                if (cs.kind == ClassifiedSource.Kind.ACTIVITY
                        && launcherClassName.equals(cs.simpleClassName)
                        && cs.sketchwareFileName != null) {
                    return cs.sketchwareFileName;
                }
            }
            logger.warning("Manifest launcher activity " + launcherClassName
                    + " has no matching imported screen — falling back.");
        }

        for (ClassifiedSource cs : sources) {
            if (cs.kind == ClassifiedSource.Kind.ACTIVITY && cs.sketchwareFileName != null) {
                return cs.sketchwareFileName;
            }
        }

        return "main";
    }

    /** Builds the data/permission JSON array from manifest &lt;uses-permission&gt; strings. */
    private String buildPermissionsJson(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < permissions.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(permissions.get(i).replace("\"", "\\\"")).append("\"");
        }
        return sb.append("]").toString();
    }

    private String buildManifestAttributesJson(ParsedManifest manifest, List<ClassifiedSource> sources) {
        JSONArray array = new JSONArray();
        if (manifest == null) return array.toString();
        try {
            if (manifest.permissionEntries != null && !manifest.permissionEntries.isEmpty()) {
                for (ParsedManifest.PermissionEntry permission : manifest.permissionEntries) {
                    if (permission == null || permission.name == null || permission.name.trim().isEmpty()) continue;
                    JSONObject item = new JSONObject();
                    item.put("name", "_application_permissions");
                    String attributes = joinManifestAttributes(permission.attributes);
                    if (attributes.isEmpty()) attributes = "android:name=\"" + xmlAttributeValue(permission.name) + "\"";
                    item.put("value", attributes);
                    array.put(item);
                }
            } else {
                for (String permission : manifest.permissions) {
                    JSONObject item = new JSONObject();
                    item.put("name", "_application_permissions");
                    item.put("value", "android:name=\"" + xmlAttributeValue(permission) + "\"");
                    array.put(item);
                }
            }
            String appAttrs = joinManifestAttributes(manifest.applicationAttributes);
            if (!appAttrs.isEmpty()) {
                JSONObject item = new JSONObject();
                item.put("name", "_application_attrs");
                item.put("value", appAttrs);
                array.put(item);
            }
            for (ParsedManifest.ActivityEntry activity : manifest.activities) {
                String className = generatedActivityClassName(activity, sources);
                if (className == null || className.isEmpty()) continue;
                String attrs = joinManifestAttributes(activity.androidAttributes);
                if (!attrs.isEmpty()) {
                    JSONObject item = new JSONObject();
                    item.put("name", className);
                    item.put("value", attrs);
                    array.put(item);
                }
            }
        } catch (JSONException e) {
            logger.warning("Could not serialize AndroidManifest attribute injections; some attributes will need manual review: " + e.getMessage());
        }
        return array.toString();
    }

    private String buildActivityComponentsJson(ParsedManifest manifest, List<ClassifiedSource> sources) {
        JSONArray array = new JSONArray();
        if (manifest == null) return array.toString();
        try {
            for (ParsedManifest.ActivityEntry activity : manifest.activities) {
                String className = generatedActivityClassName(activity, sources);
                if (className == null || className.isEmpty()) continue;
                String childXml = stripDefaultLauncherIntentFilter(activity.childComponentsXml);
                if (childXml.trim().isEmpty()) continue;
                JSONObject item = new JSONObject();
                item.put("name", className);
                item.put("value", childXml.trim());
                array.put(item);
            }
        } catch (JSONException e) {
            logger.warning("Could not serialize Activity manifest child elements: " + e.getMessage());
        }
        return array.toString();
    }

    private String generatedActivityClassName(ParsedManifest.ActivityEntry activity, List<ClassifiedSource> sources) {
        if (activity == null) return null;
        for (ClassifiedSource source : sources) {
            if (source != null && source.kind == ClassifiedSource.Kind.ACTIVITY
                    && activity.simpleClassName.equals(source.simpleClassName)) {
                String fileName = source.sketchwareFileName;
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = SourceClassifier.classNameToSketchwareFileName(source.simpleClassName);
                }
                return ProjectFileBean.getActivityName(fileName);
            }
        }
        // Use Neo's generated screen name for Activities without source files.
         
        return ProjectFileBean.getActivityName(SourceClassifier.classNameToSketchwareFileName(activity.simpleClassName));
    }

    private String buildApplicationComponentsXml(ParsedManifest manifest) {
        if (manifest == null) return "";
        StringBuilder sb = new StringBuilder();
        appendApplicationComponents(sb, manifest.services, manifest.packageName);
        appendApplicationComponents(sb, manifest.receivers, manifest.packageName);
        appendApplicationComponents(sb, manifest.providers, manifest.packageName);
        return sb.toString().trim();
    }

    private void appendApplicationComponents(StringBuilder out, List<ParsedManifest.ComponentEntry> entries,
                                             String originalPackage) {
        if (entries == null) return;
        for (ParsedManifest.ComponentEntry entry : entries) {
            if (entry == null || entry.rawXml == null || entry.rawXml.trim().isEmpty()) continue;
            String xml = entry.rawXml;
            String rawClass = entry.rawClassName == null ? "" : entry.rawClassName.trim();
            if (!rawClass.isEmpty()) {
                String fqcn = rawClass.startsWith(".") ? originalPackage + rawClass
                        : (rawClass.indexOf('.') < 0 ? originalPackage + "." + rawClass : rawClass);
                xml = xml.replaceFirst("android:name=\"[^\"]*\"",
                        java.util.regex.Matcher.quoteReplacement("android:name=\"" + xmlAttributeValue(fqcn) + "\""));
            }
            out.append(xml).append('\n');
        }
    }

    private String joinManifestAttributes(Map<String, String> attrs) {
        if (attrs == null || attrs.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : attrs.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (name == null || name.trim().isEmpty() || value == null) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(name).append("=\"").append(xmlAttributeValue(value)).append("\"");
        }
        return sb.toString();
    }

    private String xmlAttributeValue(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private String stripDefaultLauncherIntentFilter(String rawXml) {
        if (rawXml == null || rawXml.trim().isEmpty()) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(?is)<intent-filter\\b[^>]*>.*?</intent-filter>");
        java.util.regex.Matcher matcher = pattern.matcher(rawXml);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String block = matcher.group();
            boolean launcher = block.contains("android.intent.action.MAIN")
                    && block.contains("android.intent.category.LAUNCHER");
            matcher.appendReplacement(out, launcher ? "" : java.util.regex.Matcher.quoteReplacement(block));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String readImportedProguardRules(ParsedGradle gradle, File appModuleDir) {
        StringBuilder combined = new StringBuilder();
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        // Read user .pro files from proguardFiles/consumerProguardFiles, not getDefaultProguardFile().
         
         
         
        if (appModuleDir != null) {
            File gradleFile = new File(appModuleDir, "build.gradle");
            if (!gradleFile.isFile()) gradleFile = new File(appModuleDir, "build.gradle.kts");
            if (gradleFile.isFile()) {
                try {
                    String buildText = new String(java.nio.file.Files.readAllBytes(gradleFile.toPath()), StandardCharsets.UTF_8);
                    Matcher declaration = Pattern.compile("(?is)\\b(?:consumerProguardFiles?|proguardFiles?)\\b([^;\\n]*(?:\\n(?!\\s*\\}).*){0,8})").matcher(buildText);
                    while (declaration.find()) {
                        Matcher fileName = Pattern.compile("['\"]([^'\"]+\\.pro)['\"]", Pattern.CASE_INSENSITIVE).matcher(declaration.group(1));
                        while (fileName.find()) {
                            String relative = fileName.group(1).trim();
                            if (relative.isEmpty() || relative.contains("${") || relative.contains("$")) {
                                logger.warning("Could not resolve dynamic ProGuard rules path '" + relative + "'; original Gradle script is preserved for manual review.");
                                continue;
                            }
                            if (new File(relative).isAbsolute()) paths.add(relative);
                            else paths.add(new File(appModuleDir, relative).getAbsolutePath());
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Could not inspect Gradle ProGuard file declarations: " + e.getMessage());
                }
            }
            paths.add(new File(appModuleDir, "proguard-rules.pro").getAbsolutePath());
            paths.add(new File(appModuleDir, "consumer-rules.pro").getAbsolutePath());
        }
        if (gradle != null && gradle.proguardRulesPath != null
                && gradle.proguardRulesPath.toLowerCase(Locale.ROOT).endsWith(".pro")) {
            paths.add(gradle.proguardRulesPath);
        }
        if (gradle != null && gradle.consumerRulesPath != null) paths.add(gradle.consumerRulesPath);

        for (String path : paths) {
            if (path == null) continue;
            File file = new File(path);
            if (!file.isFile()) continue;
            try {
                String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                if (!content.trim().isEmpty()) {
                    if (combined.length() > 0) combined.append("\n\n");
                    combined.append("# Imported from ").append(file.getName()).append('\n').append(content.trim());
                    logger.info("Preserved ProGuard/consumer rules from " + file.getAbsolutePath());
                }
            } catch (Exception e) {
                logger.warning("Could not read ProGuard rules " + file.getName() + ": " + e.getMessage());
            }
        }
        return combined.toString();
    }

    private String buildCustomManifest(ParsedManifest manifest) {
        // Keep the original manifest for elements that Neo cannot inject, such as top-level <queries>.
         
         
         
        return "";
    }

     

    /** Best-effort recursive deletion used for temporary folders and failed installs. */
    private static boolean deleteRecursively(File target) {
        if (target == null || !target.exists()) return true;
        boolean success = true;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children == null) {
                success = false;
            } else {
                for (File child : children) {
                    if (!deleteRecursively(child)) success = false;
                }
            }
        }
        if (target.exists() && !target.delete()) success = false;
        return success;
    }

    private void cleanupTemp() {
        File directory = tempDir;
        tempDir = null;
        if (directory == null || !directory.exists()) return;
        if (deleteRecursively(directory)) {
            logger.info("Temp directory deleted: " + directory.getAbsolutePath());
            Log.d(TAG, "Temp directory cleaned up.");
        } else {
            logger.warning("Could not completely delete temporary import directory: "
                    + directory.getAbsolutePath());
        }
    }

    /** Counts regular files recursively; unreadable/missing directories count as zero. */
    private int countFiles(File directory) {
        if (directory == null || !directory.exists()) return 0;
        if (!directory.isDirectory()) return 1;
        File[] children = directory.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) count += countFiles(child);
        return count;
    }

    /** Counts resources in directories whose names begin with one of the given prefixes. */
    private int countFilesInPrefixedDirs(File resDir, String... prefixes) {
        if (resDir == null || !resDir.isDirectory() || prefixes == null || prefixes.length == 0) return 0;
        File[] children = resDir.listFiles();
        if (children == null) return 0;
        int count = 0;
        for (File child : children) {
            if (!child.isDirectory()) continue;
            String name = child.getName().toLowerCase(Locale.ROOT);
            for (String prefix : prefixes) {
                if (prefix != null && name.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                    count += countFiles(child);
                    break;
                }
            }
        }
        return count;
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void progress(String message) {
        String safeMessage = message == null ? "Working…" : message;
        Log.d(TAG, safeMessage);
        publishProgress(safeMessage);
    }

    private Result failure(String reason) {
        Log.e(TAG, "Import failed: " + reason);
        return new Result(reason, false);
    }
}
