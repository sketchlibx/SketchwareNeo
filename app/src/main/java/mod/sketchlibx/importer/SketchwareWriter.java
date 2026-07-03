package mod.sketchlibx.importer;

import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;

import java.util.HashMap;
import java.util.List;

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
 *   1.  mysc/list/<sc_id>/project   ← via lC.a()
 *   2.  data/<sc_id>/file           ← via oB.a(path, oB.d(content))
 *   3.  data/<sc_id>/view           ← via oB.a(path, oB.d(content))
 *   4.  data/<sc_id>/logic          ← via oB.a(path, oB.d(content))
 *   5.  data/<sc_id>/resource       ← via oB.a(path, oB.d(content))
 *   6.  data/<sc_id>/library        ← via oB.a(path, oB.d(content))
 *   7.  data/<sc_id>/project_settings (plain JSON, no encryption)
 *   8.  data/<sc_id>/custom_manifest.xml (plain text, no encryption)
 *
 * sc_id isolation:
 *   lC.b() reads the existing project list and returns max(existing_ids) + 1.
 *   This guarantees no collision with any existing Sketchware project.
 *
 * Atomic write guarantee:
 *   All validation happens BEFORE this class is called.
 *   If any write fails, the partially-written project is cleaned up
 *   by deleting data/<sc_id>/ and mysc/list/<sc_id>/ entirely.
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

        // Raw manifest content (for custom_manifest.xml)
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
        enc.f(dataPath + "/custom_java");

        // ── Step 3: Write project metadata file ───────────────────────────────
        // lC.a() internally:
        //   1. vB.a(projMap) → Gson.toJson(HashMap)
        //   2. oB.d(json)    → AES encrypt
        //   3. Writes to wq.c(sc_id) + "/project"
        HashMap<String, Object> projMap = buildProjectMap(input, generatedScId);
        lC.a(generatedScId, projMap);
        Log.d(TAG, "Wrote: project");

        // ── Step 4: Write the five data files (all AES encrypted via oB) ──────
        enc.a(dataPath + "/file",    enc.d(input.fileData));
        Log.d(TAG, "Wrote: file\n" + input.fileData);

        enc.a(dataPath + "/view",    enc.d(input.viewData));
        Log.d(TAG, "Wrote: view (" + input.viewData.length() + " chars)");

        enc.a(dataPath + "/logic",   enc.d(buildLogicData(input.fileData)));
        Log.d(TAG, "Wrote: logic");

        enc.a(dataPath + "/resource", enc.d(input.resourceData));
        Log.d(TAG, "Wrote: resource");

        enc.a(dataPath + "/library",  enc.d(input.libraryData));
        Log.d(TAG, "Wrote: library");

        // ── Step 5: Write project_settings (plain JSON, no encryption) ────────
        FileUtil.writeFile(dataPath + "/project_settings",
                buildProjectSettings(input));
        Log.d(TAG, "Wrote: project_settings");

        // ── Step 6: Write custom_manifest.xml (plain text, no encryption) ─────
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

        // Timestamps — confirmed format "yyyyMMddHHmmss" from nB.class
        String now = new nB().a("yyyyMMddHHmmss");
        map.put("sc_create_in", now);    // String
        map.put("sc_save_in",   now);    // String

        // Sketchware version
        map.put("sketchware_ver", 158);  // Integer

        // Icon flags
        map.put("custom_icon",    input.hasCustomIcon);   // Boolean
        map.put("isIconAdaptive", input.isIconAdaptive);  // Boolean

        return map;
    }

    // ── logic data builder ────────────────────────────────────────────────────

    /**
     * Builds empty but structurally valid logic data for all registered screens.
     *
     * We cannot import Android Studio business logic into Sketchware Blocks —
     * that would require a full decompiler + block-graph reconstruction.
     * Instead we write properly-structured empty sections so Sketchware can
     * open, navigate, and edit the project without crashing.
     *
     * Confirmed section format (from eC.class + jC.java):
     *   @ClassName.java_events        ← event list (empty)
     *   @ClassName.java_components    ← component list (empty)
     *   @ClassName.java_moreBlock     ← custom method specs (empty)
     *   @ClassName.java_definedFunc   ← defined functions (empty)
     *   @ClassName.java_list          ← variable list (empty)
     *   @ClassName.java_func          ← function implementations (empty)
     *
     * javaName is derived via ProjectFileBean.getActivityName(fileName) + ".java"
     * This is the same derivation Sketchware's own editor uses.
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
                        sb.append("@").append(javaName).append("_events\n");
                        sb.append("@").append(javaName).append("_components\n");
                        sb.append("@").append(javaName).append("_moreBlock\n");
                        sb.append("@").append(javaName).append("_definedFunc\n");
                        sb.append("@").append(javaName).append("_list\n");
                        sb.append("@").append(javaName).append("_func\n");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to parse fileName from: " + line, e);
                }
            }
        }

        return sb.toString();
    }

    // ── project_settings builder ──────────────────────────────────────────────

    /**
     * Builds the project_settings JSON (plain text, NOT encrypted).
     * Confirmed from MyProjectSettingActivity: this is a simple JSONObject
     * with string-valued flags.
     */
    private String buildProjectSettings(WriteInput input) {
        try {
            org.json.JSONObject settings = new org.json.JSONObject();
            settings.put("enable_custom_manifest", "true");
            settings.put("enable_viewbinding",     "true");
            settings.put("multidex",               "true"); // safe default for imported AS projects
            if (input.hasKotlin) {
                settings.put("java_to_kotlin", "true");
            }
            return settings.toString();
        } catch (Exception e) {
            // Fallback
            return "{\"enable_custom_manifest\":\"true\",\"enable_viewbinding\":\"true\",\"multidex\":\"true\"}";
        }
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
