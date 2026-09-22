package mod.sketchlibx.importer;

import android.util.Log;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import pro.sketchware.tools.ViewBeanParser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Android Studio layout XML files into Sketchware's
 * "view" and "file" data formats.
 *
 * Key design rules (confirmed from source analysis):
 *  - Gson MUST use excludeFieldsWithoutExposeAnnotation (matches eC's GsonBuilder).
 *  - View section headers: @fileName.xml (uses Sketchware fileName, NOT class name).
 *  - FAB section (@fileName.xml_fab) is ALWAYS written for every screen — confirmed
 *    (via full .swb format analysis, cross-checked against a real decrypted project)
 *    present on 100% of real Sketchware Neo screens, Activity or Custom View, whether
 *    or not that screen actually uses a FAB. It holds one disconnected placeholder
 *    widget (no "parent"/"preId" keys — it is NOT part of the parent/child tree) that
 *    represents Sketchware's own reserved screen-level FAB slot. This is structurally
 *    separate from any real <FloatingActionButton> found while parsing the AS layout
 *    tree, which is still imported normally as an ordinary tree-connected ViewBean.
 *  - Drawer flag set ONLY if a DrawerLayout ViewBean is found in layout.
 *  - File section: @activity for Activities, @customview for custom views.
 *  - theme field in ProjectFileBean is always -1 (THEME_NONE — confirmed deprecated).
 *  - The layout XML's ROOT element (ViewBeanParser.setSkipRoot(true) deliberately
 *    discards it from the ViewBean tree) is NOT part of data/view at all — it lives
 *    in a completely separate top-level file, data/view_root, as one JSON object
 *    keyed by "<fileName>.xml" holding {"attributes": {raw XML attr name→value},
 *    "class_name": "<root XML tag, verbatim>"}. Confirmed directly from a real
 *    decrypted project (e.g. root ConstraintLayout/ScrollView with
 *    android:background="@color/bg_root" etc.) — this is why background colors,
 *    ConstraintLayout roots, and ScrollView/fillViewport were silently lost on
 *    import before this file learned to read the root tag at all.
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
        /** Root XML tag name, verbatim (e.g. "androidx.constraintlayout.widget.ConstraintLayout",
         *  "ScrollView", "LinearLayout"). Null if the root tag could not be read. */
        public final String rootClassName;
        /** Root element's raw XML attributes (e.g. "android:background" -> "@color/bg_root"),
         *  in document order. Empty map if the root tag could not be read. */
        public final Map<String, String> rootAttributes;

        public ImportedLayout(
                String sketchwareFileName, int fileType,
                String viewSection, int options,
                int keyboardSetting, int orientation,
                String rootClassName, Map<String, String> rootAttributes) {
            this.sketchwareFileName = sketchwareFileName;
            this.fileType           = fileType;
            this.viewSection        = viewSection;
            this.options            = options;
            this.keyboardSetting    = keyboardSetting;
            this.orientation        = orientation;
            this.rootClassName      = rootClassName;
            this.rootAttributes     = rootAttributes;
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

        // Tracks every sketchwareFileName claimed so far this run, so an unrelated,
        // unregistered layout (e.g. a genuinely different "customer.xml" used only via
        // manual inflate/include, unconnected to any Activity/Fragment) can never silently
        // collide with an already-registered screen whose fileName was DERIVED from its
        // class name rather than its own raw layout filename (e.g. CustomerActivity's own
        // sketchwareFileName can legitimately be "customer" even though its own layout XML
        // is named something else entirely — a coincidental collision with an unrelated
        // "customer.xml" elsewhere in res/layout/ is a real, observed failure mode: two
        // physically different files silently sharing one "@customer.xml" section header
        // in data/view, and one fileName in data/file, corrupts Sketchware's own
        // screen registry — confirmed from a real import log where "customer" and "report"
        // both ended up imported twice under the same fileName with different root layouts).
        Map<String, File> claimedFileNames = new HashMap<>();

        for (File xml : xmlFiles) {
            if (!xml.getName().endsWith(".xml")) continue;

            String rawName = xml.getName().replace(".xml", "");
            ImportedLayout imported = processLayout(
                    xml, rawName, layoutToSource, nameToActivity, appTheme, claimedFileNames);

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
            String appTheme,
            Map<String, File> claimedFileNames) {

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

        // ── Collision guard ─────────────────────────────────────────────────────
        // A REGISTERED screen's fileName is authoritative (it's what the Activity's
        // own Java class, data/file entry, and data/logic sections are all keyed on
        // elsewhere) and is never renamed here. An UNREGISTERED (orphan) layout whose
        // computed fileName collides with one already claimed — by a registered screen
        // OR by an earlier orphan — is disambiguated with a numeric suffix instead of
        // silently sharing that fileName (which would corrupt both files' data/view
        // section and data/file registration — see comment on the caller).
        if (claimedFileNames.containsKey(sketchwareFileName)) {
            if (isRegistered) {
                Log.w(TAG, "sketchwareFileName '" + sketchwareFileName + "' for registered screen "
                        + source.simpleClassName + " collides with an already-processed layout ("
                        + claimedFileNames.get(sketchwareFileName).getName() + "). Both are registered "
                        + "screens with the same derived name — this needs SourceClassifier's fileName "
                        + "derivation fixed; NOT auto-renamed here since this fileName is also used by "
                        + "the Activity's own Java/logic sections elsewhere.");
            } else {
                String originalName = sketchwareFileName;
                int suffix = 2;
                while (claimedFileNames.containsKey(sketchwareFileName)) {
                    sketchwareFileName = originalName + "_" + suffix;
                    suffix++;
                }
                Log.w(TAG, "Unregistered layout " + xml.getName() + " would have collided with "
                        + "already-claimed fileName '" + originalName + "' (used by "
                        + claimedFileNames.get(originalName).getName() + ") — renamed to '"
                        + sketchwareFileName + "' to prevent both files sharing one data/view section.");
            }
        }
        claimedFileNames.put(sketchwareFileName, xml);

        // ── Read root tag (name + attributes) BEFORE the ViewBean parse discards it ──
        RootInfo rootInfo = extractRootInfo(xml);

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

        // FAB section: ALWAYS written (confirmed structural requirement — see class
        // javadoc). Any real FAB widget found in the AS layout tree stays exactly
        // where the parser placed it in `beans` above; this is Sketchware's separate,
        // always-present, reserved screen-level FAB placeholder, not that widget.
        viewSection.append("@").append(sketchwareFileName).append(".xml_fab\n");
        viewSection.append(buildDefaultFabPlaceholderJson()).append("\n");

        return new ImportedLayout(
                sketchwareFileName, fileType,
                viewSection.toString(), options,
                keyboardSetting, orientation,
                rootInfo != null ? rootInfo.className   : null,
                rootInfo != null ? rootInfo.attributes  : new LinkedHashMap<>());
    }

    // ── Root tag extraction ───────────────────────────────────────────────────

    private static class RootInfo {
        final String className;
        final Map<String, String> attributes;
        RootInfo(String className, Map<String, String> attributes) {
            this.className  = className;
            this.attributes = attributes;
        }
    }

    /**
     * Reads only the root element's tag name and attributes, verbatim, before
     * ViewBeanParser (with setSkipRoot(true)) discards it from the ViewBean tree.
     * This is what data/view_root needs — see class javadoc.
     */
    private RootInfo extractRootInfo(File xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xml);
            Element root = doc.getDocumentElement();
            if (root == null) return null;

            String className = root.getTagName(); // verbatim, e.g. "ScrollView" or fully-qualified

            Map<String, String> attrMap = new LinkedHashMap<>();
            NamedNodeMap attrs = root.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node n = attrs.item(i);
                attrMap.put(n.getNodeName(), n.getNodeValue());
            }
            return new RootInfo(className, attrMap);
        } catch (Exception e) {
            Log.w(TAG, "Could not read root tag for " + xml.getName()
                    + " — data/view_root entry will be skipped for this screen.", e);
            return null;
        }
    }

    /**
     * Builds the complete data/view_root JSON object: one entry per screen,
     * keyed by "<fileName>.xml", each holding {"attributes": {...}, "class_name": "..."}.
     * Screens whose root tag could not be read are omitted (Sketchware falls back
     * to its own default root container for those, same as before this fix existed).
     */
    public String buildViewRootData(List<ImportedLayout> layouts) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (ImportedLayout layout : layouts) {
            if (layout.rootClassName == null) continue;
            if (!first) sb.append(",");
            first = false;

            sb.append("\"").append(layout.sketchwareFileName).append(".xml\":{");
            sb.append("\"attributes\":{");
            boolean firstAttr = true;
            for (Map.Entry<String, String> attr : layout.rootAttributes.entrySet()) {
                if (!firstAttr) sb.append(",");
                firstAttr = false;
                sb.append(jsonString(attr.getKey())).append(":").append(jsonString(attr.getValue()));
            }
            sb.append("},");
            sb.append("\"class_name\":").append(jsonString(layout.rootClassName));
            sb.append("}");
        }
        sb.append("}");
        return sb.toString();
    }

    /** Minimal JSON string escaper — quotes + escapes backslash/quote/control chars. */
    private String jsonString(String s) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:   out.append(c);
            }
        }
        return out.append("\"").toString();
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

    /**
     * The disconnected placeholder widget written into every screen's mandatory
     * "_fab" section. Hand-written (not Gson-serialized from the real ViewBean
     * class) specifically so "parent" and "preId" can be omitted entirely, matching
     * the exact shape confirmed from a real decrypted project's data/view section —
     * Gson would otherwise always emit those keys (e.g. as "") even when unused,
     * which does not match the confirmed real-sample structure.
     *
     * Field values below are the full confirmed ViewBean field set with sensible
     * FAB-appropriate defaults (bottom-end gravity, 16dp margins, wrap_content).
     */
    private String buildDefaultFabPlaceholderJson() {
        return "{"
             + "\"id\":\"_fab\","
             + "\"parentType\":-1,"
             + "\"parentAttributes\":{},"
             + "\"index\":0,"
             + "\"preIndex\":0,"
             + "\"preParentType\":0,"
             + "\"customView\":\"\","
             + "\"convert\":\"FloatingActionButton\","
             + "\"inject\":\"\","
             + "\"enabled\":1,"
             + "\"clickable\":1,"
             + "\"alpha\":1.0,"
             + "\"scaleX\":1.0,"
             + "\"scaleY\":1.0,"
             + "\"translationX\":0.0,"
             + "\"translationY\":0.0,"
             + "\"type\":" + VIEW_TYPE_FAB + ","
             + "\"checked\":0,"
             + "\"choiceMode\":0,"
             + "\"dividerHeight\":1,"
             + "\"firstDayOfWeek\":1,"
             + "\"indeterminate\":\"false\","
             + "\"max\":100,"
             + "\"progress\":0,"
             + "\"progressStyle\":\"?android:progressBarStyle\","
             + "\"spinnerMode\":1,"
             + "\"adSize\":\"\","
             + "\"adUnitId\":\"\","
             + "\"layout\":{"
                 + "\"width\":-2,"
                 + "\"height\":-2,"
                 + "\"orientation\":-1,"
                 + "\"gravity\":0,"
                 + "\"layoutGravity\":85,"
                 + "\"weight\":0,"
                 + "\"weightSum\":0,"
                 + "\"marginLeft\":16,"
                 + "\"marginTop\":16,"
                 + "\"marginRight\":16,"
                 + "\"marginBottom\":16,"
                 + "\"paddingLeft\":0,"
                 + "\"paddingTop\":0,"
                 + "\"paddingRight\":0,"
                 + "\"paddingBottom\":0,"
                 + "\"backgroundColor\":16777215,"
                 + "\"borderColor\":-16740915,"
                 + "\"backgroundResource\":\"NONE\","
                 + "\"backgroundResColor\":\"\""
             + "},"
             + "\"text\":{"
                 + "\"hint\":\"\","
                 + "\"hintColor\":16777215,"
                 + "\"imeOption\":0,"
                 + "\"inputType\":1,"
                 + "\"line\":0,"
                 + "\"singleLine\":0,"
                 + "\"text\":\"\","
                 + "\"textColor\":16777215,"
                 + "\"textFont\":\"default_font\","
                 + "\"textSize\":12,"
                 + "\"textType\":0"
             + "},"
             + "\"image\":{"
                 + "\"rotate\":0,"
                 + "\"scaleType\":\"CENTER\""
             + "}"
             + "}";
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
