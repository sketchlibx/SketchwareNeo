package mod.hey.studios.activity.managers.cpp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Generates matched C++ JNI functions and Java native method stubs for
 * Sketchware Neo projects.
 *
 * <h3>JNI naming convention (enforced here)</h3>
 * <pre>
 *   Java_[package]_[ClassName]_[methodName]
 * </pre>
 * where every {@code .} in the package becomes {@code _}, and any literal
 * {@code _} in any component is encoded as {@code _1}.
 *
 * <p>Example:
 * <ul>
 *   <li>Package: {@code com.example.myapp}, Class: {@code MainActivity}, method: {@code getMessage}
 *   <li>→ {@code Java_com_example_myapp_MainActivity_getMessage}
 * </ul>
 *
 * <p>This class is pure generation — no file I/O, fully unit-testable.
 * All write operations are done by {@link ManageCppActivity}.
 */
public final class JniBridgeGenerator {

    private JniBridgeGenerator() {}

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Supported JNI return types with their C type, Java type, and
     * a sensible default return expression for the generated stub body.
     */
    public enum JniReturnType {
        // name          jniType         javaType    defaultReturn
        VOID    ("void",    "void",         "void",     null),
        STRING  ("String",  "jstring",      "String",   "env->NewStringUTF(\"Hello from C++!\")"),
        INT     ("int",     "jint",         "int",      "42"),
        BOOLEAN ("boolean", "jboolean",     "boolean",  "JNI_TRUE"),
        LONG    ("long",    "jlong",        "long",     "0L"),
        DOUBLE  ("double",  "jdouble",      "double",   "0.0"),
        FLOAT   ("float",   "jfloat",       "float",    "0.0f"),
        BYTE    ("byte",    "jbyte",        "byte",     "0"),
        CHAR    ("char",    "jchar",        "char",     "0"),
        SHORT   ("short",   "jshort",       "short",    "0");

        public final String displayName;  // human-readable label
        public final String jniType;      // C/C++ JNI type
        public final String javaType;     // Java type keyword
        public final String defaultReturn;// default C return expression (null = no return)

        JniReturnType(String d, String jni, String java, String ret) {
            displayName   = d;
            jniType       = jni;
            javaType      = java;
            defaultReturn = ret;
        }

        /**
         * Returns the Java visibility + return declaration for a native method stub.
         * Example: {@code "public native String"}
         */
        public String javaNativeDeclaration() {
            return "public native " + javaType;
        }
    }

    /**
     * Describes a single native method to generate.
     */
    public static class JniMethodSpec {
        public final String methodName;
        public final JniReturnType returnType;

        public JniMethodSpec(String methodName, JniReturnType returnType) {
            this.methodName = methodName;
            this.returnType = returnType;
        }
    }

    // =========================================================================
    // Default sample methods
    // =========================================================================

    /**
     * Returns the two sample methods generated in every new JNI bridge file.
     * Covers the most common use case (String return) and a numeric one.
     */
    public static List<JniMethodSpec> defaultSampleMethods() {
        return Arrays.asList(
                new JniMethodSpec("getMessage", JniReturnType.STRING),
                new JniMethodSpec("getNumber",  JniReturnType.INT)
        );
    }

    // =========================================================================
    // C++ generation
    // =========================================================================

