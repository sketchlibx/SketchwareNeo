package mod.hey.studios.activity.managers.cpp;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.yq;
import pro.sketchware.utility.FileUtil;

public final class CppValidator {

    private static final String TAG = "CppValidator";

    private CppValidator() {}

    public static ValidationResult validate(yq metadata, String sc_id) {
        List<Check> checks = new ArrayList<>();

        String cppDestPath  = metadata.cppFilesPath;
        String cmakePath    = metadata.projectMyscPath + "app" + File.separator + "CMakeLists.txt";
        String appGradlePath = metadata.projectMyscPath + "app" + File.separator + "build.gradle";

        checks.add(Check.of("cpp directory exists", FileUtil.isExistFile(cppDestPath) && new File(cppDestPath).isDirectory(), "Expected: " + cppDestPath));

        boolean hasSource = false;
        int copiedFileCount = 0;
        if (FileUtil.isExistFile(cppDestPath)) {
            List<String> allFiles = collectFilesRecursive(cppDestPath);
            copiedFileCount = allFiles.size();
            for (String f : allFiles) {
                String lower = f.toLowerCase();
                if (lower.endsWith(".c") || lower.endsWith(".cpp")) {
                    hasSource = true;
                    break;
                }
            }
        }
        checks.add(Check.of("at least one .c or .cpp source file copied (" + copiedFileCount + " total files)", hasSource, "CMake will warn about an empty source list if no .c/.cpp files are present"));

        boolean cmakeExists = FileUtil.isExistFile(cmakePath);
        checks.add(Check.of("CMakeLists.txt exists at app/CMakeLists.txt", cmakeExists, "Expected path: " + cmakePath));

        if (cmakeExists) {
            String cmakeContent = FileUtil.readFile(cmakePath);
            checks.add(Check.of("CMakeLists.txt contains cmake_minimum_required", cmakeContent.contains("cmake_minimum_required"), "Missing directive will cause CMake to reject the file"));
            checks.add(Check.of("CMakeLists.txt contains add_library", cmakeContent.contains("add_library"), "No library target defined"));
        } else {
            checks.add(Check.skipped("CMakeLists.txt contains cmake_minimum_required", "File absent"));
            checks.add(Check.skipped("CMakeLists.txt contains add_library", "File absent"));
        }

        boolean gradleExists = FileUtil.isExistFile(appGradlePath);
        if (gradleExists) {
            String gradle = FileUtil.readFile(appGradlePath);
            checks.add(Check.of("build.gradle contains externalNativeBuild", gradle.contains("externalNativeBuild"), "AGP won't invoke cmake without this block"));
            checks.add(Check.of("build.gradle contains cmake { path", gradle.contains("path") && gradle.contains("CMakeLists.txt"), "AGP needs to know where CMakeLists.txt is"));
            checks.add(Check.of("build.gradle contains abiFilters", gradle.contains("abiFilters"), "Without abiFilters, AGP builds for all ABIs"));
            checks.add(Check.of("build.gradle contains cppFlags", gradle.contains("cppFlags"), "C++ standard flag (-std=c++17) recommended"));
        } else {
            checks.add(Check.skipped("build.gradle contains externalNativeBuild", "File absent"));
            checks.add(Check.skipped("build.gradle contains cmake { path", "File absent"));
            checks.add(Check.skipped("build.gradle contains abiFilters", "File absent"));
            checks.add(Check.skipped("build.gradle contains cppFlags", "File absent"));
        }

        if (gradleExists && cmakeExists) {
            String gradle = FileUtil.readFile(appGradlePath);
            boolean pathConsistent = gradle.contains("\"CMakeLists.txt\"");
            checks.add(Check.of("CMakeLists.txt path in build.gradle is consistent with file location", pathConsistent, "Expected: path \"CMakeLists.txt\" (relative to app/ directory)"));
        } else {
            checks.add(Check.skipped("CMakeLists.txt path consistency", "One or both files absent"));
        }

        String compiledLibsPath = metadata.compiledNativeLibsPath;
        if (FileUtil.isExistFile(compiledLibsPath)) {
            File compiledDir = new File(compiledLibsPath);
            File[] abiDirs   = compiledDir.listFiles(File::isDirectory);
            if (abiDirs != null && abiDirs.length > 0) {
                boolean abiOk = true;
                for (File abiDir : abiDirs) {
                    String abiName = abiDir.getName();
                    if (!abiName.equals("armeabi-v7a") && !abiName.equals("arm64-v8a") && !abiName.equals("x86") && !abiName.equals("x86_64")) {
                        abiOk = false;
                        break;
                    }
                }
                checks.add(Check.of("compiled .so ABI directory names are valid", abiOk, "ABI directories must be valid"));
            } else {
                checks.add(Check.skipped("compiled .so ABI consistency", "No compiled libs present"));
            }
        } else {
            checks.add(Check.skipped("compiled .so ABI consistency", "compiledNativeLibsPath absent"));
        }

        boolean hasSourceFiles = false;
        boolean hasLoadLibrary = false;
        if (FileUtil.isExistFile(cppDestPath)) {
            ArrayList<String> cppItems = new ArrayList<>();
            FileUtil.listDir(cppDestPath, cppItems);
            for (String item : cppItems) {
                String lower = item.toLowerCase();
                if (lower.endsWith(".c") || lower.endsWith(".cpp")) {
                    hasSourceFiles = true;
                    break;
                }
            }
        }
        if (hasSourceFiles && FileUtil.isExistFile(metadata.javaFilesPath)) {
            ArrayList<String> javaItems = new ArrayList<>();
            FileUtil.listDir(metadata.javaFilesPath, javaItems);
            Pattern loadLibPat = Pattern.compile("System\\.loadLibrary\\s*\\(");
            for (String javaItem : javaItems) {
                if (!javaItem.endsWith(".java")) continue;
                String content = FileUtil.readFile(javaItem);
                if (content != null && loadLibPat.matcher(content).find()) {
                    hasLoadLibrary = true;
                    break;
                }
            }
            checks.add(Check.of("System.loadLibrary() present in Java source", !hasSourceFiles || hasLoadLibrary, "C++ source files exist but no Java file calls System.loadLibrary()"));
        } else if (hasSourceFiles) {
            checks.add(Check.skipped("System.loadLibrary() check", "Java files path not accessible during export validation"));
        }

        if (FileUtil.isExistFile(cppDestPath)) {
            Pattern jniFuncPat = Pattern.compile("JNIEXPORT\\s+\\w+\\s+JNICALL\\s+(Java_[A-Za-z0-9_]+)", Pattern.MULTILINE);
            ArrayList<String> cppItems = new ArrayList<>();
            FileUtil.listDir(cppDestPath, cppItems);
            boolean allJniFuncsValid = true;
            for (String item : cppItems) {
                if (!item.endsWith(".cpp") && !item.endsWith(".c")) continue;
                String content = FileUtil.readFile(item);
                if (content == null) continue;
                Matcher m = jniFuncPat.matcher(content);
                while (m.find()) {
                    String fname = m.group(1);
                    if (!JniBridgeGenerator.isValidJniFunctionName(fname)) {
                        allJniFuncsValid = false;
                        break;
                    }
                }
                if (!allJniFuncsValid) break;
            }
            checks.add(Check.of("JNI function names are well-formed", allJniFuncsValid, "Malformed Java_* function name found"));
        } else {
            checks.add(Check.skipped("JNI function name validation", "No C++ sources found"));
        }

        ValidationResult result = new ValidationResult(checks);
        Log.d(TAG, result.summary());
        return result;
    }

