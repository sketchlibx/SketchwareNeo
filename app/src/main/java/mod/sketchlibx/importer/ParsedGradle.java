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
    public String namespace     = null;   // null → falls back to applicationId
    public int    versionCode   = 1;
    public String versionName   = "1.0";
    public int    minSdk        = 21;
    public int    targetSdk     = 34;
    public int    compileSdk    = 34;

    /** e.g. "1.8", "11", "17" — from compileOptions sourceCompatibility. */
    public String javaVersion = "1.8";

    // ── Built-in Sketchware library flags ────────────────────────────────────
    // These map directly to ProjectLibraryBean libType 0-3.

    public boolean hasFirebase   = false;   // libType 0
    // libType 1 (AppCompat) is always true — Sketchware always enables it
    public boolean hasAdMob      = false;   // libType 2
    public boolean hasGoogleMaps = false;   // libType 3

    // ── Source-language flags ─────────────────────────────────────────────────

    public boolean hasKotlin    = false;
    public boolean hasMaterial3 = false;
    public boolean viewBindingEnabled = false;
    public boolean dataBindingEnabled = false;  // NOTE: generated Binding classes are never imported
    public boolean multiDexEnabled    = false;

    // ── Native / local library flags ─────────────────────────────────────────

    /** True if jniLibs/ exists, an ndk block is found, or source-level JNI exists → libType 5 */
    public boolean hasNativeLibs = false;

    public enum NativeBuildSystem { NONE, CMAKE, NDK_BUILD }

    /** Which native build system this module actually uses, if any. */
    public NativeBuildSystem nativeBuildSystem = NativeBuildSystem.NONE;

    /**
     * Relative paths to .aar / .jar files found in libs/ folder.
     * Each maps to a ProjectLibraryBean with libType=4.
     */
    public final List<String> localLibPaths = new ArrayList<>();

    // ── Proguard / consumer rules ────────────────────────────────────────────

    /** Absolute path to the module's proguard-rules.pro, or null if none declared. */
    public String proguardRulesPath = null;

    /** Absolute path to consumer-rules.pro, or null if none declared. */
    public String consumerRulesPath = null;

    public boolean minifyEnabledInRelease = false;

    // ── Flavors / build types (best-effort names only) ──────────────────────

    public final List<String> productFlavors = new ArrayList<>();
    public final List<String> buildTypes = new ArrayList<>();

    // ── Signing config — warning only, never imported ────────────────────────

    /** True if a signingConfigs {} block exists in this module. Never auto-imported. */
    public boolean hasSigningConfig = false;
}
