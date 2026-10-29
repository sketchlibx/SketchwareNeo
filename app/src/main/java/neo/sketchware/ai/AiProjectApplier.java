package neo.sketchware.ai;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.manage.view.ScreenPackageManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.wq;
import a.a.a.yq;
import mod.hey.studios.project.ProjectSettings;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;

/** Applies AI-generated layout and Java specifications into a real Sketchware Neo project. */
public final class AiProjectApplier {
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    private static final Pattern RESOURCE_NAME = Pattern.compile("^[a-z][a-z0-9_]{0,79}$");
    private static final Pattern COLOR_VALUE = Pattern.compile("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$");
    private static final Pattern DIMEN_VALUE = Pattern.compile("^[0-9]+(?:\\.[0-9]+)?(?:dp|sp|px)$");
    private static final Pattern VIEW_ID_REF = Pattern.compile("R\\.id\\.([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern UNSAFE_JAVA_API = Pattern.compile("(?i)\\b(?:Runtime\\s*\\.\\s*getRuntime|ProcessBuilder|DexClassLoader|PathClassLoader|Class\\s*\\.\\s*forName|System\\s*\\.\\s*(?:load|loadLibrary)|HttpURLConnection|Socket|java\\.net\\.)\\b");
    private static final Pattern XML_DTD_OR_ENTITY = Pattern.compile("(?is)<!\\s*(?:DOCTYPE|ENTITY)\\b");
    private static final Pattern SET_CONTENT_VIEW = Pattern.compile("setContentView\\s*\\(\\s*(?:R\\.layout\\.[A-Za-z_][A-Za-z0-9_]*|binding\\.getRoot\\(\\))\\s*\\)\\s*;");
    private static final int MAX_SCREENS = 6;

    public interface ProgressListener {
        void onProgress(int stage, String title, String detail);
    }

    public static final class Result {
        public final boolean success;
        public final String message;
        public final String launchScreen;
        Result(boolean success, String message, String launchScreen) {
            this.success = success;
            this.message = message;
            this.launchScreen = launchScreen;
        }
    }

    private static final class PreparedScreen {
        final String targetName;
        final String sourceName;
        final String layoutXml;
        final String onCreateCode;
        final String helperMethods;
        final String rootName;
        final Map<String, String> rootAttributes;
        final List<ViewBean> views;
        final Set<String> viewIds;
        PreparedScreen(String targetName, String sourceName, String layoutXml, String onCreateCode,
                       String helperMethods, String rootName, Map<String, String> rootAttributes,
                       List<ViewBean> views, Set<String> viewIds) {
            this.targetName = targetName;
            this.sourceName = sourceName;
            this.layoutXml = layoutXml;
            this.onCreateCode = onCreateCode;
            this.helperMethods = helperMethods;
            this.rootName = rootName;
            this.rootAttributes = rootAttributes;
            this.views = views;
            this.viewIds = viewIds;
        }
    }

    private AiProjectApplier() { }

    /** Runs on the caller's worker thread; UI callbacks are the caller's responsibility. */
    public static Result apply(Context context, String scId, JSONObject specification, ProgressListener progress) {
        if (context == null || TextUtils.isEmpty(scId) || specification == null) {
            return new Result(false, "Project details are missing.", null);
        }
        File projectRoot;
        try {
            projectRoot = new File(wq.b(scId)).getCanonicalFile();
            if (!projectRoot.isDirectory()) return new Result(false, "The new project's data folder could not be found.", null);
        } catch (Exception exception) {
            return new Result(false, "Could not resolve the new project's data folder: " + safeMessage(exception), null);
        }

        ArrayList<PreparedScreen> prepared = new ArrayList<>();
        JSONArray screens = specification.optJSONArray("screens");
        if (screens == null || screens.length() == 0 || screens.length() > MAX_SCREENS) {
            return new Result(false, "The project specification has an invalid number of screens.", null);
        }

        try {
            validateResourceValues(specification.optJSONObject("values"));
            report(progress, 3, "Generating layouts and logic", "Validating each screen before changing the project");
            Set<String> names = new HashSet<>();
            for (int i = 0; i < screens.length(); i++) {
                JSONObject screen = screens.getJSONObject(i);
                String targetName = normalizeScreenName(screen.optString("name", "screen_" + (i + 1)), i);
                while (!names.add(targetName.toLowerCase(Locale.ROOT))) {
                    targetName = fitName(targetName + "_" + (i + 1));
                }
                String sourceName = "aigen_source_" + (i + 1);
                String xml = screen.optString("layoutXml", "").trim();
                if (xml.isEmpty() || xml.length() > 45000) throw new IllegalArgumentException("Layout for '" + targetName + "' is empty or too large.");
                if (XML_DTD_OR_ENTITY.matcher(xml).find()) throw new IllegalArgumentException("Layout for '" + targetName + "' cannot contain a document type or entity declaration.");
                JSONObject values = specification.optJSONObject("values");
                if (values != null) xml = rewriteDeclaredValueReferences(xml, values);

                ViewBeanParser parser = new ViewBeanParser(xml);
                parser.setSkipRoot(true);
                ArrayList<ViewBean> views = parser.parse();
                Pair<String, Map<String, String>> parsedRoot = parser.getRootAttributes();
                if (parsedRoot == null || TextUtils.isEmpty(parsedRoot.first) || parsedRoot.second == null) {
                    throw new IllegalArgumentException("Layout for '" + targetName + "' does not have a supported root element.");
                }
                String rootName = parsedRoot.first;
                if (!isSupportedRoot(rootName)) {
                    throw new IllegalArgumentException("Use a built-in LinearLayout, FrameLayout, RelativeLayout or ScrollView as the root of '" + targetName + "'.");
                }
                Map<String, String> rootAttributes = new LinkedHashMap<>(parsedRoot.second);
                rootAttributes.putIfAbsent("android:layout_width", "match_parent");
                rootAttributes.putIfAbsent("android:layout_height", "match_parent");

                Set<String> ids = new HashSet<>();
                if (views != null) {
                    for (ViewBean view : views) {
                        if (view == null || TextUtils.isEmpty(view.id)) continue;
                        if (!ids.add(view.id)) throw new IllegalArgumentException("Layout for '" + targetName + "' contains a duplicate view id: " + view.id);
                    }
                }
                String code = screen.optString("onCreateCode", "").trim();
                String methods = screen.optString("helperMethods", "").trim();
                if (values != null) {
                    code = rewriteDeclaredJavaValueReferences(code, values);
                    methods = rewriteDeclaredJavaValueReferences(methods, values);
                }
                validateJavaFragment(targetName, code, methods, ids);
                prepared.add(new PreparedScreen(targetName, sourceName, xml, code, methods, rootName,
                        rootAttributes, views == null ? new ArrayList<>() : views, ids));
            }

            report(progress, 4, "Packaging native screen data", "Creating a Sketchware Neo screen package");
            String sourceTemplate = firstActivityName(scId);
            File templateFile = ScreenPackageManager.createExportFile(context, scId,
                    Collections.singletonList(sourceTemplate), new ScreenPackageManager.ExportOptions(false, false));
            File packageFile = buildNativePackage(context, templateFile, sourceTemplate, prepared);
            ScreenPackageManager.PackageContent packageContent = ScreenPackageManager.readPackage(packageFile);
            LinkedHashMap<String, String> requestedNames = new LinkedHashMap<>();
            for (PreparedScreen screen : prepared) requestedNames.put(screen.sourceName, screen.targetName);

            report(progress, 5, "Applying project changes", "Importing layouts into Neo's native screen registry");
            ScreenPackageManager.Result importResult = ScreenPackageManager.importScreens(context, scId,
                    packageContent, requestedNames, false, false);
            if (!importResult.success) return new Result(false, importResult.message, null);

            InjectRootLayoutManager roots = new InjectRootLayoutManager(scId);
            for (PreparedScreen screen : prepared) {
                roots.set(screen.targetName + ".xml", new InjectRootLayoutManager.Root(screen.rootName, screen.rootAttributes));
            }
            installJavaOverrides(context, scId, prepared);
            writeGeneratedValues(projectRoot, specification, prepared);
            AndroidManifestInjector.setLauncherActivity(scId, prepared.get(0).targetName);
            report(progress, 6, "Checking build setup", "Saved screen data, resources, Java logic and launch activity");
            try { if (packageFile.exists()) packageFile.delete(); } catch (Exception ignored) { }
            try { if (templateFile.exists()) templateFile.delete(); } catch (Exception ignored) { }
            return new Result(true, "Created " + prepared.size() + " native screen(s). The project is ready for a real build check.", prepared.get(0).targetName);
        } catch (Exception exception) {
            return new Result(false, "Project generation stopped safely: " + safeMessage(exception), null);
        }
    }

    private static void report(ProgressListener listener, int stage, String title, String detail) {
        if (listener != null) listener.onProgress(stage, title, detail);
    }

    private static String firstActivityName(String scId) throws Exception {
        hC registry = jC.b(scId);
        if (registry == null || registry.b() == null || registry.b().isEmpty()) {
            throw new IllegalStateException("The new project has no starter Activity to use as an import template.");
        }
        for (ProjectFileBean bean : registry.b()) {
            if (bean != null && bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && "main".equals(bean.fileName)) return bean.fileName;
        }
        for (ProjectFileBean bean : registry.b()) {
            if (bean != null && bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) return bean.fileName;
        }
        throw new IllegalStateException("No activity-style starter screen is available in the new project.");
    }

    private static File buildNativePackage(Context context, File templateFile, String originalName,
                                          List<PreparedScreen> screens) throws Exception {
        JSONObject templateScreen;
        try (ZipFile zip = new ZipFile(templateFile)) {
            ZipEntry entry = zip.getEntry("screens/screen_0.json");
            if (entry == null) throw new IllegalStateException("The starter screen package has no screen payload.");
            templateScreen = new JSONObject(new String(readBounded(zip.getInputStream(entry), 8 * 1024 * 1024), StandardCharsets.UTF_8));
        }
        String originalXml = originalName + ".xml";
        String originalJava = ProjectFileBean.getJavaName(originalName);
        String originalActivity = ProjectFileBean.getActivityName(originalName);
        String originalDrawer = ProjectFileBean.getDrawerName(originalName);
        String originalDrawerXml = originalDrawer + ".xml";
        String originalDrawerJava = ProjectFileBean.getJavaName(originalDrawer);

        File outputDir = new File(context.getCacheDir(), "ai-native-project-package");
        if (!outputDir.isDirectory() && !outputDir.mkdirs() && !outputDir.isDirectory()) throw new IllegalStateException("Cannot create temporary package folder.");
        File output = new File(outputDir, "ai-project-" + System.currentTimeMillis() + ".swnscreen");
        JSONObject manifest = new JSONObject();
        manifest.put("format", "SketchwareNeoScreenPackage");
        manifest.put("schemaVersion", 1);
        manifest.put("includesLogic", false);
        manifest.put("includesJavaSources", false);
        manifest.put("exportedAtEpochMs", System.currentTimeMillis());
        JSONArray descriptors = new JSONArray();
        JSONArray emptyJavaFiles = new JSONArray();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output))) {
            for (int index = 0; index < screens.size(); index++) {
                PreparedScreen prepared = screens.get(index);
                JSONObject screenJson = new JSONObject(templateScreen.toString());
                JSONObject bean = screenJson.getJSONObject("screen");
                bean.put("fileName", prepared.sourceName);
                // The AI screen supplies its own root and header; do not carry a starter toolbar or drawer.
                bean.put("options", 0);
                bean.put("theme", ProjectFileBean.THEME_NONE);
                screenJson.put("drawer", JSONObject.NULL);
                JSONObject data = screenJson.getJSONObject("data");
                JSONArray fields = data.names();
                if (fields != null) {
                    for (int fieldIndex = 0; fieldIndex < fields.length(); fieldIndex++) {
                        String fieldName = fields.optString(fieldIndex, "");
                        JSONObject entries = data.optJSONObject(fieldName);
                        if (entries == null) continue;
                        renameMapKey(entries, originalName, prepared.sourceName);
                        renameMapKey(entries, originalXml, prepared.sourceName + ".xml");
                        renameMapKey(entries, originalJava, ProjectFileBean.getJavaName(prepared.sourceName));
                        renameMapKey(entries, originalActivity, ProjectFileBean.getActivityName(prepared.sourceName));
                        entries.remove(originalDrawer);
                        entries.remove(originalDrawerXml);
                        entries.remove(originalDrawerJava);
                    }
                }
                JSONObject layouts = data.optJSONObject("c");
                if (layouts == null) throw new IllegalStateException("Starter screen layout data was missing.");
                layouts.put(prepared.sourceName + ".xml", new JSONArray(GSON.toJson(prepared.views)));
                // The source data key may be present under the old name if a starter file uses a nonstandard key.
                if (!layouts.has(prepared.sourceName + ".xml")) throw new IllegalStateException("Could not attach the generated layout to the native package.");

                String entryName = "screens/screen_" + index + ".json";
                byte[] screenBytes = screenJson.toString().getBytes(StandardCharsets.UTF_8);
                putZipEntry(zip, entryName, screenBytes);
                JSONObject descriptor = new JSONObject(bean.toString());
                descriptor.put("index", index);
                descriptor.put("entry", entryName);
                descriptors.put(descriptor);
            }
            manifest.put("screens", descriptors);
            manifest.put("javaFiles", emptyJavaFiles);
            putZipEntry(zip, "manifest.json", manifest.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            try { output.delete(); } catch (Exception ignored) { }
            throw exception;
        }
        return output;
    }

    private static void renameMapKey(JSONObject object, String oldKey, String newKey) throws Exception {
        if (object == null || oldKey == null || newKey == null || oldKey.equals(newKey) || !object.has(oldKey)) return;
        Object value = object.get(oldKey);
        object.remove(oldKey);
        object.put(newKey, value);
    }

    private static void putZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > maxBytes) throw new IllegalStateException("Starter screen data exceeded the size limit.");
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        }
    }

    private static void installJavaOverrides(Context context, String scId, List<PreparedScreen> screens) throws Exception {
        File root = new File(wq.b(scId)).getCanonicalFile();
        File customJavaDir = new File(root, "custom_java");
        if (!customJavaDir.isDirectory() && !customJavaDir.mkdirs() && !customJavaDir.isDirectory()) {
            throw new IllegalStateException("Cannot create the project's custom Java folder.");
        }
        hC registry = jC.b(scId);
        eC data = jC.a(scId);
        iC libraries = jC.c(scId);
        if (registry == null || data == null || libraries == null) throw new IllegalStateException("The imported screen registry could not be loaded.");
        yq sourceGenerator = new yq(context, scId);
        boolean anyOverride = false;

        for (PreparedScreen screen : screens) {
            if (screen.onCreateCode.isEmpty() && screen.helperMethods.isEmpty()) continue;
            ProjectFileBean bean = registry.a(ProjectFileBean.getActivityName(screen.targetName));
            if (bean == null) {
                for (ProjectFileBean candidate : registry.b()) {
                    if (candidate != null && screen.targetName.equals(candidate.fileName)) { bean = candidate; break; }
                }
            }
            if (bean == null) throw new IllegalStateException("Could not resolve the imported screen '" + screen.targetName + "' for Java logic.");
            String source = sourceGenerator.getFileSrc(bean.getJavaName(), registry, data, libraries);
            if (TextUtils.isEmpty(source)) throw new IllegalStateException("Could not generate the base Java class for '" + screen.targetName + "'.");
            String rewritten = injectJava(source, bean.getXmlName(), screen.onCreateCode, screen.helperMethods);
            File target = new File(customJavaDir, bean.getJavaName()).getCanonicalFile();
            if (!isInside(target, customJavaDir.getCanonicalFile())) throw new IllegalStateException("Generated Java path escaped the project folder.");
            writeAtomic(target, rewritten);
            anyOverride = true;
        }
        if (anyOverride) new ProjectSettings(scId).setValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "true");
    }

    private static String injectJava(String source, String layoutName, String onCreateCode, String helperMethods) {
        if (containsForbiddenJavaWrapper(onCreateCode) || containsForbiddenJavaWrapper(helperMethods)) {
            throw new IllegalArgumentException("Generated Java logic must be statements and helper methods, not another class or package declaration.");
        }
        String rewritten = source;
        Matcher contentViewMatcher = SET_CONTENT_VIEW.matcher(rewritten);
        if (!TextUtils.isEmpty(onCreateCode)) {
            if (!contentViewMatcher.find()) throw new IllegalArgumentException("Could not find the generated setContentView call; no custom Java was saved.");
            String statement = contentViewMatcher.group();
            int insertAt = contentViewMatcher.end();
            rewritten = rewritten.substring(0, insertAt) + "\n        // AI-generated project logic\n        " + onCreateCode + "\n        " + rewritten.substring(insertAt);
        }
        if (!TextUtils.isEmpty(helperMethods)) {
            int end = rewritten.lastIndexOf('}');
            if (end < 0) throw new IllegalArgumentException("The generated Activity class has no closing brace.");
            rewritten = rewritten.substring(0, end) + "\n\n    // AI-generated helper methods\n    " + helperMethods + "\n" + rewritten.substring(end);
        }
        rewritten = addImport(rewritten, "android.content.*");
        rewritten = addImport(rewritten, "android.graphics.*");
        rewritten = addImport(rewritten, "android.view.*");
        rewritten = addImport(rewritten, "android.widget.*");
        rewritten = addImport(rewritten, "java.util.*");
        return rewritten;
    }

    private static String addImport(String source, String className) {
        String line = "import " + className + ";";
        if (source.contains(line)) return source;
        Matcher packageMatcher = Pattern.compile("(?m)^\\s*package\\s+[^;]+;").matcher(source);
        if (packageMatcher.find()) {
            int end = packageMatcher.end();
            return source.substring(0, end) + "\n\n" + line + source.substring(end);
        }
        return line + "\n" + source;
    }

    private static boolean containsForbiddenJavaWrapper(String code) {
        if (TextUtils.isEmpty(code)) return false;
        String lower = code.toLowerCase(Locale.ROOT);
        return Pattern.compile("(?m)^\\s*package\\s+[\\w.]+\\s*;").matcher(code).find()
                || Pattern.compile("(?m)^\\s*import\\s+[\\w.*]+\\s*;").matcher(code).find()
                || Pattern.compile("(?m)^\\s*(public|protected|private)?\\s*(abstract\\s+|final\\s+)?class\\s+").matcher(code).find()
                || Pattern.compile("(?i)\\bsetContentView\\s*\\(").matcher(code).find()
                || UNSAFE_JAVA_API.matcher(code).find();
    }

    private static void validateJavaFragment(String name, String code, String methods, Set<String> ids) {
        if (containsForbiddenJavaWrapper(code) || containsForbiddenJavaWrapper(methods)) {
            throw new IllegalArgumentException("Java logic for '" + name + "' must not contain imports, a class wrapper, or setContentView().");
        }
        Matcher matcher = VIEW_ID_REF.matcher(code + "\n" + methods);
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!ids.contains(id)) throw new IllegalArgumentException("Java logic for '" + name + "' references R.id." + id + ", but that id is not present in its layout.");
        }
        if (code.length() > 16000 || methods.length() > 16000) throw new IllegalArgumentException("Java logic for '" + name + "' is too long.");
    }

    private static boolean isSupportedRoot(String rootName) {
        String tag = rootName == null ? "" : rootName.substring(rootName.lastIndexOf('.') + 1);
        return "LinearLayout".equals(tag) || "FrameLayout".equals(tag) || "RelativeLayout".equals(tag)
                || "ScrollView".equals(tag) || "HorizontalScrollView".equals(tag);
    }

    private static String normalizeScreenName(String supplied, int index) {
        String cleaned = supplied == null ? "" : supplied.trim().replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_+|_+$", "");
        if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) cleaned = "screen_" + (index + 1) + (cleaned.isEmpty() ? "" : "_" + cleaned);
        if (!cleaned.toLowerCase(Locale.ROOT).startsWith("ai_")) cleaned = "ai_" + cleaned;
        return fitName(cleaned);
    }

    private static String fitName(String name) {
        if (name == null || name.isEmpty()) return "ai_screen";
        return name.length() > 95 ? name.substring(0, 95) : name;
    }

    private static void validateResourceValues(JSONObject values) {
        if (values == null) return;
        validateResourceGroup(values.optJSONObject("strings"), "string");
        validateResourceGroup(values.optJSONObject("colors"), "color");
        validateResourceGroup(values.optJSONObject("dimens"), "dimen");
    }

    private static void validateResourceGroup(JSONObject group, String type) {
        if (group == null) return;
        JSONArray names = group.names();
        if (names == null) return;
        Set<String> normalizedNames = new HashSet<>();
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            String value = group.optString(name, "");
            if (!RESOURCE_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Invalid Android " + type + " resource name: " + name);
            }
            String normalizedName = name.startsWith("ai_") ? name : "ai_" + name;
            if (!normalizedNames.add(normalizedName)) {
                throw new IllegalArgumentException("The generated " + type + " resource names collide after normalization: " + normalizedName);
            }
            if (value.length() > 4000) throw new IllegalArgumentException("Resource '" + name + "' is too long.");
            if ("color".equals(type) && !COLOR_VALUE.matcher(value.trim()).matches()) {
                throw new IllegalArgumentException("Color resource '" + name + "' must use #RRGGBB or #AARRGGBB.");
            }
            if ("dimen".equals(type) && !DIMEN_VALUE.matcher(value.trim()).matches()) {
                throw new IllegalArgumentException("Dimension resource '" + name + "' must use a dp, sp or px value.");
            }
        }
    }

    private static String rewriteDeclaredValueReferences(String xml, JSONObject values) {
        JSONObject strings = values.optJSONObject("strings");
        JSONObject colors = values.optJSONObject("colors");
        JSONObject dimens = values.optJSONObject("dimens");
        String result = xml;
        result = rewriteRefs(result, "string", strings);
        result = rewriteRefs(result, "color", colors);
        result = rewriteRefs(result, "dimen", dimens);
        return result;
    }

    private static String rewriteRefs(String xml, String resourceType, JSONObject values) {
        if (values == null) return xml;
        JSONArray names = values.names();
        if (names == null) return xml;
        String updated = xml;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            if (!RESOURCE_NAME.matcher(name).matches()) continue;
            String replacement = name.startsWith("ai_") ? name : "ai_" + name;
            updated = updated.replace("@" + resourceType + "/" + name, "@" + resourceType + "/" + replacement);
        }
        return updated;
    }

    private static String rewriteDeclaredJavaValueReferences(String code, JSONObject values) {
        JSONObject strings = values.optJSONObject("strings");
        JSONObject colors = values.optJSONObject("colors");
        JSONObject dimens = values.optJSONObject("dimens");
        String result = code;
        result = rewriteJavaRefs(result, "string", strings);
        result = rewriteJavaRefs(result, "color", colors);
        result = rewriteJavaRefs(result, "dimen", dimens);
        return result;
    }

    private static String rewriteJavaRefs(String code, String resourceType, JSONObject values) {
        if (values == null || TextUtils.isEmpty(code)) return code;
        JSONArray names = values.names();
        if (names == null) return code;
        String updated = code;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            if (!RESOURCE_NAME.matcher(name).matches()) continue;
            String replacement = name.startsWith("ai_") ? name : "ai_" + name;
            updated = updated.replace("R." + resourceType + "." + name, "R." + resourceType + "." + replacement);
        }
        return updated;
    }

    private static void writeGeneratedValues(File projectRoot, JSONObject specification, List<PreparedScreen> screens) throws Exception {
        JSONObject values = specification.optJSONObject("values");
        if (values == null) return;
        JSONObject strings = values.optJSONObject("strings");
        JSONObject colors = values.optJSONObject("colors");
        JSONObject dimens = values.optJSONObject("dimens");
        if ((strings == null || strings.length() == 0) && (colors == null || colors.length() == 0) && (dimens == null || dimens.length() == 0)) return;

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n");
        appendResourceValues(xml, strings, "string");
        appendResourceValues(xml, colors, "color");
        appendResourceValues(xml, dimens, "dimen");
        xml.append("</resources>\n");
        File targetDir = new File(projectRoot, "files/resource/values");
        if (!targetDir.isDirectory() && !targetDir.mkdirs() && !targetDir.isDirectory()) throw new IllegalStateException("Cannot create the project's values resource folder.");
        File target = new File(targetDir, "ai_generated_values.xml");
        writeAtomic(target, xml.toString());
    }

    private static void appendResourceValues(StringBuilder xml, JSONObject values, String type) {
        if (values == null) return;
        JSONArray names = values.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String name = names.optString(i, "");
            String value = values.optString(name, "");
            if (!RESOURCE_NAME.matcher(name).matches() || value.trim().isEmpty()) continue;
            if ("color".equals(type) && !COLOR_VALUE.matcher(value.trim()).matches()) continue;
            if ("dimen".equals(type) && !DIMEN_VALUE.matcher(value.trim()).matches()) continue;
            String targetName = name.startsWith("ai_") ? name : "ai_" + name;
            if ("string".equals(type)) xml.append("    <string name=\"").append(targetName).append("\">").append(escapeXml(value)).append("</string>\n");
            else xml.append("    <").append(type).append(" name=\"").append(targetName).append("\">").append(escapeXml(value)).append("</").append(type).append(">\n");
        }
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void writeAtomic(File target, String content) throws Exception {
        File parent = target.getParentFile();
        if (parent == null) throw new IllegalStateException("Generated file has no parent directory.");
        if (!parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) throw new IllegalStateException("Could not create folder for generated file.");
        File temp = new File(parent, target.getName() + ".aigen.tmp");
        File backup = new File(parent, target.getName() + ".aigen.bak");
        if (temp.exists() && !temp.delete()) throw new IllegalStateException("Cannot clear temporary generated file.");
        if (backup.exists() && !backup.delete()) throw new IllegalStateException("Cannot clear previous generated-file backup.");
        try (FileOutputStream output = new FileOutputStream(temp)) {
            output.write(content.getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        }
        try {
            if (target.exists() && !target.renameTo(backup)) throw new IllegalStateException("Could not preserve existing file '" + target.getName() + "'.");
            if (!temp.renameTo(target)) {
                if (backup.exists()) backup.renameTo(target);
                throw new IllegalStateException("Could not save generated file '" + target.getName() + "'.");
            }
            if (backup.exists()) backup.delete();
        } finally {
            if (temp.exists()) temp.delete();
        }
    }

    private static boolean isInside(File child, File parent) {
        try {
            String root = parent.getCanonicalPath();
            String path = child.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (Exception ignored) { return false; }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return TextUtils.isEmpty(message) ? exception.getClass().getSimpleName() : message;
    }
}