    private static List<String> collectFilesRecursive(String dirPath) {
        List<String> result = new ArrayList<>();
        ArrayList<String> items = new ArrayList<>();
        FileUtil.listDir(dirPath, items);
        for (String item : items) {
            if (FileUtil.isDirectory(item)) {
                result.addAll(collectFilesRecursive(item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    public static final class Check {
        public enum Status { PASS, FAIL, SKIP }

        public final String  name;
        public final Status  status;
        public final String  hint;

        private Check(String name, Status status, String hint) {
            this.name   = name;
            this.status = status;
            this.hint   = hint;
        }

        static Check of(String name, boolean passed, String failHint) {
            return new Check(name, passed ? Status.PASS : Status.FAIL, passed ? "" : failHint);
        }

        static Check skipped(String name, String reason) {
            return new Check(name, Status.SKIP, reason);
        }

        @Override
        public String toString() {
            String icon = status == Status.PASS ? "✓" : status == Status.SKIP ? "○" : "✗";
            return icon + " " + name + (hint.isEmpty() ? "" : "  [" + hint + "]");
        }
    }

    public static final class ValidationResult {
        public final List<Check> checks;
        public final boolean passed;
        public final int passCount;
        public final int failCount;
        public final int skipCount;

        ValidationResult(List<Check> checks) {
            this.checks = Collections.unmodifiableList(checks);
            int p = 0, f = 0, s = 0;
            for (Check c : checks) {
                switch (c.status) {
                    case PASS -> p++;
                    case FAIL -> f++;
                    case SKIP -> s++;
                }
            }
            passCount = p;
            failCount = f;
            skipCount = s;
            passed    = (f == 0);
        }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n── C/C++ Export Validation ─────────────────────────────\n");
            for (Check c : checks) {
                sb.append("  ").append(c).append('\n');
            }
            sb.append("────────────────────────────────────────────────────────\n");
            sb.append("  Result: ")
              .append(passed ? "PASSED" : "FAILED")
              .append("  (")
              .append(passCount).append(" pass, ")
              .append(failCount).append(" fail, ")
              .append(skipCount).append(" skip)\n");
            return sb.toString();
        }

        public List<Check> failures() {
            List<Check> result = new ArrayList<>();
            for (Check c : checks) {
                if (c.status == Check.Status.FAIL) result.add(c);
            }
            return result;
        }
    }
}