    /**
     * Generates a complete, ready-to-compile JNI bridge .cpp file.
     *
     * <p>The file includes:
     * <ol>
     *   <li>A header comment with Java-side instructions</li>
     *   <li>{@code #include <jni.h>} and standard headers</li>
     *   <li>{@code extern "C"} block with one function per {@code JniMethodSpec}</li>
     * </ol>
     *
     * @param fileName    Name the file will be saved as (without extension).
     * @param packageName Android app package, e.g. {@code "com.example.myapp"}.
     * @param className   Java class containing the native declarations,
     *                    e.g. {@code "MainActivity"}.
     * @param libName     Sanitized CMake library name (from
     *                    {@link CmakeListsGenerator#sanitizeLibName}).
     * @param methods     Methods to generate; use {@link #defaultSampleMethods()} for a
     *                    new-file template.
     * @return            Contents of a .cpp file.
     */
    public static String generateBridgeFile(
            String fileName,
            String packageName,
            String className,
            String libName,
            List<JniMethodSpec> methods) {

        StringBuilder sb = new StringBuilder();

        // ── File header ───────────────────────────────────────────────────────
        sb.append("// ").append(fileName).append(".cpp — JNI Bridge\n");
        sb.append("// Generated by Sketchware Neo C/C++ Manager\n");
        sb.append("//\n");
        sb.append("// ─── Java-side setup ────────────────────────────────────────\n");
        sb.append("// Add the following to ").append(className).append(".java:\n");
        sb.append("//\n");
        sb.append("// static {\n");
        sb.append("//     System.loadLibrary(\"").append(libName).append("\");\n");
        sb.append("// }\n");
        sb.append("//\n");
        for (JniMethodSpec m : methods) {
            sb.append("// ").append(m.returnType.javaNativeDeclaration())
              .append(" ").append(m.methodName).append("();\n");
        }
        sb.append("// ────────────────────────────────────────────────────────────\n");
        sb.append('\n');

        // ── Includes ─────────────────────────────────────────────────────────
        sb.append("#include <jni.h>\n");
        boolean needsString = methods.stream()
                .anyMatch(m -> m.returnType == JniReturnType.STRING);
        if (needsString) sb.append("#include <string>\n");
        sb.append("#include <android/log.h>\n");
        sb.append('\n');

        // ── Log macro ────────────────────────────────────────────────────────
        sb.append("#define LOG_TAG \"").append(libName).append("\"\n");
        sb.append("#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)\n");
        sb.append('\n');

        // ── extern "C" block ─────────────────────────────────────────────────
        sb.append("extern \"C\" {\n");

        for (JniMethodSpec method : methods) {
            sb.append('\n');
            appendCppFunction(sb, packageName, className, method);
        }

        sb.append('\n');
        sb.append("} // extern \"C\"\n");

        return sb.toString();
    }

    /**
     * Appends one complete JNI function definition to {@code sb}.
     */
    private static void appendCppFunction(StringBuilder sb,
                                          String packageName,
                                          String className,
                                          JniMethodSpec method) {
        String jniFuncName = buildJniFunctionName(packageName, className, method.methodName);
        String jniType     = method.returnType.jniType;

        sb.append("JNIEXPORT ").append(jniType).append(" JNICALL\n");
        sb.append(jniFuncName).append("(\n");
        sb.append("        JNIEnv* env,\n");
        sb.append("        jobject /* thiz */) {\n");

        // Log call
        sb.append("    LOGI(\"").append(method.methodName).append(" called\");\n");

        // Return value
        if (method.returnType.defaultReturn != null) {
            if (method.returnType == JniReturnType.STRING) {
                sb.append("    std::string result = \"Hello from C++! (").append(method.methodName).append(")\";\n");
                sb.append("    return env->NewStringUTF(result.c_str());\n");
            } else {
                sb.append("    return ").append(method.returnType.defaultReturn).append(";\n");
            }
        }

        sb.append("}\n");
    }

    // =========================================================================
    // Java stub generation
    // =========================================================================

    /**
     * Generates a Java code snippet the user pastes into their Activity / class.
     *
     * <p>The snippet is intentionally a <em>snippet</em>, not a complete file —
     * the user already has their Activity and only needs to add the loadLibrary
     * block and native declarations.
     *
     * @param className  Java class name (shown in comment header).
     * @param libName    CMake library name — used in {@code System.loadLibrary()}.
     * @param methods    Native methods to declare.
     * @return           A Java code snippet string.
     */
    public static String generateJavaStub(
            String className,
            String libName,
            List<JniMethodSpec> methods) {

        StringBuilder sb = new StringBuilder();

        sb.append("// ── Add to ").append(className).append(".java ──────────────────\n\n");

        // loadLibrary block
        sb.append("// 1. Load the native library (add inside the class body):\n");
        sb.append("static {\n");
        sb.append("    System.loadLibrary(\"").append(libName).append("\");\n");
        sb.append("}\n\n");

        // Native method declarations
        sb.append("// 2. Native method declarations:\n");
        for (JniMethodSpec m : methods) {
            sb.append(m.returnType.javaNativeDeclaration())
              .append(" ").append(m.methodName).append("();\n");
        }

        sb.append("\n// ── Build note ────────────────────────────────────────────\n");
        sb.append("// The native implementation is in ").append(libName).append(".cpp\n");
        sb.append("// Export to Android Studio and build there to compile the C++ code.\n");
        sb.append("// The .so library will be placed in lib/{ABI}/lib").append(libName).append(".so\n");

        return sb.toString();
    }

