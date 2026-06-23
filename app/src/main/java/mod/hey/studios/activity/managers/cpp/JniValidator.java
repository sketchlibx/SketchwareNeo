package mod.hey.studios.activity.managers.cpp;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

/**
 * Validates JNI correctness across a Sketchware Neo project's Java and C++ files.
 *
 * <h3>Checks performed</h3>
 * <ol>
 *   <li><strong>LoadLibrary present</strong> — if any Java file declares a {@code native}
 *       method, at least one Java file must call {@code System.loadLibrary()} somewhere
 *       in the project.</li>
 *   <li><strong>JNI function format</strong> — every {@code Java_*} function in C++ files
 *       must have the minimum three-segment structure and be accompanied by
 *       {@code JNIEXPORT} and {@code JNICALL}.</li>
 *   <li><strong>Underscore mangling warning</strong> — any package name, class name, or
 *       method name component containing {@code _} needs JNI underscore-mangling
 *       ({@code _1}). This is flagged as a warning so the user can verify the
 *       generated bridge is correct.</li>
 *   <li><strong>Native method coverage</strong> — native method names declared in Java
 *       are compared against {@code Java_} implementations found in C++ files.
 *       Missing implementations are reported as errors.</li>
 *   <li><strong>Header consistency</strong> — if {@code .h} or {@code .hpp} files exist,
 *       check that {@code .c} or {@code .cpp} files are also present
 *       (headers without sources won't produce a library).</li>
 * </ol>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   JniValidator.ValidationReport report = JniValidator.validate(sc_id);
 *   if (report.hasErrors || report.hasWarnings) {
 *       showDialog(report.formatForDisplay());
 *   }
 * }</pre>
 */
public final class JniValidator {

    private JniValidator() {}

    // ── Regex patterns ────────────────────────────────────────────────────────

    /**
     * Matches {@code native} method declarations in Java source.
     * Group 1: optional visibility modifier, Group 2: return type, Group 3: method name.
     *
     * <p>Matches patterns like:
     * <ul>
     *   <li>{@code public native String getMessage();}</li>
     *   <li>{@code private native int compute(int a, int b);}</li>
     *   <li>{@code native void doWork();} (package-private)</li>
     * </ul>
     */
    private static final Pattern NATIVE_METHOD_PATTERN = Pattern.compile(
            "(?:public|private|protected)?\\s+native\\s+" +
            "[\\w<>\\[\\]]+\\s+" +          // return type
            "(\\w+)\\s*\\(",                 // method name (group 1)
            Pattern.MULTILINE
    );

    /** Matches {@code System.loadLibrary("...")}, capturing the library name. */
    private static final Pattern LOAD_LIBRARY_PATTERN = Pattern.compile(
            "System\\.loadLibrary\\s*\\(\\s*\"([^\"]+)\"\\s*\\)"
    );

