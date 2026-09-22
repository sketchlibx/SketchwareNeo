package mod.sketchlibx.importer;

import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;

import java.util.HashMap;

import a.a.a.lC;
import a.a.a.nB;
import a.a.a.oB;
import a.a.a.wq;

import pro.sketchware.utility.FileUtil;

/**
 * Writes all Sketchware Neo project files in the correct order,
 * using oB for AES-128-CBC-PKCS5Padding encryption (same as Sketchware itself).
 *
 * Confirmed encryption (from oB.class bytecode disassembly):
 *   Algorithm : AES/CBC/PKCS5Padding
 *   Key       : "sketchwaresecure".getBytes(UTF-8)   (16 bytes)
 *   IV        : "sketchwaresecure".getBytes(UTF-8)   (SAME bytes as key — confirmed)
 *
 * Write order (must match what Sketchware expects):
 *   AES-encrypted (via oB, all read back through the same key/IV):
 *     mysc/list/<sc_id>/project, data/<sc_id>/{file,view,logic,resource,library}
 *   Plaintext (confirmed verbatim from a real decrypted Sketchware Neo project):
 *     data/<sc_id>/{project_config,build_config,permission,local_library,
 *                   proguard,proguard-rules.pro,stringfog,service,java,
 *                   custom_blocks,view_root}
 *     data/<sc_id>/Injection/androidmanifest/{activity_launcher.txt,
 *                   app_components.txt,attributes.json}
 *   Optional:
 *     data/<sc_id>/custom_manifest.xml (only if actually populated)
 *
 * This full file set (beyond the core 5 AES-encrypted files) was reverse-engineered
 * by decrypting a real, working Sketchware Neo project and diffing its data/
 * directory against what earlier versions of this class wrote — several required
 * files (project_config, build_config, permission, local_library, proguard,
 * proguard-rules.pro, stringfog, service, java, view_root, and the three
 * Injection/androidmanifest/* files) were previously missing entirely.
 *
 * sc_id isolation:
 *   lC.b() reads the existing project list and returns max(existing_ids) + 1.
 *   This guarantees no collision with any existing Sketchware project.
 *
 * Atomic write guarantee:
 *   All validation happens BEFORE this class is called.
 *   If any write fails, the partially-written project is cleaned up
 *   by deleting data/<sc_id>/ and mysc/list/<sc_id>/ entirely.
 *
 * IMPORTANT — post-write patching:
 *   ResourceMapper can only compute real resource data / custom-icon status
 *   AFTER write() has run (it needs the sc_id-derived destination paths that
 *   write() creates). Any code that needs to update the already-written
 *   project MUST go through patchIconFlags()/patchResourceData() below —
 *   never touch lC/oB directly from outside this class, and never call
 *   lC.b(String, HashMap) with a partial map (see patchIconFlags() doc for
 *   why that overload is dangerous).
 */
public class SketchwareWriter {

    private static final String TAG = "SketchwareWriter";

    // Holds the generated sc_id after write() is called
    private String generatedScId = null;

