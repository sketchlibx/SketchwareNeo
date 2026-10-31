package mod.sketchlibx.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds all data extracted from AndroidManifest.xml.
 * No logic here — pure data carrier.
 */
public class ParsedManifest {

    public String packageName = "com.imported.project";
    public String appName     = "Imported App";
    public String iconResName = "ic_launcher";   // raw name, no @drawable/ prefix
    public String appTheme    = "";               // raw value, e.g. "@style/AppTheme"

    /** Android attributes to preserve on the generated <application> element. */
    public final Map<String, String> applicationAttributes = new LinkedHashMap<>();

    /** Simple class name of android:name on <application>, null if none declared. */
    public String applicationClassName = null;

    public final List<ActivityEntry> activities = new ArrayList<>();
    /** Permission names for Neo's data/permission file. */
    public final List<String> permissions = new ArrayList<>();
    /** Full standard uses-permission attributes for the manifest injector. */
    public final List<PermissionEntry> permissionEntries = new ArrayList<>();
    /** Manifest constructs that this importer cannot inject without changing their semantics. */
    public final List<String> unsupportedManifestItems = new ArrayList<>();
    public final List<ComponentEntry> services = new ArrayList<>();
    public final List<ComponentEntry> receivers = new ArrayList<>();
    public final List<ComponentEntry> providers = new ArrayList<>();


    public static class PermissionEntry {
        public String tagName = "uses-permission";
        public String name = "";
        public final Map<String, String> attributes = new LinkedHashMap<>();
    }

    // ── Shared data for <service> / <receiver> / <provider> ──────────────────

    public static class ComponentEntry {

        public String rawClassName;
        public String simpleClassName;
        public String qualifiedClassName;
        public boolean exported = false;
        public String permission = "";

        /** Only populated for <provider> entries. */
        public String authorities = "";
        /** Serialized source XML for this service/receiver/provider, including intent filters. */
        public String rawXml = "";

        public ComponentEntry(String rawClassName, String simpleClassName) {
            this.rawClassName    = rawClassName;
            this.simpleClassName = simpleClassName;
        }
    }

    // ── Per-activity data ─────────────────────────────────────────────────────

    public static class ActivityEntry {

        /** Fully-qualified or relative class name as written in manifest */
        public String rawClassName;

        /**
         * Simple class name resolved against manifest package.
         * e.g. ".MainActivity" + "com.example" → "MainActivity"
         */
        public String simpleClassName;

        /** Fully-qualified name resolved against the manifest package. */
        public String qualifiedClassName;

        /**
         * android:screenOrientation value, raw string from manifest.
         * Null / empty → treat as portrait.
         */
        public String orientation = "";

        /**
         * android:windowSoftInputMode value, raw string from manifest.
         * e.g. "adjustResize", "stateHidden"
         */
        public String softInputMode = "";

        /** android:theme on this specific activity (may be empty → inherit app theme) */
        public String theme = "";

        /** Android attributes from the source Activity other than android:name. */
        public final Map<String, String> androidAttributes = new LinkedHashMap<>();

        /** Raw nested intent-filter/meta-data/property snippets from the source Activity. */
        public String childComponentsXml = "";

        /** True if this activity has MAIN + LAUNCHER intent-filter */
        public boolean isLauncher = false;

        public ActivityEntry(String rawClassName, String simpleClassName) {
            this.rawClassName    = rawClassName;
            this.simpleClassName = simpleClassName;
        }

        // ── Sketchware field conversions ──────────────────────────────────────

        /**
         * Maps android:screenOrientation → ProjectFileBean orientation int.
         *   0 = portrait (default)
         *   1 = landscape
         *   2 = both (sensor / unspecified)
         */
        public int sketchwareOrientation() {
            if (orientation == null || orientation.isEmpty()) return 0;
            switch (orientation.toLowerCase()) {
                case "landscape":
                case "reverseLandscape":
                case "sensorLandscape":
                case "userLandscape":
                    return 1;
                case "fullSensor":
                case "sensor":
                case "user":
                case "behind":
                case "unspecified":
                    return 2;
                default:
                    return 0; // portrait
            }
        }

        /**
         * Maps android:windowSoftInputMode → ProjectFileBean keyboardSetting int.
         *   0 = unspecified
         *   1 = visible
         *   2 = hidden
         */
        public int sketchwareKeyboard() {
            if (softInputMode == null || softInputMode.isEmpty()) return 0;
            String s = softInputMode.toLowerCase();
            if (s.contains("statevisible") || s.contains("adjustresize")) return 1;
            if (s.contains("statehidden")  || s.contains("adjustpan"))    return 2;
            return 0;
        }
    }
}
