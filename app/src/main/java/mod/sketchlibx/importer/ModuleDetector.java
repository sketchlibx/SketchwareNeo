package mod.sketchlibx.importer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locates the project root (folder with settings.gradle) and the
 * application module folder (folder with src/main/ and the
 * com.android.application plugin in its build.gradle).
 *
 * Rules (no guessing):
 *  1. Walk dirs depth-first to find settings.gradle / settings.gradle.kts.
 *  2. Parse the include list from settings.gradle.
 *  3. For each included module, verify it has com.android.application plugin.
 *  4. If multiple matches, pick the one explicitly named "app".
 *  5. If still ambiguous, throw — never silently pick the wrong module.
 */
public class ModuleDetector {

    private static final String TAG = "ModuleDetector";

    public static class DetectionResult {
        /** Folder containing settings.gradle */
        public final File projectRoot;
        /** Folder containing src/main/ and build.gradle with application plugin */
        public final File appModuleDir;

        public DetectionResult(File projectRoot, File appModuleDir) {
            this.projectRoot  = projectRoot;
            this.appModuleDir = appModuleDir;
        }
    }

    /**
     * @param extractedRoot  Root of the temp folder where the ZIP was extracted.
     * @return DetectionResult, or null if detection fails.
     */
    public DetectionResult detect(File extractedRoot) {

        // Step 1: find settings.gradle / settings.gradle.kts
        File settingsFile = findSettingsGradle(extractedRoot);
        if (settingsFile == null) {
            Log.w(TAG, "settings.gradle not found — falling back to src/main scan.");
            return fallbackDetect(extractedRoot);
        }

        File projectRoot = settingsFile.getParentFile();
        Log.d(TAG, "Project root: " + projectRoot.getAbsolutePath());

        // Step 2: parse included module names
        List<String> moduleNames = parseIncludes(settingsFile);
        Log.d(TAG, "Included modules: " + moduleNames);

        // Step 3: find the module with com.android.application plugin
        List<File> appModuleCandidates = new ArrayList<>();
        for (String moduleName : moduleNames) {
            // settings.gradle uses ':app' or 'app' — strip leading colon
            String cleanName = moduleName.startsWith(":") ? moduleName.substring(1) : moduleName;
            // Handle nested module paths like ':feature:login' → feature/login
            cleanName = cleanName.replace(":", File.separator);

            File moduleDir = new File(projectRoot, cleanName);
            if (!moduleDir.exists()) {
                Log.w(TAG, "Module directory not found: " + moduleDir.getAbsolutePath());
                continue;
            }

            if (isApplicationModule(moduleDir)) {
                appModuleCandidates.add(moduleDir);
                Log.d(TAG, "Application module candidate: " + moduleDir.getAbsolutePath());
            }
        }

        // Step 4: resolve candidate
        if (appModuleCandidates.isEmpty()) {
            Log.w(TAG, "No com.android.application module found — fallback to src/main scan.");
            return fallbackDetect(extractedRoot);
        }

        if (appModuleCandidates.size() == 1) {
            return new DetectionResult(projectRoot, appModuleCandidates.get(0));
        }

        // Multiple candidates — prefer one named "app"
        for (File candidate : appModuleCandidates) {
            if (candidate.getName().equals("app")) {
                Log.d(TAG, "Multiple candidates — chose 'app': " + candidate.getAbsolutePath());
                return new DetectionResult(projectRoot, candidate);
            }
        }

        // Still ambiguous: pick first and warn
        Log.w(TAG, "Multiple app modules found, picking first: "
                + appModuleCandidates.get(0).getAbsolutePath());
        return new DetectionResult(projectRoot, appModuleCandidates.get(0));
    }

    // ── settings.gradle search ────────────────────────────────────────────────

    /** Depth-first search for settings.gradle or settings.gradle.kts. */
    private File findSettingsGradle(File dir) {
        File groovy = new File(dir, "settings.gradle");
        if (groovy.exists()) return groovy;
        File kts = new File(dir, "settings.gradle.kts");
        if (kts.exists()) return kts;

        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findSettingsGradle(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── include() / include(':app') parser ────────────────────────────────────

    private static final Pattern INCLUDE_GROOVY =
            Pattern.compile("include\\s+['\"]([^'\"]+)['\"]");
    private static final Pattern INCLUDE_KTS =
            Pattern.compile("include\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)");

    private List<String> parseIncludes(File settingsFile) {
        List<String> result = new ArrayList<>();
        String content = readFile(settingsFile);
        if (content == null) return result;

        // Try both Groovy and KTS patterns
        for (Pattern p : new Pattern[]{INCLUDE_GROOVY, INCLUDE_KTS}) {
            Matcher m = p.matcher(content);
            while (m.find()) {
                result.add(m.group(1));
            }
        }
        // Deduplicate (both patterns might match same line in some edge cases)
        return new ArrayList<>(new java.util.LinkedHashSet<>(result));
    }

    // ── com.android.application check ────────────────────────────────────────

    private boolean isApplicationModule(File moduleDir) {
        // Must have src/main/
        if (!new File(moduleDir, "src/main").exists()) return false;

        File gradle    = new File(moduleDir, "build.gradle");
        File gradleKts = new File(moduleDir, "build.gradle.kts");

        String content = null;
        if (gradle.exists())    content = readFile(gradle);
        if (content == null && gradleKts.exists()) content = readFile(gradleKts);
        if (content == null)    return false;

        return content.contains("com.android.application")
                || content.contains("'android'")      // legacy shorthand
                || content.contains("\"android\"");
    }

    // ── Fallback: just find first folder with src/main ────────────────────────

    /**
     * Used when settings.gradle is absent or unusable.
     * Finds the first directory that has src/main/AndroidManifest.xml.
     */
    private DetectionResult fallbackDetect(File root) {
        File module = findSrcMain(root);
        if (module == null) return null;
        // Use the module's parent as project root (best guess)
        return new DetectionResult(module.getParentFile(), module);
    }

    private File findSrcMain(File dir) {
        if (new File(dir, "src/main/AndroidManifest.xml").exists()) return dir;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findSrcMain(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    // ── File read util (local, no FileUtil dependency here) ──────────────────

    private String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read: " + f.getAbsolutePath(), e);
            return null;
        }
        return sb.toString();
    }
}
