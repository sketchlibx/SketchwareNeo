package mod.hey.studios.activity.managers.cpp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Static helper for all C/C++ build and export operations.
 * Used by ManageCppActivity (file management) and yq (AS export + gradle injection).
 */
public final class CppBuildHelper {

    private CppBuildHelper() {}

    // ── CMakeLists.txt template ───────────────────────────────────────────────
    private static final String CMAKE_TEMPLATE =
            "cmake_minimum_required(VERSION 3.22.1)\n\n" +
            "project(\"%s\")\n\n" +
            "# Collect all C/C++ sources recursively\n" +
            "file(GLOB_RECURSE CPP_SOURCES\n" +
            "        \"src/main/cpp/*.c\"\n" +
            "        \"src/main/cpp/*.cpp\")\n\n" +
            "add_library(\n" +
            "        %s\n" +
            "        SHARED\n" +
            "        ${CPP_SOURCES})\n\n" +
            "# Link against the Android log library\n" +
            "find_library(log-lib log)\n\n" +
            "target_link_libraries(\n" +
            "        %s\n" +
            "        ${log-lib})\n";

    // ── Gradle externalNativeBuild block ─────────────────────────────────────
    private static final String GRADLE_CMAKE_BLOCK =
            "\n    externalNativeBuild {\n" +
            "        cmake {\n" +
            "            path \"CMakeLists.txt\"\n" +
            "            version \"3.22.1\"\n" +
            "        }\n" +
            "    }\n";

    // ── JNI bridge template ───────────────────────────────────────────────────
    private static final String JNI_TEMPLATE =
            "// %s.cpp  — JNI Bridge\n" +
            "//\n" +
            "// Java-side declaration (add to your Activity):\n" +
            "//   static { System.loadLibrary(\"%s\"); }\n" +
            "//   public native String nativeGreet();\n\n" +
            "#include <jni.h>\n" +
            "#include <string>\n\n" +
            "extern \"C\" JNIEXPORT jstring JNICALL\n" +
            "Java_%s_%s_nativeGreet(\n" +
            "        JNIEnv* env,\n" +
            "        jobject /* thiz */) {\n" +
            "    std::string message = \"Hello from C++!\";\n" +
            "    return env->NewStringUTF(message.c_str());\n" +
            "}\n";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns true if the project has at least one .c / .cpp / .h / .hpp file.
     */
    public static boolean hasCppFiles(String sc_id) {
        String cppPath = new FilePathUtil().getPathCpp(sc_id);
        if (!FileUtil.isExistFile(cppPath)) return false;
        ArrayList<String> found = new ArrayList<>();
        collectCppFilesRecursive(cppPath, found);
        return !found.isEmpty();
    }

    /**
     * Generates a CMakeLists.txt string for the given project / library name.
     */
    public static String generateCmakeLists(String projectName) {
        String lib = sanitizeLibName(projectName);
        return String.format(CMAKE_TEMPLATE, lib, lib, lib);
    }

    /**
     * Returns the Gradle externalNativeBuild block that references CMakeLists.txt.
     */
    public static String getGradleCmakeBlock() {
        return GRADLE_CMAKE_BLOCK;
    }

    /**
     * Generates a JNI bridge .cpp file content.
     *
     * @param fileName    name the file will be saved as (without extension)
     * @param packageName Android app package, e.g. "com.example.myapp"
     * @param className   Java class containing the native method, e.g. "MainActivity"
     */
    public static String generateJniBridge(String fileName, String packageName, String className) {
        String libName    = sanitizeLibName(fileName);
        String jniPackage = packageName.replace('.', '_');
        return String.format(JNI_TEMPLATE, fileName, libName, jniPackage, className);
    }

    /**
     * Copies the user's cpp source directory into the Android Studio export build path
     * ({projectMyscPath}/app/src/main/cpp/), creating it if it does not exist.
     *
     * @param sc_id       project identifier
     * @param cppBuildPath  yq.cppFilesPath — the target path inside the build tree
     */
    public static void copyCppFilesToBuildDir(String sc_id, String cppBuildPath) {
        String cppSourcePath = new FilePathUtil().getPathCpp(sc_id);
        if (!FileUtil.isExistFile(cppSourcePath)) return;

        FileUtil.makeDir(cppBuildPath);

        ArrayList<String> items = new ArrayList<>();
        FileUtil.listDir(cppSourcePath, items);
        for (String item : items) {
            File src  = new File(item);
            File dest = new File(cppBuildPath, src.getName());
            try {
                FileUtil.copyDirectory(src, dest);
            } catch (IOException e) {
                // Non-fatal: log and continue
                android.util.Log.w("CppBuildHelper", "Failed to copy " + src + ": " + e.getMessage());
            }
        }
    }

    /**
     * Injects the externalNativeBuild cmake block into the content of app/build.gradle,
     * inserting it before the closing brace of the android { } block.
     * Returns the modified gradle string, or the original if injection fails.
     */
    public static String injectCmakeBlockIntoGradle(String gradleContent) {
        if (gradleContent.contains("externalNativeBuild")) return gradleContent; // already present

        int androidStart = gradleContent.indexOf("android {");
        if (androidStart < 0) return gradleContent;

        // Walk forward from "android {" tracking brace depth to find its closing }
        int depth = 0;
        for (int i = androidStart; i < gradleContent.length(); i++) {
            char c = gradleContent.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Insert cmake block before this closing brace
                    return gradleContent.substring(0, i) + GRADLE_CMAKE_BLOCK + gradleContent.substring(i);
                }
            }
        }
        return gradleContent; // unmatched braces — leave unchanged
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Recursively collect .c / .cpp / .h / .hpp files under a directory path. */
    private static void collectCppFilesRecursive(String dirPath, ArrayList<String> result) {
        ArrayList<String> items = new ArrayList<>();
        FileUtil.listDir(dirPath, items);
        for (String item : items) {
            if (FileUtil.isDirectory(item)) {
                collectCppFilesRecursive(item, result);
            } else {
                String lower = item.toLowerCase();
                if (lower.endsWith(".c")   || lower.endsWith(".cpp") ||
                    lower.endsWith(".h")   || lower.endsWith(".hpp")) {
                    result.add(item);
                }
            }
        }
    }

    /** Converts an arbitrary project name into a valid CMake / JNI library identifier. */
    private static String sanitizeLibName(String raw) {
        return raw.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
}