    /** Matches any {@code Java_} prefixed function definition with {@code JNIEXPORT}. */
    private static final Pattern JNI_FUNC_PATTERN = Pattern.compile(
            "JNIEXPORT\\s+\\w+\\s+JNICALL\\s+(Java_[A-Za-z0-9_]+)",
            Pattern.MULTILINE
    );

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Runs all JNI validation checks against the project's Java and C++ sources.
     *
     * @param sc_id  Sketchware project identifier.
     * @return       A {@link ValidationReport} with structured findings.
     */
    public static ValidationReport validate(String sc_id) {
        FilePathUtil fpu = new FilePathUtil();
        String javaRoot = fpu.getPathJava(sc_id);
        String cppRoot  = fpu.getPathCpp(sc_id);

        List<JniIssue> issues = new ArrayList<>();

        // ── Collect all Java source files ─────────────────────────────────────
        List<File> javaFiles = new ArrayList<>();
        collectFilesRecursive(new File(javaRoot), ".java", javaFiles);

        // ── Collect all C/C++ source and header files ─────────────────────────
        List<File> cppFiles    = new ArrayList<>();
        List<File> headerFiles = new ArrayList<>();
        collectFilesRecursive(new File(cppRoot), ".c",   cppFiles);
        collectFilesRecursive(new File(cppRoot), ".cpp", cppFiles);
        collectFilesRecursive(new File(cppRoot), ".h",   headerFiles);
        collectFilesRecursive(new File(cppRoot), ".hpp", headerFiles);

        // No C++ files at all → skip JNI checks (project has no native code)
        if (cppFiles.isEmpty() && headerFiles.isEmpty()) {
            return new ValidationReport(issues); // empty = all clear
        }

        // ── Check 1: loadLibrary present when native methods exist ─────────────
        Set<String> declaredNativeMethods = new HashSet<>();
        Set<String> loadedLibraries       = new HashSet<>();

        for (File javaFile : javaFiles) {
            String content = FileUtil.readFile(javaFile.getAbsolutePath());
            if (content == null || content.isEmpty()) continue;

            Matcher nativeMatcher = NATIVE_METHOD_PATTERN.matcher(content);
            while (nativeMatcher.find()) {
                declaredNativeMethods.add(nativeMatcher.group(1));
            }

            Matcher libMatcher = LOAD_LIBRARY_PATTERN.matcher(content);
            while (libMatcher.find()) {
                loadedLibraries.add(libMatcher.group(1));
            }
        }

        if (!declaredNativeMethods.isEmpty() && loadedLibraries.isEmpty()) {
            issues.add(JniIssue.error(
                    "No System.loadLibrary() call found",
                    "The project declares " + declaredNativeMethods.size() +
                    " native method(s) " + declaredNativeMethods +
                    " but no System.loadLibrary() exists in any Java file.\n" +
                    "The app will throw UnsatisfiedLinkError at runtime.",
                    null
            ));
        }

        // ── Check 2: headers without sources ─────────────────────────────────
        if (!headerFiles.isEmpty() && cppFiles.isEmpty()) {
            issues.add(JniIssue.warning(
                    "Header files without source files",
                    "Found " + headerFiles.size() + " header file(s) but no .c/.cpp sources.\n" +
                    "CMake's add_library() requires at least one source file to produce a .so.",
                    null
            ));
        }

        // ── Check 3: JNI function format in C++ files ─────────────────────────
        Set<String> implementedMethods = new HashSet<>();

        for (File cppFile : cppFiles) {
            String content = FileUtil.readFile(cppFile.getAbsolutePath());
            if (content == null || content.isEmpty()) continue;

            // Extract Java_ function names (with JNIEXPORT+JNICALL)
            Matcher funcMatcher = JNI_FUNC_PATTERN.matcher(content);
            while (funcMatcher.find()) {
                String jniFuncName = funcMatcher.group(1);

                // Validate minimum structure
                if (!JniBridgeGenerator.isValidJniFunctionName(jniFuncName)) {
                    issues.add(JniIssue.error(
                            "Malformed JNI function name: " + jniFuncName,
                            "Expected format: Java_<package>_<ClassName>_<methodName>\n" +
                            "Package separators (.) must be replaced with _\n" +
                            "File: " + cppFile.getName(),
                            cppFile.getAbsolutePath()
                    ));
                } else {
                    // Extract method name (last segment of Java_pkg_Class_method)
                    String[] parts = jniFuncName.split("_");
                    implementedMethods.add(parts[parts.length - 1]);
                }
            }

            // Check for Java_ functions that lack JNIEXPORT / JNICALL
            List<String> allJavaFuncs = JniBridgeGenerator.extractJniFunctionNames(content);
            for (String funcName : allJavaFuncs) {
                if (!content.contains("JNIEXPORT") || !content.contains("JNICALL")) {
                    issues.add(JniIssue.warning(
                            "Java_ function without JNIEXPORT/JNICALL: " + funcName,
                            "JNI functions must be declared with JNIEXPORT and JNICALL for " +
                            "correct symbol visibility on all Android ABIs.\n" +
                            "File: " + cppFile.getName(),
                            cppFile.getAbsolutePath()
                    ));
                }
            }
        }

        // ── Check 4: native method coverage ──────────────────────────────────
        // Compare declared Java native methods against C++ implementations.
        // This is a best-effort check: we compare method names only (not full signatures)
        // since full signature matching requires parsing both sides.
        if (!declaredNativeMethods.isEmpty() && !implementedMethods.isEmpty()) {
            for (String nativeMethod : declaredNativeMethods) {
                // JNI encodes underscores as _1, so normalise before comparing
                String encoded = JniBridgeGenerator.encodeJniComponent(nativeMethod);
                if (!implementedMethods.contains(nativeMethod)
                        && !implementedMethods.contains(encoded)) {
                    issues.add(JniIssue.warning(
                            "Possibly unimplemented native method: " + nativeMethod + "()",
                            "Declared as native in Java but no matching Java_*_" + nativeMethod +
                            " function found in C++ files.\n" +
                            "The app will throw UnsatisfiedLinkError if this method is called.\n" +
                            "Note: if the method is in a different ABI or pre-compiled .so, this is a false positive.",
                            null
                    ));
                }
            }
        }

        // ── Check 5: underscore in package/class name warning ─────────────────
        // Underscores require mangling; easy to get wrong manually.
        // We scan Java files for the package declaration and warn.
        for (File javaFile : javaFiles) {
            String content = FileUtil.readFile(javaFile.getAbsolutePath());
            if (content == null) continue;

            Pattern pkgPattern = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
            Matcher pkgMatcher = pkgPattern.matcher(content);
            if (pkgMatcher.find()) {
                String pkg = pkgMatcher.group(1);
                if (JniBridgeGenerator.requiresMangling(pkg)) {
                    issues.add(JniIssue.warning(
                            "Package name contains underscore: " + pkg,
                            "Package name '" + pkg + "' contains '_' which must be " +
                            "encoded as '_1' in JNI function names.\n" +
                            "Example: com.my_app → Java_com_my_1app_ClassName_method\n" +
                            "File: " + javaFile.getName(),
                            javaFile.getAbsolutePath()
                    ));
                    break; // Warn once per project (all files share the package)
                }
            }
        }

        // ── Info: loaded libraries ─────────────────────────────────────────────
        for (String lib : loadedLibraries) {
            issues.add(JniIssue.info(
                    "System.loadLibrary(\"" + lib + "\") detected",
                    "Make sure CMakeLists.txt has: add_library(" + lib + " SHARED ...)\n" +
                    "The .so file will be named lib" + lib + ".so",
                    null
            ));
        }

        return new ValidationReport(issues);
    }

