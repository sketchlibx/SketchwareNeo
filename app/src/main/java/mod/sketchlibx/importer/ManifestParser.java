package mod.sketchlibx.importer;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses AndroidManifest.xml using Android's XmlPullParser.
 *
 * Does NOT use regex for XML parsing — avoids all attribute-order / whitespace
 * assumptions that regex-based manifest parsing suffers from.
 *
 * Resolves @string/app_name by reading values/strings.xml if needed.
 */
public class ManifestParser {

    private static final String TAG = "ManifestParser";
    private static final String NS  = "http://schemas.android.com/apk/res/android";

    /** src/main directory — used to resolve @string/ and @dimen/ references. */
    private final File srcMain;

    public ManifestParser(File srcMain) {
        this.srcMain = srcMain;
    }

    // ── Public entry point ────────────────────────────────────────────────────

    public ParsedManifest parse() {
        ParsedManifest result = new ParsedManifest();
        File manifestFile = new File(srcMain, "AndroidManifest.xml");
        if (!manifestFile.exists()) {
            Log.w(TAG, "AndroidManifest.xml not found at " + manifestFile.getAbsolutePath());
            return result;
        }

        try (FileInputStream fis = new FileInputStream(manifestFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(fis, null);

            parseInternal(parser, result);

        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to parse AndroidManifest.xml", e);
        }

        // Resolve @string/ references now that we've finished parsing
        resolveStringRefs(result);

        return result;
    }

    // ── XmlPullParser walk ────────────────────────────────────────────────────

    private void parseInternal(XmlPullParser p, ParsedManifest out)
            throws XmlPullParserException, IOException {

        boolean inActivity = false;
        boolean inIntentFilter = false;
        boolean hasMainAction = false;
        boolean hasLauncherCategory = false;
        ParsedManifest.ActivityEntry currentActivity = null;
        int activityChildDepth = 0;

        ParsedManifest.ComponentEntry currentComponent = null;
        java.util.List<ParsedManifest.ComponentEntry> pendingComponentList = null;
        int componentDepth = 0;

        int eventType = p.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            // Service/receiver/provider elements are copied as real manifest XML so their
            // exported flags, permissions, metadata and intent-filters survive import.
            if (currentComponent != null) {
                if (eventType == XmlPullParser.START_TAG) {
                    componentDepth++;
                    currentComponent.rawXml += serializeStartTag(p);
                } else if (eventType == XmlPullParser.END_TAG) {
                    currentComponent.rawXml += "</" + p.getName() + ">";
                    componentDepth--;
                    if (componentDepth <= 0) {
                        if (pendingComponentList != null) pendingComponentList.add(currentComponent);
                        currentComponent = null;
                        pendingComponentList = null;
                        componentDepth = 0;
                    }
                } else if (eventType == XmlPullParser.TEXT && componentDepth > 0) {
                    String text = p.getText();
                    if (text != null && !text.trim().isEmpty()) currentComponent.rawXml += xmlEscapeText(text);
                }
                eventType = p.next();
                continue;
            }

            if (eventType == XmlPullParser.START_TAG) {
                String tag = p.getName();

                // Keep all child XML under an Activity (intent-filter, meta-data, property, etc.)
                // while still passing the same events through the launcher detector below.
                if (currentActivity != null && inActivity && !"activity".equals(tag)) {
                    if (activityChildDepth == 0 && currentActivity.childComponentsXml.length() > 0) {
                        currentActivity.childComponentsXml += "\n";
                    }
                    currentActivity.childComponentsXml += serializeStartTag(p);
                    activityChildDepth++;
                } else if (currentActivity != null && inActivity && activityChildDepth > 0) {
                    currentActivity.childComponentsXml += serializeStartTag(p);
                    activityChildDepth++;
                }

                if ("manifest".equals(tag)) {
                    out.packageName = attr(p, null, "package", out.packageName);

                } else if ("uses-permission".equals(tag)) {
                    String permName = attr(p, NS, "name", "");
                    if (!permName.isEmpty()) {
                        if (!out.permissions.contains(permName)) out.permissions.add(permName);
                        ParsedManifest.PermissionEntry permission = new ParsedManifest.PermissionEntry();
                        permission.name = permName;
                        copyAllAndroidAttributes(p, permission.attributes);
                        out.permissionEntries.add(permission);
                    }
                } else if (tag.startsWith("uses-permission-")) {
                    // Neo's confirmed injector always emits a normal <uses-permission> tag;
                    // silently converting uses-permission-sdk-* would change SDK semantics.
                    out.unsupportedManifestItems.add("<" + tag + "> is preserved in the original manifest reference but is not injected because Neo's importer channel emits only <uses-permission>.");
                } else if ("uses-feature".equals(tag) || "uses-configuration".equals(tag)
                        || "supports-screens".equals(tag) || "queries".equals(tag)) {
                    out.unsupportedManifestItems.add("<" + tag + "> is preserved in files/android_studio_original/AndroidManifest.xml but is not copied into the generated manifest by the current Neo injection API.");

                } else if ("application".equals(tag)) {
                    String rawLabel = attr(p, NS, "label", "");
                    if (!rawLabel.isEmpty()) out.appName = rawLabel;
                    out.appTheme = attr(p, NS, "theme", "");
                    out.iconResName = stripResPrefix(attr(p, NS, "icon", "@mipmap/ic_launcher"));
                    copyAndroidAttributes(p, out.applicationAttributes, true);

                    String appClassRaw = attr(p, NS, "name", "");
                    if (!appClassRaw.isEmpty()) {
                        out.applicationClassName = resolveClassName(appClassRaw, out.packageName);
                    }

                } else if ("service".equals(tag)) {
                    currentComponent = newComponentEntry(p, out.packageName);
                    currentComponent.rawXml = serializeStartTag(p);
                    componentDepth = 1;
                    pendingComponentList = out.services;

                } else if ("receiver".equals(tag)) {
                    currentComponent = newComponentEntry(p, out.packageName);
                    currentComponent.rawXml = serializeStartTag(p);
                    componentDepth = 1;
                    pendingComponentList = out.receivers;

                } else if ("provider".equals(tag)) {
                    currentComponent = newComponentEntry(p, out.packageName);
                    currentComponent.authorities = attr(p, NS, "authorities", "");
                    currentComponent.rawXml = serializeStartTag(p);
                    componentDepth = 1;
                    pendingComponentList = out.providers;

                } else if ("activity".equals(tag)) {
                    inActivity = true;
                    hasMainAction = false;
                    hasLauncherCategory = false;
                    activityChildDepth = 0;

                    String rawName = attr(p, NS, "name", "");
                    String simpleName = resolveClassName(rawName, out.packageName);
                    currentActivity = new ParsedManifest.ActivityEntry(rawName, simpleName);
                    currentActivity.qualifiedClassName = resolveQualifiedClassName(rawName, out.packageName);
                    currentActivity.orientation = attr(p, NS, "screenOrientation", "");
                    currentActivity.softInputMode = attr(p, NS, "windowSoftInputMode", "");
                    currentActivity.theme = attr(p, NS, "theme", "");
                    copyAndroidAttributes(p, currentActivity.androidAttributes, false);

                } else if (!inActivity && ("meta-data".equals(tag) || "uses-library".equals(tag)
                        || "activity-alias".equals(tag) || "property".equals(tag)
                        || "profileable".equals(tag) || "uses-native-library".equals(tag))) {
                    // Do not silently imply these application-level manifest declarations
                    // were imported. The original manifest is preserved for manual recovery.
                    out.unsupportedManifestItems.add("Application-level <" + tag
                            + "> is preserved in files/android_studio_original/AndroidManifest.xml"
                            + " but is not injected into the generated manifest by the current Neo importer.");

                } else if (inActivity && "intent-filter".equals(tag)) {
                    inIntentFilter = true;

                } else if (inIntentFilter && "action".equals(tag)) {
                    String name = attr(p, NS, "name", "");
                    if ("android.intent.action.MAIN".equals(name)) hasMainAction = true;

                } else if (inIntentFilter && "category".equals(tag)) {
                    String name = attr(p, NS, "name", "");
                    if ("android.intent.category.LAUNCHER".equals(name)) hasLauncherCategory = true;
                }

            } else if (eventType == XmlPullParser.TEXT && currentActivity != null && activityChildDepth > 0) {
                String text = p.getText();
                if (text != null && !text.trim().isEmpty()) currentActivity.childComponentsXml += xmlEscapeText(text);

            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = p.getName();

                if (currentActivity != null && inActivity && activityChildDepth > 0 && !"activity".equals(tag)) {
                    currentActivity.childComponentsXml += "</" + tag + ">";
                    activityChildDepth--;
                    if (activityChildDepth == 0) currentActivity.childComponentsXml += "\n";
                }

                if ("intent-filter".equals(tag)) {
                    inIntentFilter = false;

                } else if ("activity".equals(tag)) {
                    if (currentActivity != null) {
                        currentActivity.isLauncher = hasMainAction && hasLauncherCategory;
                        out.activities.add(currentActivity);
                    }
                    inActivity = false;
                    currentActivity = null;
                    activityChildDepth = 0;
                }
            }
            eventType = p.next();
        }
    }