    // =========================================================================
    // JNI naming
    // =========================================================================

    /**
     * Builds the fully-qualified JNI C function name for a native method.
     * @param packageName  e.g. {@code "com.example.myapp"}
     * @param className    e.g. {@code "MainActivity"}
     * @param methodName   e.g. {@code "getMessage"}
     * @return             e.g. {@code "Java_com_example_myapp_MainActivity_getMessage"}
     */
    public static String buildJniFunctionName(
            String packageName,
            String className,
            String methodName) {
        // Replace package dots with underscores first, then encode component underscores
        String encodedPkg    = encodeJniComponent(packageName.replace('.', '/'));
        String encodedClass  = encodeJniComponent(className);
        String encodedMethod = encodeJniComponent(methodName);

        return "Java_" + encodedPkg + "_" + encodedClass + "_" + encodedMethod;
    }

    /**
     * Applies JNI name-mangling to a single identifier or slash-separated
     * package string.
     *
     * <p>Replaces {@code _} with {@code _1}, {@code ;} with {@code _2},
     * {@code [} with {@code _3}, and {@code /} with {@code _}.
     */
    public static String encodeJniComponent(String name) {
        // Order matters: encode _1, _2, _3 literals first to avoid double-encoding
        return name
                .replace("_",  "_1")   // literal underscore → _1
                .replace(";",  "_2")   // semicolon (array descriptors)
                .replace("[",  "_3")   // bracket (array prefix)
                .replace("/",  "_");   // package separator
    }

    // =========================================================================
    // Library name utilities
    // =========================================================================

    /**
     * Derives a CMake-compatible library name from a Java package name by
     * extracting the last segment and sanitizing it.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "com.example.myapp"} → {@code "myapp"}</li>
     *   <li>{@code "com.my_app"} → {@code "my_1app"} (underscore encoded)</li>
     *   <li>{@code ""} → {@code "native_lib"}</li>
     * </ul>
     *
     * <p><strong>Note:</strong> The derived name should match the {@code project()}
     * name in your CMakeLists.txt. Adjust if needed to keep them consistent.
     */
    public static String inferLibName(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return "native_lib";
        String lastSegment = packageName.contains(".")
                ? packageName.substring(packageName.lastIndexOf('.') + 1)
                : packageName;
        return CmakeListsGenerator.sanitizeLibName(lastSegment);
    }

    // =========================================================================
    // Validation helpers (used by JniValidator)
    // =========================================================================

    /**
     * Returns {@code true} if {@code name} is a valid Java identifier that
     * requires no JNI underscore-mangling (i.e., contains no {@code _}).
     *
     * <p>JNI function names with {@code _} are valid but require careful
     * matching. This method flags them as needing explicit attention.
     */
    public static boolean isSimpleIdentifier(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.matches("[a-zA-Z][a-zA-Z0-9]*");
    }

    /**
     * Returns {@code true} if a package/class/method component contains
     * characters that require JNI name-mangling (underscore or non-ASCII).
     */
    public static boolean requiresMangling(String component) {
        return component != null && component.contains("_");
    }

    /**
     * Returns all JNI function names ({@code Java_...}) extracted from a block
     * of C/C++ source code. Used by {@link JniValidator}.
     */
    public static List<String> extractJniFunctionNames(String cppContent) {
        List<String> names = new ArrayList<>();
        if (cppContent == null || cppContent.isEmpty()) return names;

        // Split on whitespace / newlines and scan for Java_ tokens
        String[] tokens = cppContent.split("[\\s(]");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.startsWith("Java_") && trimmed.length() > 5) {
                // Strip any leading * or & that might be attached
                trimmed = trimmed.replaceAll("^[*&]+", "");
                if (trimmed.startsWith("Java_")) names.add(trimmed);
            }
        }
        return names;
    }

    /**
     * Validates that a JNI function name follows the expected format:
     * {@code Java_<pkg>_<Class>_<method>} with at least 3 {@code _}-separated
     * segments after the {@code Java} prefix.
     */
    public static boolean isValidJniFunctionName(String name) {
        if (name == null || !name.startsWith("Java_")) return false;
        // After "Java_" there must be at least pkg_Class_method → 2 more underscores
        String after = name.substring(5); // skip "Java_"
        String[] parts = after.split("_(?![0-9])"); // split on _ not followed by digit (those are encodings)
        return parts.length >= 3;
    }
}
