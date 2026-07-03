package mod.sketchlibx.importer;

import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import pro.sketchware.tools.ViewBeanParser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Android Studio layout XML files into Sketchware's
 * "view" and "file" data formats.
 *
 * Key design rules (confirmed from source analysis):
 *  - Gson MUST use excludeFieldsWithoutExposeAnnotation (matches eC's GsonBuilder).
 *  - View section headers: @fileName.xml (uses Sketchware fileName, NOT class name).
 *  - FAB section (@fileName.xml_fab) ONLY written if a FAB ViewBean is found in layout.
 *  - Drawer flag set ONLY if a DrawerLayout ViewBean is found in layout.
 *  - File section: @activity for Activities, @customview for custom views.
 *  - theme field in ProjectFileBean is always -1 (THEME_NONE — confirmed deprecated).
 *
 * FAB/Drawer detection uses ViewBean.type integers.
 * These are the confirmed Sketchware type values (from ViewBeanFactory):
 *   type 16 = FloatingActionButton
 *   type 21 = DrawerLayout
 */
public class LayoutImporter {

    private static final String TAG = "LayoutImporter";

    private static final int VIEW_TYPE_FAB    = 16;
    private static final int VIEW_TYPE_DRAWER = 21;

    // Confirmed from eC.class: GsonBuilder with excludeFieldsWithoutExposeAnnotation
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    // ── Result holder ─────────────────────────────────────────────────────────

    public static class ImportedLayout {
        /** Sketchware fileName (e.g. "main", "my_login"). */
        public final String sketchwareFileName;
        /** fileType: 0=Activity, 1=CustomView, 3=Fragment */
        public final int fileType;
        /** View section string: "@fileName.xml\n{bean}\n{bean}\n" */
        public final String viewSection;
        /** Computed options bitmask from theme + layout structure detection. */
        public final int options;
        /** Keyboard setting (from manifest, passed in). */
        public final int keyboardSetting;
        /** Orientation (from manifest, passed in). */
        public final int orientation;

        public ImportedLayout(
                String sketchwareFileName, int fileType,
                String viewSection, int options,
                int keyboardSetting, int orientation) {
            this.sketchwareFileName = sketchwareFileName;
            this.fileType           = fileType;
            this.viewSection        = viewSection;
            this.options            = options;
            this.keyboardSetting    = keyboardSetting;
            this.orientation        = orientation;
        }
    }

    // ── Options bitmask constants (from ProjectFileBean) ──────────────────────
    private static final int OPTION_TOOLBAR    = 1;
    private static final int OPTION_FULLSCREEN = 2;
    private static final int OPTION_DRAWER     = 4;
    private static final int OPTION_FAB        = 8;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes all layout XML files in the given layout directory.
     *
     * @param layoutDir      res/layout folder
     * @param sources        classified source files (for layout→screen mapping)
     * @param manifestParser result (for activity-level options like keyboard/orientation)
     * @param appTheme       application-level theme string (from manifest)
     * @return list of ImportedLayout, one per successfully parsed layout file
     */
    public List<ImportedLayout> importLayouts(
            File layoutDir,
            List<ClassifiedSource> sources,
            ParsedManifest manifest,
            String appTheme) {

        List<ImportedLayout> results = new ArrayList<>();

        if (!layoutDir.exists() || !layoutDir.isDirectory()) {
            Log.w(TAG, "Layout directory not found: " + layoutDir.getAbsolutePath());
            return results;
        }

        // Build layout→ClassifiedSource map for O(1) lookup
        Map<String, ClassifiedSource> layoutToSource = buildLayoutMap(sources);

        // Build activity name→manifest entry map for keyboard/orientation lookup
        Map<String, ParsedManifest.ActivityEntry> nameToActivity = new HashMap<>();
        for (ParsedManifest.ActivityEntry entry : manifest.activities) {
            nameToActivity.put(entry.simpleClassName, entry);
        }

        File[] xmlFiles = layoutDir.listFiles();
        if (xmlFiles == null) return results;

        for (File xml : xmlFiles) {
            if (!xml.getName().endsWith(".xml")) continue;

            String rawName = xml.getName().replace(".xml", "");
            ImportedLayout imported = processLayout(
                    xml, rawName, layoutToSource, nameToActivity, appTheme);

            if (imported != null) results.add(imported);
        }

        return results;
    }

    // ── Single layout processing ──────────────────────────────────────────────

    private ImportedLayout processLayout(
            File xml,
            String rawLayoutName,
            Map<String, ClassifiedSource> layoutToSource,
            Map<String, ParsedManifest.ActivityEntry> nameToActivity,
            String appTheme) {

        // Find which source class uses this layout
        ClassifiedSource source = layoutToSource.get(rawLayoutName);
        boolean isRegistered = (source != null && source.isSketchwareScreen());

        // If not mapped to any source, treat as custom view
        String sketchwareFileName;
        int fileType;
        int keyboardSetting = 0;
        int orientation     = 0;

        if (isRegistered) {
            sketchwareFileName = source.sketchwareFileName;
            fileType           = source.sketchwareFileType;

            // Look up per-activity manifest settings
            ParsedManifest.ActivityEntry actEntry = nameToActivity.get(source.simpleClassName);
            if (actEntry != null) {
                keyboardSetting = actEntry.sketchwareKeyboard();
                orientation     = actEntry.sketchwareOrientation();
            }
        } else {
            // Unmapped layout → register as custom view using layout name as fileName
            sketchwareFileName = rawLayoutName;
            fileType           = ProjectFileBean.PROJECT_FILE_TYPE_CUSTOM_VIEW; // 1
        }

        // ── Parse XML → ViewBeans ─────────────────────────────────────────────
        ArrayList<ViewBean> beans;
        try {
            ViewBeanParser parser = new ViewBeanParser(xml);
            parser.setSkipRoot(true);
            beans = parser.parse();
        } catch (Exception e) {
            Log.e(TAG, "ViewBeanParser failed for: " + xml.getName(), e);
            beans = new ArrayList<>();
        }

        // ── Detect FAB and DrawerLayout from parsed beans ─────────────────────
        boolean hasFab    = false;
        boolean hasDrawer = false;
        for (ViewBean bean : beans) {
            if (bean.type == VIEW_TYPE_FAB)    hasFab    = true;
            if (bean.type == VIEW_TYPE_DRAWER)  hasDrawer = true;
        }

        // ── Compute options bitmask ───────────────────────────────────────────
        int options = 0;
        String effectiveTheme = (source != null
                && source.kind == ClassifiedSource.Kind.ACTIVITY
                && nameToActivity.containsKey(source.simpleClassName)
                && !nameToActivity.get(source.simpleClassName).theme.isEmpty())
                ? nameToActivity.get(source.simpleClassName).theme
                : appTheme;

        if (isNoActionBarTheme(effectiveTheme))  options |= OPTION_TOOLBAR;
        if (isFullscreenTheme(effectiveTheme))   options |= OPTION_FULLSCREEN;
        if (hasDrawer)                            options |= OPTION_DRAWER;
        if (hasFab)                              options |= OPTION_FAB;

        // ── Build view section string ─────────────────────────────────────────
        StringBuilder viewSection = new StringBuilder();
        viewSection.append("@").append(sketchwareFileName).append(".xml\n");
        for (ViewBean bean : beans) {
            viewSection.append(GSON.toJson(bean)).append("\n");
        }

        // FAB section: only when FAB was detected in layout
        if (hasFab) {
            viewSection.append("@").append(sketchwareFileName).append(".xml_fab\n");
            // FAB ViewBean is already in beans (type=16), no separate JSON needed
            // Sketchware reads the _fab section for the standalone FAB widget reference
            // Write an empty section — Sketchware creates a default FAB if section exists
        }

        return new ImportedLayout(
                sketchwareFileName, fileType,
                viewSection.toString(), options,
                keyboardSetting, orientation);
    }

    // ── Helper: build layout name → ClassifiedSource map ─────────────────────

    private Map<String, ClassifiedSource> buildLayoutMap(List<ClassifiedSource> sources) {
        Map<String, ClassifiedSource> map = new HashMap<>();
        for (ClassifiedSource cs : sources) {
            if (cs.associatedLayout != null && !cs.associatedLayout.isEmpty()) {
                // Only register the first match for a given layout name
                if (!map.containsKey(cs.associatedLayout)) {
                    map.put(cs.associatedLayout, cs);
                } else {
                    Log.w(TAG, "Layout '" + cs.associatedLayout
                            + "' claimed by both " + map.get(cs.associatedLayout).simpleClassName
                            + " and " + cs.simpleClassName + " — keeping first.");
                }
            }
        }
        return map;
    }

    // ── Theme string inspection ───────────────────────────────────────────────

    private boolean isNoActionBarTheme(String theme) {
        if (theme == null) return false;
        String t = theme.toLowerCase();
        return t.contains("noactionbar") || t.contains("no_action_bar");
    }

    private boolean isFullscreenTheme(String theme) {
        if (theme == null) return false;
        String t = theme.toLowerCase();
        return t.contains("fullscreen") || t.contains("notitlebar");
    }

    // ── Assemble final "file" data string ─────────────────────────────────────

    /**
     * Builds the complete "file" data string from a list of imported layouts.
     * Format (confirmed from ProjectFileBean + file data analysis):
     *
     *   @activity
     *   {ProjectFileBean JSON for activity 1}
     *   {ProjectFileBean JSON for activity 2}
     *   @customview
     *   {ProjectFileBean JSON for custom view 1}
     *
     * Always ensures at least one "main" activity exists (Sketchware requirement).
     */
    public String buildFileData(List<ImportedLayout> layouts) {
        StringBuilder activities  = new StringBuilder();
        StringBuilder customViews = new StringBuilder();
        boolean hasMain = false;

        for (ImportedLayout layout : layouts) {
            String json = buildProjectFileBeanJson(layout);
            if (layout.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY
                    || layout.fileType == ProjectFileBean.PROJECT_FILE_TYPE_FRAGMENT) {
                activities.append(json).append("\n");
                if ("main".equals(layout.sketchwareFileName)) hasMain = true;
            } else {
                customViews.append(json).append("\n");
            }
        }

        // Sketchware requires at least one activity named "main"
        if (!hasMain) {
            activities.append(buildDefaultMainActivity()).append("\n");
        }

        return "@activity\n" + activities.toString()
             + "@customview\n" + customViews.toString();
    }

    /** Builds the complete "view" data string from all imported layouts. */
    public String buildViewData(List<ImportedLayout> layouts) {
        StringBuilder sb = new StringBuilder();
        for (ImportedLayout layout : layouts) {
            sb.append(layout.viewSection);
        }
        return sb.toString();
    }

    // ── JSON builders ─────────────────────────────────────────────────────────

    /**
     * Builds a ProjectFileBean JSON line for the file data section.
     * Fields are written manually to guarantee field order and prevent
     * including non-@Expose fields from ProjectFileBean.
     *
     * Confirmed fields (from ProjectFileBean @Expose analysis):
     *   fileName, fileType, keyboardSetting, options, orientation, theme
     */
    private String buildProjectFileBeanJson(ImportedLayout layout) {
        return "{\"fileName\":\"" + layout.sketchwareFileName + "\","
             + "\"fileType\":"     + layout.fileType          + ","
             + "\"keyboardSetting\":" + layout.keyboardSetting + ","
             + "\"options\":"      + layout.options            + ","
             + "\"orientation\":"  + layout.orientation        + ","
             + "\"theme\":-1}";    // always -1 (THEME_NONE — confirmed deprecated)
    }

    private String buildDefaultMainActivity() {
        return "{\"fileName\":\"main\","
             + "\"fileType\":0,"
             + "\"keyboardSetting\":0,"
             + "\"options\":0,"
             + "\"orientation\":0,"
             + "\"theme\":-1}";
    }
}
