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

        // Tracks whether the current <activity> block has a MAIN+LAUNCHER filter
        boolean inActivity         = false;
        boolean inIntentFilter     = false;
        boolean hasMainAction      = false;
        boolean hasLauncherCategory = false;
        ParsedManifest.ActivityEntry currentActivity = null;

        int eventType = p.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {

            if (eventType == XmlPullParser.START_TAG) {
                String tag = p.getName();

                if ("manifest".equals(tag)) {
                    out.packageName = attr(p, null, "package", out.packageName);

                } else if ("application".equals(tag)) {
                    String rawLabel = attr(p, NS, "label", "");
                    if (!rawLabel.isEmpty()) out.appName = rawLabel; // may be @string/xxx
                    out.appTheme    = attr(p, NS, "theme", "");
                    out.iconResName = stripResPrefix(attr(p, NS, "icon", "@mipmap/ic_launcher"));

                } else if ("activity".equals(tag)) {
                    inActivity      = true;
                    hasMainAction      = false;
                    hasLauncherCategory = false;

                    String rawName   = attr(p, NS, "name", "");
                    String simpleName = resolveClassName(rawName, out.packageName);

                    currentActivity  = new ParsedManifest.ActivityEntry(rawName, simpleName);
                    currentActivity.orientation  = attr(p, NS, "screenOrientation", "");
                    currentActivity.softInputMode = attr(p, NS, "windowSoftInputMode", "");
                    currentActivity.theme        = attr(p, NS, "theme", "");

                } else if (inActivity && "intent-filter".equals(tag)) {
                    inIntentFilter = true;

                } else if (inIntentFilter && "action".equals(tag)) {
                    String name = attr(p, NS, "name", "");
                    if ("android.intent.action.MAIN".equals(name)) hasMainAction = true;

                } else if (inIntentFilter && "category".equals(tag)) {
                    String name = attr(p, NS, "name", "");
                    if ("android.intent.category.LAUNCHER".equals(name)) hasLauncherCategory = true;
                }

            } else if (eventType == XmlPullParser.END_TAG) {
                String tag = p.getName();

                if ("intent-filter".equals(tag)) {
                    inIntentFilter = false;

                } else if ("activity".equals(tag)) {
                    if (currentActivity != null) {
                        currentActivity.isLauncher = hasMainAction && hasLauncherCategory;
                        out.activities.add(currentActivity);
                    }
                    inActivity      = false;
                    currentActivity = null;
                }
            }

            eventType = p.next();
        }
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
