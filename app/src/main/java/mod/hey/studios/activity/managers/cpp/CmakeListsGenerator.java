package mod.hey.studios.activity.managers.cpp;

import java.util.ArrayList;
import java.util.List;

/** Generates CMakeLists.txt content and injects the matching Gradle cmake blocks for AS export. */
public final class CmakeListsGenerator {

    private CmakeListsGenerator() {}

    public static final String CMAKE_MIN_VERSION = "3.22.1";
    public static final String C_STANDARD = "11";
    public static final String CXX_STANDARD = "17";

    private static final String CMAKE_TEMPLATE =
            "cmake_minimum_required(VERSION 3.22.1)\n\n" +
            "project(\"%s\")\n\n" +
            "file(GLOB_RECURSE CPP_SOURCES\n" +
            "        \"src/main/jni/*.c\"\n" +
            "        \"src/main/jni/*.cpp\")\n\n" +
            "add_library(\n" +
            "        %s\n" +
            "        SHARED\n" +
            "        ${CPP_SOURCES})\n\n" +
            "find_library(log-lib log)\n\n" +
            "target_link_libraries(\n" +
            "        %s\n" +
            "        ${log-lib})\n";

    public static String generate(String projectName) {
        String libName = sanitizeLibName(projectName);
        return String.format(CMAKE_TEMPLATE, projectName, libName, libName);
    }

    public static String sanitizeLibName(String raw) {
        String sanitized = raw.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
        if (!sanitized.isEmpty() && Character.isDigit(sanitized.charAt(0))) {
            sanitized = "lib_" + sanitized;
        }
        if (sanitized.isEmpty()) {
            sanitized = "native_lib";
        }
        return sanitized;
    }

    public static List<String> extractHeaderNames(List<String> allPaths) {
        List<String> headers = new ArrayList<>();
        for (String path : allPaths) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".h") || lower.endsWith(".hpp")) {
                headers.add(path);
            }
        }
        return headers;
    }

    public static String generateTargetSourcesSnippet(String libName, String srcPath) {
        return "target_sources(" + libName + "\n" +
               "        PRIVATE\n" +
               "        " + srcPath + ")\n";
    }

    // =========================================================================
    // Gradle Injection Helpers (used by CppExporter for the Android Studio export path)
    // =========================================================================

    public static String injectDefaultConfigCmakeBlock(String gradleContent, String abiFilters) {
        if (gradleContent.contains("externalNativeBuild")) return gradleContent; // idempotent

        String block = "\n        externalNativeBuild {\n" +
                       "            cmake {\n" +
                       "                cppFlags \"-std=c++17\"\n" +
                       (abiFilters != null && !abiFilters.isEmpty() ? "                abiFilters " + abiFilters + "\n" : "") +
                       "            }\n" +
                       "        }\n    ";

        int idx = gradleContent.indexOf("defaultConfig {");
        if (idx != -1) {
            int openBraces = 0;
            for (int i = idx; i < gradleContent.length(); i++) {
                char c = gradleContent.charAt(i);
                if (c == '{') openBraces++;
                else if (c == '}') {
                    openBraces--;
                    if (openBraces == 0) {
                        return gradleContent.substring(0, i) + block + gradleContent.substring(i);
                    }
                }
            }
        }
        return gradleContent;
    }

    public static String injectAndroidLevelCmakeBlock(String gradleContent) {
        if (gradleContent.contains("externalNativeBuild") && gradleContent.contains("path \"CMakeLists.txt\"")) {
            return gradleContent; // idempotent
        }

        String block = "\n    externalNativeBuild {\n" +
                       "        cmake {\n" +
                       "            path \"CMakeLists.txt\"\n" +
                       "            version \"" + CMAKE_MIN_VERSION + "\"\n" +
                       "        }\n" +
                       "    }\n";

        int idx = gradleContent.indexOf("android {");
        if (idx != -1) {
            int openBraces = 0;
            for (int i = idx; i < gradleContent.length(); i++) {
                char c = gradleContent.charAt(i);
                if (c == '{') openBraces++;
                else if (c == '}') {
                    openBraces--;
                    if (openBraces == 0) {
                        return gradleContent.substring(0, i) + block + gradleContent.substring(i);
                    }
                }
            }
        }
        return gradleContent;
    }
}
