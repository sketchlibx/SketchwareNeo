package mod.sketchlibx.importer;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all data extracted from build.gradle / build.gradle.kts.
 * No logic here — pure data carrier.
 */
public class ParsedGradle {

    // ── App identity ──────────────────────────────────────────────────────────

    public String applicationId = "com.imported.project";
    public int    versionCode   = 1;
    public String versionName   = "1.0";
    public int    minSdk        = 21;
    public int    targetSdk     = 34;

    // ── Built-in Sketchware library flags ────────────────────────────────────
    // These map directly to ProjectLibraryBean libType 0-3.

    public boolean hasFirebase   = false;   // libType 0
    // libType 1 (AppCompat) is always true — Sketchware always enables it
    public boolean hasAdMob      = false;   // libType 2
    public boolean hasGoogleMaps = false;   // libType 3

    // ── Source-language flags ─────────────────────────────────────────────────

    public boolean hasKotlin    = false;
    public boolean hasMaterial3 = false;

    // ── Native / local library flags ─────────────────────────────────────────

    /** True if jniLibs/ exists or a ndk block is found → libType 5 */
    public boolean hasNativeLibs = false;

    /**
     * Relative paths to .aar / .jar files found in libs/ folder.
     * Each maps to a ProjectLibraryBean with libType=4.
     */
    public final List<String> localLibPaths = new ArrayList<>();
}