    private void copyAllAndroidAttributes(XmlPullParser p, java.util.Map<String, String> target) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String name = p.getAttributeName(i);
            String ns = p.getAttributeNamespace(i);
            if (ns != null && ns.contains("schemas.android.com/tools")) continue;
            if (NS.equals(ns)) name = "android:" + name;
            else if (ns != null && !ns.isEmpty()) continue;
            String value = p.getAttributeValue(i);
            if (value != null) target.put(name, value);
        }
    }

    private void copyAndroidAttributes(XmlPullParser p, java.util.Map<String, String> target,
                                       boolean application) {
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String name = p.getAttributeName(i);
            String ns = p.getAttributeNamespace(i);
            // Do not copy build-tool-only attributes; their prefixes may not exist in Neo's manifest.
            if (ns != null && ns.contains("schemas.android.com/tools")) continue;
            if (NS.equals(ns)) name = "android:" + name;
            else if (ns != null && !ns.isEmpty()) continue;
            if ("android:name".equals(name)) continue; // screen/application class is generated by Neo.
            if (application && ("android:label".equals(name) || "android:icon".equals(name))) continue;
            String value = p.getAttributeValue(i);
            if (value != null) target.put(name, value);
        }
    }

    private String serializeStartTag(XmlPullParser p) {
        StringBuilder out = new StringBuilder("<").append(p.getName());
        for (int i = 0; i < p.getAttributeCount(); i++) {
            String ns = p.getAttributeNamespace(i);
            if (ns != null && ns.contains("schemas.android.com/tools")) continue;
            String name = p.getAttributeName(i);
            if (NS.equals(ns)) name = "android:" + name;
            else if (ns != null && !ns.isEmpty()) continue;
            out.append(' ').append(name).append("=\"")
                    .append(xmlEscapeAttribute(p.getAttributeValue(i))).append('"');
        }
        return out.append('>').toString();
    }

    private String xmlEscapeAttribute(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private String xmlEscapeText(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ── Helper: build a service/receiver/provider entry ───────────────────────

    private ParsedManifest.ComponentEntry newComponentEntry(XmlPullParser p, String pkg) {
        String rawName = attr(p, NS, "name", "");
        String simpleName = resolveClassName(rawName, pkg);
        ParsedManifest.ComponentEntry entry = new ParsedManifest.ComponentEntry(rawName, simpleName);
        entry.qualifiedClassName = resolveQualifiedClassName(rawName, pkg);
        entry.exported = Boolean.parseBoolean(attr(p, NS, "exported", "false"));
        entry.permission = attr(p, NS, "permission", "");
        return entry;
    }

    // ── Helper: read attribute safely ─────────────────────────────────────────

    private String attr(XmlPullParser p, String ns, String name, String def) {
        String val = (ns != null)
                ? p.getAttributeValue(ns, name)
                : p.getAttributeValue(null, name);
        return (val != null && !val.isEmpty()) ? val : def;
    }

    // ── Resolve class name relative to manifest package ───────────────────────

    /**
     * ".MainActivity"              → "MainActivity"
     * "com.example.MainActivity"   → "MainActivity"
     * "MainActivity"               → "MainActivity"
     */
    private String resolveClassName(String rawName, String pkg) {
        if (rawName.startsWith(".")) {
            // Relative name: ".MainActivity" → pkg + ".MainActivity" → simple part
            return rawName.substring(1); // strip dot, use simple name
        }
        if (rawName.startsWith(pkg)) {
            return rawName.substring(pkg.length() + 1); // strip "com.example."
        }
        // Already simple or unknown package
        int dot = rawName.lastIndexOf('.');
        return (dot >= 0) ? rawName.substring(dot + 1) : rawName;
    }

    private String resolveQualifiedClassName(String rawName, String pkg) {
        if (rawName == null || rawName.trim().isEmpty()) return "";
        String name = rawName.trim();
        if (name.startsWith(".")) return (pkg == null || pkg.isEmpty() ? "" : pkg) + name;
        if (name.indexOf('.') >= 0) return name;
        return (pkg == null || pkg.isEmpty()) ? name : pkg + "." + name;
    }

    // ── Strip @drawable/ / @mipmap/ prefix from icon name ────────────────────

    private String stripResPrefix(String raw) {
        // "@mipmap/ic_launcher" → "ic_launcher"
        // "@drawable/ic_logo"   → "ic_logo"
        int slash = raw.lastIndexOf('/');
        return (slash >= 0) ? raw.substring(slash + 1) : raw;
    }

    // ── Resolve @string/xxx references ───────────────────────────────────────

    private void resolveStringRefs(ParsedManifest out) {
        if (out.appName.startsWith("@string/")) {
            String key = out.appName.substring("@string/".length());
            String resolved = lookupString(key);
            if (resolved != null) out.appName = resolved;
        }
    }

    /**
     * Reads values/strings.xml and returns the value for the given key.
     * Returns null if not found.
     */
    private String lookupString(String key) {
        File stringsFile = new File(srcMain, "res/values/strings.xml");
        if (!stringsFile.exists()) return null;

        // Simple regex is acceptable here — strings.xml has a very predictable format
        try {
            byte[] bytes = readBytes(stringsFile);
            String content = new String(bytes, "UTF-8");
            Pattern p = Pattern.compile(
                    "<string\\s+name=\"" + Pattern.quote(key) + "\"[^>]*>([^<]+)</string>");
            Matcher m = p.matcher(content);
            if (m.find()) return m.group(1).trim();
        } catch (Exception e) {
            Log.w(TAG, "Could not read strings.xml", e);
        }
        return null;
    }

    private byte[] readBytes(File f) throws IOException {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int read = 0;
            while (read < buf.length) {
                int r = fis.read(buf, read, buf.length - read);
                if (r < 0) break;
                read += r;
            }
            return buf;
        }
    }
}
