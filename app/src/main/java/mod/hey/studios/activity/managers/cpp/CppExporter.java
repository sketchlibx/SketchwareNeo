package mod.hey.studios.activity.managers.cpp;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import a.a.a.yq;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Orchestrates all C/C++ export operations for both targets:
 *
 * <ol>
 *   <li><strong>Android Studio export</strong> ({@link #processForAndroidStudioExport}) —
 *       copies source files, generates CMakeLists.txt, injects Gradle cmake blocks.
 *       Called from {@code yq.generateGradleFiles()} at the end of the AS export pipeline.</li>
 *   <li><strong>APK build</strong> ({@link #hasCompiledNativeLibs}) — check used by
 *       {@code ProjectBuilder.buildApk()} to decide whether to add a third
 *       {@code ApkBuilder.addNativeLibraries()} hook for on-device compiled .so files.</li>
 * </ol>
 *
 * <h3>Priority 2 — Android Studio export</h3>
 * <pre>
 *   exportSrc() in ExportProjectActivity
 *     └─ yq.generateGradleFiles()
 *          └─ CppExporter.processForAndroidStudioExport(yq, sc_id)
 *               ├─ copySourceFiles()           → app/src/main/cpp/
 *               ├─ generateCmakeLists()        → app/CMakeLists.txt
 *               └─ injectGradleBlocks()        → app/build.gradle (in-place)
 * </pre>
 *
 * <h3>Priority 3 — APK native library packaging</h3>
 * <pre>
 *   ProjectBuilder.buildApk()
 *     └─ if (CppExporter.hasCompiledNativeLibs(yq))
 *            apkBuilder.addNativeLibraries(new File(yq.compiledNativeLibsPath));
 * </pre>
 */
public final class CppExporter {

    private static final String TAG = "CppExporter";

    /**
     * ABI targets included in {@code abiFilters} inside the generated Gradle block.
     * Both 32-bit and 64-bit ARM are supported; x86/x86_64 are excluded since
     * Sketchware builds are ARM-focused and x86 emulator support is a separate concern.
     */
    private static final String ABI_FILTERS = "\"armeabi-v7a\", \"arm64-v8a\"";

    private CppExporter() {}

    // =========================================================================
    // Priority 2 — Android Studio export
    // =========================================================================

    /**
     * Entry point for Android Studio export.
     *
     * <p>Called at the end of {@code yq.generateGradleFiles()} after the Gradle files
     * have been written to disk. Performs three operations only if the project
     * contains at least one C/C++ source or header file:
     *
     * <ol>
     *   <li>Copies {@code .sketchware/data/{sc_id}/files/cpp/} → {@code yq.cppFilesPath}</li>
     *   <li>Writes {@code app/CMakeLists.txt} (skips if already present)</li>
     *   <li>Injects {@code externalNativeBuild} blocks into {@code app/build.gradle}</li>
     * </ol>
     *
     * <p>Failure in any step is logged but does NOT throw — the overall export
     * continues so the user still gets a valid (non-native) project rather than
     * a hard build failure.
     *
     * @param metadata  The {@code yq} instance for the current export, providing all paths.
     * @param sc_id     Sketchware project identifier.
     */
    public static void processForAndroidStudioExport(yq metadata, String sc_id) {
        String cppSourcePath = new FilePathUtil().getPathCpp(sc_id);

        if (!hasCppFiles(cppSourcePath)) {
            Log.d(TAG, "No C/C++ files found for sc_id=" + sc_id + ", skipping export.");
            return;
        }

        Log.d(TAG, "C/C++ files detected for sc_id=" + sc_id + ". Starting export.");

        try {
            copySourceFiles(cppSourcePath, metadata.cppFilesPath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy C/C++ source files: " + e.getMessage(), e);
            // Non-fatal: continue so the rest of the project exports correctly.
            return;
        }

        try {
            generateCmakeListsIfAbsent(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate CMakeLists.txt: " + e.getMessage(), e);
            return;
        }

        try {
            injectGradleBlocks(metadata);
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject cmake Gradle blocks: " + e.getMessage(), e);
        }
    }

    // ── Step 1: Copy source files ─────────────────────────────────────────────

    /**
     * Copies everything under {@code srcPath} into {@code destPath}, creating
     * {@code destPath} if it does not exist.
     *
     * <p>Preserves the full sub-directory structure so folder hierarchies inside
     * the user's cpp manager are retained in the exported project.
     *
     * @param srcPath   {@code .sketchware/data/{sc_id}/files/cpp}
     * @param destPath  {@code {projectMyscPath}/app/src/main/cpp}
     */
    private static void copySourceFiles(String srcPath, String destPath) throws IOException {
        FileUtil.makeDir(destPath);

        ArrayList<String> items = new ArrayList<>();
        FileUtil.listDir(srcPath, items);

        if (items.isEmpty()) {
            Log.d(TAG, "cpp source directory is empty — nothing to copy.");
            return;
        }

        for (String itemPath : items) {
            File src  = new File(itemPath);
            File dest = new File(destPath, src.getName());
            FileUtil.copyDirectory(src, dest);
        }

        Log.d(TAG, "Copied " + items.size() + " item(s) from " + srcPath + " to " + destPath);
    }

    // ── Step 2: CMakeLists.txt ────────────────────────────────────────────────

    /**
     * Writes {@code app/CMakeLists.txt} if it does not already exist.
     *
     * <p>The CMake path is at the {@code app/} level (not inside {@code app/src/main/cpp/})
     * because that is where AGP expects it when using
     * {@code externalNativeBuild { cmake { path "CMakeLists.txt" } }}.
     *
     * <p>The CMakeLists.txt uses {@code CMAKE_CURRENT_SOURCE_DIR} with a relative path
     * to {@code src/main/cpp} so the path resolves correctly from the {@code app/} directory.
     */
    private static void generateCmakeListsIfAbsent(yq metadata) {
        // app/CMakeLists.txt — alongside build.gradle
        String cmakePath = metadata.projectMyscPath + "app" + File.separator + "CMakeLists.txt";

        if (FileUtil.isExistFile(cmakePath)) {
            Log.d(TAG, "CMakeLists.txt already exists at " + cmakePath + " — skipping generation.");
            return;
        }

        String content = CmakeListsGenerator.generate(metadata.projectName);

        // The generated CMakeLists.txt lives in app/ and references src/main/cpp via
        // CMAKE_CURRENT_SOURCE_DIR. We need to adjust the GLOB path to be relative:
        // Replace the self-referencing pattern with the correct relative path.
        content = content.replace(
                "\"${CMAKE_CURRENT_SOURCE_DIR}/*.c\"",
                "\"${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/*.c\"");
        content = content.replace(
                "\"${CMAKE_CURRENT_SOURCE_DIR}/*.cpp\"",
                "\"${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp/*.cpp\"");
        content = content.replace(
                "${CMAKE_CURRENT_SOURCE_DIR})",
                "${CMAKE_CURRENT_SOURCE_DIR}/src/main/cpp)");

        FileUtil.writeFile(cmakePath, content);
        Log.d(TAG, "Generated CMakeLists.txt at " + cmakePath);
    }

    // ── Step 3: Gradle injection ──────────────────────────────────────────────

    /**
     * Reads {@code app/build.gradle}, injects two cmake-related blocks, then
     * writes the modified content back. Both injections are idempotent — they check
     * for existing keywords before modifying.
     *
     * <h4>Injected blocks</h4>
     * <p><strong>Inside {@code defaultConfig { }}</strong>:
     * <pre>{@code
     *     externalNativeBuild {
     *         cmake {
     *             cppFlags "-std=c++17"
     *             abiFilters "armeabi-v7a", "arm64-v8a"
     *         }
     *     }
     * }</pre>
     *
     * <p><strong>Inside {@code android { }}</strong> (at the block level, not inside defaultConfig):
     * <pre>{@code
     *     externalNativeBuild {
     *         cmake {
     *             path "CMakeLists.txt"
     *             version "3.22.1"
     *         }
     *     }
     * }</pre>
     */
    private static void injectGradleBlocks(yq metadata) {
        String appGradlePath =
                metadata.projectMyscPath + "app" + File.separator + "build.gradle";

        if (!FileUtil.isExistFile(appGradlePath)) {
            Log.w(TAG, "app/build.gradle not found at " + appGradlePath
                    + " — cannot inject cmake blocks.");
            return;
        }

        String original = FileUtil.readFile(appGradlePath);
        if (original == null || original.trim().isEmpty()) {
            Log.w(TAG, "app/build.gradle is empty — skipping cmake injection.");
            return;
        }

        // Injection order matters for correctness:
        // 1. Inject into defaultConfig first (deeper nesting, earlier in file).
        // 2. Then inject the android-level block (later in the file, so indices
        //    from step 1 don't affect step 2 after the string is rebuilt).
        String modified = CmakeListsGenerator.injectDefaultConfigCmakeBlock(original, ABI_FILTERS);
        modified        = CmakeListsGenerator.injectAndroidLevelCmakeBlock(modified);

        if (modified.equals(original)) {
            Log.d(TAG, "app/build.gradle already contains cmake blocks — nothing injected.");
            return;
        }

        FileUtil.writeFile(appGradlePath, modified);
        Log.d(TAG, "Injected externalNativeBuild cmake blocks into app/build.gradle.");
    }

    // =========================================================================
    // Priority 3 — APK native library packaging
    // =========================================================================

    /**
     * Returns {@code true} if the {@code compiledNativeLibsPath} directory exists
     * and contains at least one ABI subdirectory with a {@code .so} file.
     *
     * <p>Called by {@code ProjectBuilder.buildApk()} to decide whether to add the
     * third {@code apkBuilder.addNativeLibraries()} hook.
     *
     * <p>Checking for actual {@code .so} files (not just directory existence) avoids
     * passing an empty directory to {@code ApkBuilder}, which could cause an APK
     * creation exception depending on the implementation.
     *
     * @param metadata  Current project metadata.
     * @return          {@code true} if packagable native libs are present.
     */
    public static boolean hasCompiledNativeLibs(yq metadata) {
        File dir = new File(metadata.compiledNativeLibsPath);
        if (!dir.exists() || !dir.isDirectory()) return false;

        // Expect structure: {compiledNativeLibsPath}/{ABI}/*.so
        File[] abiDirs = dir.listFiles(File::isDirectory);
        if (abiDirs == null || abiDirs.length == 0) return false;

        for (File abiDir : abiDirs) {
            File[] soFiles = abiDir.listFiles(
                    f -> f.isFile() && f.getName().endsWith(".so"));
            if (soFiles != null && soFiles.length > 0) return true;
        }

        return false;
    }

    // =========================================================================
    // Shared detection utility
    // =========================================================================

    /**
     * Returns {@code true} if {@code cppSourcePath} exists and contains at least one
     * {@code .c}, {@code .cpp}, {@code .h}, or {@code .hpp} file (recursively).
     *
     * <p>Used both here and by {@link CppValidator} to avoid unnecessary work.
     *
     * @param cppSourcePath  Absolute path to the project's cpp source directory.
     * @return               {@code true} if at least one C/C++ file is present.
     */
    public static boolean hasCppFiles(String cppSourcePath) {
        if (!FileUtil.isExistFile(cppSourcePath)) return false;
        return findCppFileRecursive(new File(cppSourcePath));
    }

    private static boolean findCppFileRecursive(File dir) {
        File[] children = dir.listFiles();
        if (children == null) return false;

        for (File child : children) {
            if (child.isDirectory()) {
                if (findCppFileRecursive(child)) return true;
            } else {
                String lower = child.getName().toLowerCase();
                if (lower.endsWith(".c")   || lower.endsWith(".cpp")
                 || lower.endsWith(".h")   || lower.endsWith(".hpp")) {
                    return true;
                }
            }
        }
        return false;
    }
}