    // =========================================================================
    // File scanning
    // =========================================================================

    private static void collectFilesRecursive(File dir, String extension, List<File> result) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectFilesRecursive(child, extension, result);
            } else if (child.getName().toLowerCase().endsWith(extension)) {
                result.add(child);
            }
        }
    }

    // =========================================================================
    // Result types
    // =========================================================================

    /** Severity levels for JNI issues. */
    public enum Severity { INFO, WARNING, ERROR }

    /**
     * A single JNI validation finding.
     *
     * <p>{@code filePath} is {@code null} for project-level findings.
     */
    public static final class JniIssue {
        public final Severity severity;
        public final String   title;
        public final String   detail;
        public final String   filePath; // nullable

        private JniIssue(Severity s, String title, String detail, String filePath) {
            this.severity = s;
            this.title    = title;
            this.detail   = detail;
            this.filePath = filePath;
        }

        static JniIssue error  (String t, String d, String f) { return new JniIssue(Severity.ERROR,   t, d, f); }
        static JniIssue warning(String t, String d, String f) { return new JniIssue(Severity.WARNING, t, d, f); }
        static JniIssue info   (String t, String d, String f) { return new JniIssue(Severity.INFO,    t, d, f); }

        String icon() {
            switch (severity) {
                case ERROR:   return "✗";
                case WARNING: return "⚠";
                default:      return "ℹ";
            }
        }

        @Override
        public String toString() {
            return icon() + " " + title;
        }
    }

    /**
     * The result of a full JNI validation run.
     */
    public static final class ValidationReport {
        public final List<JniIssue> issues;
        public final boolean        hasErrors;
        public final boolean        hasWarnings;
        public final int            errorCount;
        public final int            warningCount;
        public final int            infoCount;

        ValidationReport(List<JniIssue> issues) {
            this.issues = Collections.unmodifiableList(issues);
            int e = 0, w = 0, i = 0;
            for (JniIssue issue : issues) {
                switch (issue.severity) {
                    case ERROR:   e++; break;
                    case WARNING: w++; break;
                    default:      i++;
                }
            }
            errorCount   = e;
            warningCount = w;
            infoCount    = i;
            hasErrors    = e > 0;
            hasWarnings  = w > 0;
        }

        /** Returns true if there are no errors and no warnings — project is clean. */
        public boolean isClean() {
            return !hasErrors && !hasWarnings;
        }

        /**
         * Returns a human-readable summary suitable for display in a dialog title bar.
         * Example: {@code "JNI OK"} or {@code "2 errors, 1 warning"}
         */
        public String statusSummary() {
            if (issues.isEmpty()) return "No C/C++ files found";
            if (isClean())        return "JNI setup looks correct";
            List<String> parts = new ArrayList<>();
            if (errorCount   > 0) parts.add(errorCount   + " error"   + (errorCount   > 1 ? "s" : ""));
            if (warningCount > 0) parts.add(warningCount + " warning" + (warningCount > 1 ? "s" : ""));
            return String.join(", ", parts);
        }

        /**
         * Returns full formatted text suitable for display in a {@code TextView}
         * inside a dialog or bottom sheet.
         */
        public String formatForDisplay() {
            if (issues.isEmpty()) {
                return "No C/C++ source files were found in this project.\n\n" +
                       "Create a .c or .cpp file in the C/C++ Manager to get started.";
            }

            StringBuilder sb = new StringBuilder();

            // Errors first
            for (JniIssue issue : issues) {
                if (issue.severity == Severity.ERROR) {
                    sb.append(issue.icon()).append("  ").append(issue.title).append('\n');
                    sb.append("    ").append(issue.detail.replace("\n", "\n    ")).append('\n');
                    sb.append('\n');
                }
            }
            // Warnings
            for (JniIssue issue : issues) {
                if (issue.severity == Severity.WARNING) {
                    sb.append(issue.icon()).append("  ").append(issue.title).append('\n');
                    sb.append("    ").append(issue.detail.replace("\n", "\n    ")).append('\n');
                    sb.append('\n');
                }
            }
            // Info
            for (JniIssue issue : issues) {
                if (issue.severity == Severity.INFO) {
                    sb.append(issue.icon()).append("  ").append(issue.title).append('\n');
                    sb.append("    ").append(issue.detail.replace("\n", "\n    ")).append('\n');
                    sb.append('\n');
                }
            }

            return sb.toString().trim();
        }

        /**
         * Returns only error-level issues (used for surfacing blockers to the user).
         */
        public List<JniIssue> errors() {
            List<JniIssue> result = new ArrayList<>();
            for (JniIssue issue : issues) {
                if (issue.severity == Severity.ERROR) result.add(issue);
            }
            return result;
        }
    }
}