    public String getGeneratedScId() {
        return generatedScId;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static class WriteInput {
        // Project metadata (from ManifestParser + GradleParser)
        public String appName      = "Imported App";
        public String packageName  = "com.imported.project";
        public int    versionCode  = 1;
        public String versionName  = "1.0";
        public int    minSdk       = 21;
        public int    targetSdk    = 34;
        public boolean hasKotlin   = false;

        // Icon
        public boolean hasCustomIcon   = false;
        public boolean isIconAdaptive  = false;

        // Sketchware data strings (built by LayoutImporter, ResourceMapper, LibraryDetector)
        public String fileData     = "@activity\n@customview\n";
        public String viewData     = "";
        public String resourceData = "@images\n@sounds\n@fonts\n";
        public String libraryData  = "";

        /** data/view_root JSON — built by LayoutImporter.buildViewRootData(). "{}" if no
         *  screen's root tag could be read (Sketchware falls back to its own default root). */
        public String viewRootData = "{}";

        /** Full data/logic content: real converted blocks (JavaLogicConverter) where possible,
         *  addSourceDirectly fallback for everything else, built by ASProjectImporter per
         *  Activity. If left at the default "" here, write() falls back to
         *  buildLogicData(fileData) — the old behavior of empty, structurally-valid section
         *  headers only (no real logic) — so this class still degrades safely if a caller
         *  doesn't wire real conversion in. */
        public String logicData = "";

        /** data/permission JSON array of manifest <uses-permission> strings, e.g.
         *  ["android.permission.INTERNET"]. "[]" if none. */
        public String permissionsJson = "[]";

        /** Sketchware fileName (not class name) of the launcher screen, e.g. "main".
         *  Written verbatim to data/Injection/androidmanifest/activity_launcher.txt. */
        public String launcherFileName = "main";

        /** Written to project_config's "app_class". Left at Sketchware's own default
         *  unless a real custom Application class was found AND actually wired in —
         *  this importer currently only copies a detected custom Application class as
         *  plain Java (see ASProjectImporter's APPLICATION-kind handling) without
         *  replacing Sketchware's own Application, so this should stay at the default. */
        public String appClass = ".SketchApplication";

        // Raw manifest content (for custom_manifest.xml — currently always "" since
        // buildCustomManifest() is an intentional stub; see ASProjectImporter javadoc).
        public String manifestContent = "";
    }

    /**
     * Performs all writes.
     * @throws Exception if any write fails (caller is responsible for cleanup).
     */
    public void write(WriteInput input) throws Exception {

        oB enc = new oB();

        // ── Step 1: Generate isolated sc_id ──────────────────────────────────
        // lC.b() confirmed: reads mysc/list/ directory, finds max numeric folder name,
        // returns (max + 1) as a String. Never reuses existing IDs.
        generatedScId = lC.b();
        Log.d(TAG, "Generated sc_id: " + generatedScId);

        String dataPath  = wq.b(generatedScId);  // .sketchware/data/<sc_id>
        String filesPath = dataPath + "/files";

        // ── Step 2: Create directory structure ────────────────────────────────
        enc.f(dataPath);
        enc.f(filesPath);
        enc.f(filesPath + "/java");
        enc.f(filesPath + "/resource");
        enc.f(filesPath + "/assets");
        enc.f(filesPath + "/app-icon");
        // Created one level at a time (not assuming oB.f() recurses like mkdirs() —
        // matches how every other nested path above was already created step by step).
        enc.f(dataPath + "/Injection");
        enc.f(dataPath + "/Injection/androidmanifest");

        // ── Step 3: Write project metadata file ───────────────────────────────
        // lC.a() internally:
        //   1. vB.a(projMap) → Gson.toJson(HashMap)
        //   2. oB.d(json)    → AES encrypt
        //   3. Writes to wq.c(sc_id) + "/project"
        HashMap<String, Object> projMap = buildProjectMap(input, generatedScId);
        lC.a(generatedScId, projMap);
        Log.d(TAG, "Wrote: project");

        // ── Step 4: Write the AES-encrypted data files ─────────────────────────
        enc.a(dataPath + "/file",    enc.d(input.fileData));
        Log.d(TAG, "Wrote: file\n" + input.fileData);

        enc.a(dataPath + "/view",    enc.d(input.viewData));
        Log.d(TAG, "Wrote: view (" + input.viewData.length() + " chars)");

        enc.a(dataPath + "/logic",   enc.d(input.logicData != null && !input.logicData.isEmpty()
                ? input.logicData : buildLogicData(input.fileData)));
        Log.d(TAG, "Wrote: logic (" + (input.logicData != null ? input.logicData.length() : 0) + " chars real content)");

        enc.a(dataPath + "/resource", enc.d(input.resourceData));
        Log.d(TAG, "Wrote: resource");

        enc.a(dataPath + "/library",  enc.d(input.libraryData));
        Log.d(TAG, "Wrote: library");

        // ── Step 5: Write the plaintext config/manifest files ─────────────────
        // Every value below except where noted is confirmed verbatim (or as an
        // explicitly-safe default matching a fresh Sketchware project) from decrypting
        // a real, working Sketchware Neo project's data/ directory. Before this fix,
        // NONE of these files were written at all — the imported project was missing
        // required files that a genuine Sketchware project always has.

        // data/project_config — confirmed keys/format from a real project.
        FileUtil.writeFile(dataPath + "/project_config", buildProjectConfig(input));
        Log.d(TAG, "Wrote: project_config");

        // data/build_config — confirmed content is a fixed, project-independent
        // default in real projects (D8/Java 1.8/logcat on) — written verbatim.
        FileUtil.writeFile(dataPath + "/build_config",
                "{\"dexer\":\"D8\",\"classpath\":\"\",\"enable_logcat\":\"true\","
                        + "\"no_http_legacy\":\"false\",\"android_jar\":\"\","
                        + "\"no_warn\":\"true\",\"java_ver\":\"1.8\"}");
        Log.d(TAG, "Wrote: build_config");

        // data/permission — JSON array of manifest <uses-permission> strings.
        FileUtil.writeFile(dataPath + "/permission", input.permissionsJson);
        Log.d(TAG, "Wrote: permission");

        // data/local_library — confirmed real format is an array of fully pre-processed
        // descriptors (dexPath/jarPath/manifestPath/assetsPath/pgRulesPath under
        // .sketchware/libs/local_libs/<name>/) pointing at a dexed+manifest-extracted
        // copy of each .aar/.jar. This importer only copies the RAW .aar/.jar into
        // files/libs/ (see ASProjectImporter step 14) — it does not dex them or extract
        // their manifest, so it cannot honestly populate real entries here yet. Writing
        // "[]" is the safe choice: any local libraries copied to files/libs/ will be
        // present on disk after import but are NOT yet wired into the actual build —
        // flagging this clearly rather than fabricating descriptor paths that don't exist.
        FileUtil.writeFile(dataPath + "/local_library", "[]");
        Log.d(TAG, "Wrote: local_library (raw .aar/.jar copies are NOT yet auto-registered — see comment)");

        // data/proguard — confirmed real default for a project with no obfuscation set up.
        FileUtil.writeFile(dataPath + "/proguard", "{\"debug\":\"false\",\"enabled\":\"false\"}");
        Log.d(TAG, "Wrote: proguard");

        // data/proguard-rules.pro — confirmed real default ProGuard rule file content.
        FileUtil.writeFile(dataPath + "/proguard-rules.pro",
                "-repackageclasses\n-ignorewarnings\n-dontwarn\n-dontnote\n");
        Log.d(TAG, "Wrote: proguard-rules.pro");

        // data/stringfog — confirmed real default (string obfuscation off).
        FileUtil.writeFile(dataPath + "/stringfog", "{\"enabled\":\"false\"}");
        Log.d(TAG, "Wrote: stringfog");

        // data/service — confirmed: must exist, confirmed real projects have it EMPTY.
        FileUtil.writeFile(dataPath + "/service", "");
        Log.d(TAG, "Wrote: service (empty)");

        // data/java — confirmed: a separate, always-empty top-level marker file.
        // NOT the same thing as files/java/ (the real Java source directory, already
        // created above) — this bare marker must still exist alongside it.
        FileUtil.writeFile(dataPath + "/java", "");
        Log.d(TAG, "Wrote: java (empty top-level marker)");

        // data/custom_blocks — confirmed format is a JSON array of user-defined custom
        // block specs. A freshly imported project has none, so "[]" is the honest and
        // safe value (an invented placeholder block would show up as a real, unwanted
        // custom block in Sketchware's block editor).
        FileUtil.writeFile(dataPath + "/custom_blocks", "[]");
        Log.d(TAG, "Wrote: custom_blocks");

        // data/view_root — per-screen root container (tag name + raw XML attributes,
        // e.g. background/id/fillViewport). Built by LayoutImporter.buildViewRootData().
        // Before this fix, root backgrounds/ConstraintLayout roots/ScrollView roots were
        // silently dropped on every import (ViewBeanParser.setSkipRoot(true) discards
        // the root tag from the ViewBean tree — this file is the only place it's kept).
        FileUtil.writeFile(dataPath + "/view_root", input.viewRootData);
        Log.d(TAG, "Wrote: view_root");

        // data/Injection/androidmanifest/activity_launcher.txt — plain text, the
        // launcher screen's Sketchware fileName (verbatim, no quotes/JSON).
        FileUtil.writeFile(dataPath + "/Injection/androidmanifest/activity_launcher.txt",
                input.launcherFileName);
        Log.d(TAG, "Wrote: Injection/androidmanifest/activity_launcher.txt = " + input.launcherFileName);

        // data/Injection/androidmanifest/app_components.txt — confirmed to exist but
        // EMPTY in a real project with no <service>/<receiver>/<provider> entries. This
        // importer currently has no confirmed real-sample schema for the POPULATED case
        // (services/receivers/providers are copied as plain Java only — see
        // ASProjectImporter's reconcileComponents() warnings) — writing a guessed schema
        // here risks a manifest merge that's subtly wrong in a way that's hard to notice.
        // Left empty and documented rather than guessed; manual registration in
        // Sketchware's Manifest Manager is still required for these, exactly as already
        // warned to the user during import.
        FileUtil.writeFile(dataPath + "/Injection/androidmanifest/app_components.txt", "");
        Log.d(TAG, "Wrote: Injection/androidmanifest/app_components.txt (empty — see comment)");

        // data/Injection/androidmanifest/attributes.json — confirmed real format is an
        // array of extra <application> tag attributes (theme/backup rules/etc.), but the
        // real sample's values reference Sketchware's OWN auto-generated resource names
        // (its own @style/Theme.<AppName>, @xml/backup_rules) which only exist once
        // Sketchware itself creates/opens the project — this importer cannot predict
        // those names, so "[]" is the safe, non-corrupting default; Sketchware applies
        // its own standard defaults here regardless.
        FileUtil.writeFile(dataPath + "/Injection/androidmanifest/attributes.json", "[]");
        Log.d(TAG, "Wrote: Injection/androidmanifest/attributes.json");

        // data/custom_manifest.xml — optional; only written if actually populated.
        // Currently always empty (buildCustomManifest() is an intentional stub).
        if (input.manifestContent != null && !input.manifestContent.isEmpty()) {
            FileUtil.writeFile(dataPath + "/custom_manifest.xml", input.manifestContent);
            Log.d(TAG, "Wrote: custom_manifest.xml");
        }
    }

    /**
     * Deletes the partially-created project directories on failure.
     * Safe to call even if directories were never created.
     */
    public void rollback() {
        if (generatedScId == null) return;
        try {
            String dataPath = wq.b(generatedScId);
            FileUtil.deleteFile(dataPath);
            Log.w(TAG, "Rolled back data dir: " + dataPath);

            // Also delete the mysc/list entry that lC.a() may have created
            String listPath = wq.c(generatedScId);
            FileUtil.deleteFile(listPath);
            Log.w(TAG, "Rolled back list dir: " + listPath);
        } catch (Exception e) {
            Log.e(TAG, "Rollback error (non-fatal)", e);
        }
    }

    // ── Post-write patch API ────────────────────────────────────────────────
    //
    // ResourceMapper can only determine the real custom-icon / resource-data
    // values AFTER write() has run (it needs the sc_id-derived destination
    // paths that write() itself creates). These two methods are the ONLY
    // sanctioned way to update an already-written project afterwards.
    //
    // Both are implemented using lC.b(String) [full read, no field dropping]
    // + lC.a(String, HashMap) [full unconditional write of exactly what you
    // give it]. Neither uses lC.b(String, HashMap) — that overload
    // unconditionally copies 11 identity/color fields via hashMap.get(key)
    // with NO null-guard, so calling it with anything less than a complete
    // map silently nulls out my_app_name / my_sc_pkg_name / my_ws_name /
    // sc_ver_code / sc_ver_name / sketchware_ver / all five color fields.
    // That is real, confirmed behavior in lC.b(String,HashMap) — never call
    // it with a partial map from anywhere in this importer.

    /**
     * Updates custom_icon / isIconAdaptive on an already-written project
     * without touching (or risking corrupting) any other field.
     *
     * @return true if the patch was applied, false if the project could not
     *         be found on disk (write() was never called, or was rolled back).
     */
    public boolean patchIconFlags(String scId, boolean hasCustomIcon, boolean isIconAdaptive) {
        HashMap<String, Object> existing = lC.b(scId);
        if (existing == null) {
            Log.w(TAG, "patchIconFlags: no project found for sc_id=" + scId);
            return false;
        }
        existing.put("custom_icon", hasCustomIcon);
        existing.put("isIconAdaptive", isIconAdaptive);
        lC.a(scId, existing);
        Log.d(TAG, "Patched icon flags for sc_id=" + scId
                + " (custom_icon=" + hasCustomIcon + ", isIconAdaptive=" + isIconAdaptive + ")");
        return true;
    }

    /**
     * Overwrites the "resource" data file for an already-written project
     * with the real image/sound/font entries computed by ResourceMapper
     * (write() itself only had the WriteInput.resourceData default/placeholder
     * available at the time it ran, since real resource data requires the
     * sc_id-derived paths write() creates).
     */
    public void patchResourceData(String scId, String resourceData) {
        String dataPath = wq.b(scId);
        oB enc = new oB();
        enc.a(dataPath + "/resource", enc.d(resourceData));
        Log.d(TAG, "Patched resource data for sc_id=" + scId
                + " (" + resourceData.length() + " chars)");
    }

    // ── project HashMap builder ───────────────────────────────────────────────

    /**
     * Builds the HashMap that lC.a() will serialize to the "project" file.
     *
     * Key types confirmed from:
     *   - lC.java field reads  (shows which fields are put/expected)
     *   - yB.class bytecode    (confirms Gson reads all numbers as Double,
     *                            so Integer values survive Double.intValue() cast)
     *   - MyProjectSettingActivity.java (shows projMap.put() types used by Sketchware itself)
     *
     * String fields: put as String
     * Numeric fields: put as Integer (Gson serializes to number; yB reads via Double.intValue())
     * Boolean fields: put as Boolean
     */
    private HashMap<String, Object> buildProjectMap(WriteInput input, String scId) {
        HashMap<String, Object> map = new HashMap<>();

        map.put("sc_id",            scId);                              // String
        map.put("my_app_name",      input.appName);                    // String
        map.put("my_ws_name",       sanitizeWorkspaceName(input.appName)); // String
        map.put("my_sc_pkg_name",   input.packageName);                // String
        map.put("sc_ver_code",      String.valueOf(input.versionCode)); // String (confirmed from lC)
        map.put("sc_ver_name",      input.versionName);                // String

        // Color scheme defaults (same as Sketchware's own new-project defaults)
        map.put("color_primary",           -10455380);  // Integer
        map.put("color_primary_dark",      -10455380);  // Integer
        map.put("color_accent",            -10455380);  // Integer
        map.put("color_control_highlight", -2497793);   // Integer
        map.put("color_control_normal",    -10455380);  // Integer

        // Registration timestamp — confirmed field name "my_sc_reg_dt" (format
        // "yyyyMMddHHmmss" from nB.class) directly from a real decrypted project file.
        // (Earlier versions of this method invented "sc_create_in"/"sc_save_in" fields
        // that do not actually exist in a real project — corrected here.)
        map.put("my_sc_reg_dt", new nB().a("yyyyMMddHHmmss"));  // String

        // Sketchware version
        map.put("sketchware_ver", 158);  // Integer

        // Icon flags — real values are applied later via patchIconFlags() once
        // ResourceMapper has run (it needs sc_id-derived paths that only exist
        // after this write() call completes). Written here as the input-provided
        // starting values so a caller who already knows the icon status upfront
        // (e.g. a future ResourceMapper that pre-scans without copying) doesn't
        // need the post-write patch at all.
        map.put("custom_icon",    input.hasCustomIcon);   // Boolean
        map.put("isIconAdaptive", input.isIconAdaptive);  // Boolean

        return map;
    }

    // ── logic data builder ────────────────────────────────────────────────────

    /**
     * FALLBACK ONLY — used when a caller doesn't provide real converted logicData
     * (WriteInput.logicData empty). Builds the minimal structurally-valid empty
     * sections so Sketchware can still open/navigate the project without crashing,
     * with no real logic.
     *
     * Section set corrected against sketchware-neo-swb-analysis-complete.md (built
     * from decrypting a real, working project — stronger evidence than the earlier
     * eC.class/jC.java bytecode inference this method previously relied on, which
     * had gotten the per-function/list section names wrong):
     *   @<javaName>_components               ← always present, even empty (confirmed)
     *   @<javaName>_events                   ← always present, even empty (confirmed)
     *   @<javaName>_onCreate_initializeLogic  ← always present (confirmed, protected section)
     *   @<javaName>_onBackPressed_onBackPressed ← always present (confirmed)
     * `_var`/`_list` are confirmed OMITTED ENTIRELY when empty (not written as empty
     * sections) — no longer emitted here. Per-function `_<funcName>_moreBlock`
     * sections only exist when a function is actually defined — also not emitted
     * here, since this fallback path defines none.
     *
     * The `@<javaName>_` prefix convention on every section name is carried over
     * from the earlier eC.class/jC.java evidence — the newer real-project doc lists
     * section suffixes (_components, _events, onCreate_initializeLogic, etc.)
     * without transcribing a raw example line showing whether/how they're prefixed
     * per screen, so this specific prefix has NOT been independently re-confirmed
     * against the doc. It's kept because some per-screen namespacing must exist
     * (all screens' sections live in one shared data/logic file, and section names
     * like "onCreate_initializeLogic" would otherwise collide across every Activity
     * in the project), and no alternative confirmed convention is available.
     *
     * javaName is derived via ProjectFileBean.getActivityName(fileName) + ".java" —
     * the same derivation Sketchware's own editor uses.
     */
    private String buildLogicData(String fileData) {
        StringBuilder sb = new StringBuilder();

        // Parse fileData to extract registered fileNames
        // Format: @activity\n{bean}\n{bean}\n@customview\n{bean}\n
        for (String line : fileData.split("\n")) {
            line = line.trim();
            if (line.startsWith("{") && line.contains("\"fileName\"")) {
                try {
                    // Extract fileName from the JSON line
                    String fileName = extractJsonStringField(line, "fileName");
                    if (fileName != null && !fileName.isEmpty()) {
                        String javaName = ProjectFileBean.getActivityName(fileName) + ".java";
                        sb.append("@").append(javaName).append("_components\n");
                        sb.append("@").append(javaName).append("_events\n");
                        sb.append("@").append(javaName).append("_onCreate_initializeLogic\n");
                        sb.append("@").append(javaName).append("_onBackPressed_onBackPressed\n");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse fileName from: " + line, e);
                }
            }
        }

        return sb.toString();
    }

    // ── project_config builder ────────────────────────────────────────────────

    /**
     * Builds data/project_config (plain JSON, NOT encrypted).
     *
     * All 13 keys and their exact string-typed formatting are confirmed verbatim
     * from a real decrypted project's data/project_config:
     *   {"min_sdk":"21","enable_viewbinding":"true","force_androidx":"true",
     *    "compile_sdk_version":"35","multidex":"false","target_sdk":"35",
     *    "disable_old_methods":"true","xml_command":"true",
     *    "enable_version_history":"false","custom_manifest":"false",
     *    "enable_bridgeless_themes":"false","app_class":".SketchApplication",
     *    "custom_java":"false"}
     *
     * min_sdk/target_sdk use the real values parsed from the AS project's
     * build.gradle by GradleParser. compile_sdk_version is not separately tracked
     * by GradleParser today, so it's set equal to target_sdk — the overwhelmingly
     * common convention in real Android projects (compileSdk >= targetSdk, usually
     * equal) — rather than a hardcoded, possibly-stale constant.
     */
    private String buildProjectConfig(WriteInput input) {
        return "{"
             + "\"min_sdk\":\"" + input.minSdk + "\","
             + "\"enable_viewbinding\":\"true\","
             + "\"force_androidx\":\"true\","
             + "\"compile_sdk_version\":\"" + input.targetSdk + "\","
             + "\"multidex\":\"false\","
             + "\"target_sdk\":\"" + input.targetSdk + "\","
             + "\"disable_old_methods\":\"true\","
             + "\"xml_command\":\"true\","
             + "\"enable_version_history\":\"false\","
             + "\"custom_manifest\":\"false\","
             + "\"enable_bridgeless_themes\":\"false\","
             + "\"app_class\":\"" + input.appClass + "\","
             // "false" even in real projects that DO have extensive custom Java files
             // (confirmed from the same real sample, which has several) — this flag
             // tracks something else internal to Sketchware, not "has custom Java",
             // so it's left at the confirmed real default rather than guessed.
             + "\"custom_java\":\"false\""
             + "}";
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /**
     * Sanitizes app name to a valid Sketchware workspace name.
     * my_ws_name must contain only alphanumeric + spaces.
     */
    private String sanitizeWorkspaceName(String appName) {
        return appName.replaceAll("[^a-zA-Z0-9 ]", "").trim();
    }

    /**
     * Minimal JSON field extractor — used to parse fileName from file data lines
     * without pulling in a full JSON library dependency just for this one field.
     *
     * Parses: {"fileName":"main","fileType":0,...}
     *                      ^^^^
     */
    private String extractJsonStringField(String json, String fieldName) {
        String searchKey = "\"" + fieldName + "\":\"";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        int end = json.indexOf('"', start);
        if (end < 0) return null;
        return json.substring(start, end);
    }
}
