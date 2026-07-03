package mod.sketchlibx.importer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses build.gradle (Groovy DSL) and build.gradle.kts (Kotlin DSL).
 *
 * Handles both DSL forms for every field:
 *   applicationId "com.example"      ← Groovy (no equals)
 *   applicationId = "com.example"    ← Kotlin DSL (with equals)
 *
 * Does NOT attempt to evaluate variables or version catalog references
 * (e.g. libs.versions.toml). If a field resolves to a variable reference
 * instead of a literal, the safe default is kept and a warning is logged.
 */
public class GradleParser {

    private static final String TAG = "GradleParser";

    // ── Patterns for defaultConfig fields ─────────────────────────────────────

    // Both Groovy and KTS: optional '=' between key and value
    private static final Pattern P_APP_ID = Pattern.compile(
            "applicationId\\s*=?\\s*[\"']([^\"']+)[\"']");
    private static final Pattern P_VER_CODE = Pattern.compile(
            "versionCode\\s*=?\\s*(\\d+)");
    private static final Pattern P_VER_NAME = Pattern.compile(
            "versionName\\s*=?\\s*[\"']([^\"']+)[\"']");
    private static final Pattern P_MIN_SDK = Pattern.compile(
            "minSdk(?:Version)?\\s*=?\\s*(\\d+)");
    private static final Pattern P_TARGET_SDK = Pattern.compile(
            "targetSdk(?:Version)?\\s*=?\\s*(\\d+)");

    // ── Entry point ───────────────────────────────────────────────────────────

    /**
     * @param appModuleDir  The app module folder (contains build.gradle or build.gradle.kts).
     */
    public ParsedGradle parse(File appModuleDir) {
        ParsedGradle result = new ParsedGradle();

        File gradle    = new File(appModuleDir, "build.gradle");
        File gradleKts = new File(appModuleDir, "build.gradle.kts");

        String content = null;
        if (gradle.exists())          content = readFile(gradle);
        else if (gradleKts.exists())  content = readFile(gradleKts);

        if (content == null) {
            Log.w(TAG, "No build.gradle found. Using safe defaults.");
            return result;
        }

        // ── App identity ───────────────────────────────────────────────────────
        result.applicationId = extractString(content, P_APP_ID,    result.applicationId);
        result.versionName   = extractString(content, P_VER_NAME,  result.versionName);
        result.versionCode   = extractInt(content,    P_VER_CODE,   result.versionCode);
        result.minSdk        = extractInt(content,    P_MIN_SDK,    result.minSdk);
        result.targetSdk     = extractInt(content,    P_TARGET_SDK, result.targetSdk);

        // ── Language detection ─────────────────────────────────────────────────
        result.hasKotlin = content.contains("kotlin-android")
                || content.contains("org.jetbrains.kotlin")
                || content.contains("kotlin(\"android\")");

        // ── Material3 ─────────────────────────────────────────────────────────
        result.hasMaterial3 = content.contains("material3")
                || content.contains("androidx.compose.material3");

        // ── Library detection from dependencies {} block ───────────────────────
        String depsBlock = extractDependenciesBlock(content);
        if (depsBlock != null) {
            parseDependencies(depsBlock, result);
        }

        // ── Native lib detection ───────────────────────────────────────────────
        result.hasNativeLibs = content.contains("externalNativeBuild")
                || content.contains("cmake {")
                || content.contains("ndkBuild {")
                || new File(appModuleDir, "src/main/jniLibs").exists()
                || new File(appModuleDir, "CMakeLists.txt").exists()
                || new File(appModuleDir, "Android.mk").exists();

        // ── Local .aar / .jar detection ───────────────────────────────────────
        File libsDir = new File(appModuleDir, "libs");
        if (libsDir.exists() && libsDir.isDirectory()) {
            File[] libFiles = libsDir.listFiles();
            if (libFiles != null) {
                for (File f : libFiles) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".aar") || name.endsWith(".jar")) {
                        result.localLibPaths.add(f.getAbsolutePath());
                        Log.d(TAG, "Local lib found: " + f.getName());
                    }
                }
            }
        }

        Log.d(TAG, "Parsed gradle: appId=" + result.applicationId
                + " vCode=" + result.versionCode
                + " minSdk=" + result.minSdk);
        return result;
    }

    // ── Dependencies block extraction ─────────────────────────────────────────

    /**
     * Extracts the content of the outermost dependencies { } block.
     * Returns null if not found.
     */
    private String extractDependenciesBlock(String content) {
        int start = content.indexOf("dependencies");
        if (start < 0) return null;

        int braceStart = content.indexOf('{', start);
        if (braceStart < 0) return null;

        int depth = 0;
        int end   = braceStart;
        for (int i = braceStart; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }
        return content.substring(braceStart + 1, end);
    }

    // ── Dependency keyword scanning ───────────────────────────────────────────

    private void parseDependencies(String deps, ParsedGradle out) {

        // Firebase — any firebase dependency
        if (deps.contains("firebase")) {
            out.hasFirebase = true;
            Log.d(TAG, "Detected Firebase dependency.");
        }

        // AdMob
        if (deps.contains("play-services-ads")
                || deps.contains("google-mobile-ads")
                || deps.contains("admob")) {
            out.hasAdMob = true;
            Log.d(TAG, "Detected AdMob dependency.");
        }

        // Google Maps
        if (deps.contains("play-services-maps")
                || deps.contains("maps-sdk")
                || deps.contains("google-maps")) {
            out.hasGoogleMaps = true;
            Log.d(TAG, "Detected Google Maps dependency.");
        }
    }

    // ── Regex helpers ─────────────────────────────────────────────────────────

    private String extractString(String content, Pattern p, String fallback) {
        Matcher m = p.matcher(content);
        if (m.find()) {
            String val = m.group(1);
            // Reject if it looks like a variable reference (starts with $, or no dots/letters only)
            if (val.startsWith("$") || (!val.contains(".") && val.matches("[A-Z_]+"))) {
                Log.w(TAG, "Pattern " + p.pattern()
                        + " matched a variable ref '" + val + "', using default.");
                return fallback;
            }
            return val;
        }
        return fallback;
    }

    private int extractInt(String content, Pattern p, int fallback) {
        Matcher m = p.matcher(content);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); }
            catch (NumberFormatException e) { /* fall through */ }
        }
        return fallback;
    }

    // ── File reader ───────────────────────────────────────────────────────────

    private String readFile(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + f.getAbsolutePath(), e);
            return null;
        }
    }
}
