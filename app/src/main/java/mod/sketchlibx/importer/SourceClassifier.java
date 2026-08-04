package mod.sketchlibx.importer;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies every .java / .kt file under src/main/java and src/main/kotlin.
 *
 * Replacement for the original regex-on-whole-file-content approach.
 * Uses line-by-line scanning to find the class declaration, which avoids
 * false positives from comments, string literals, or multi-class files.
 *
 * Also derives:
 *   - The associated layout name (via setContentView / ViewBinding)
 *   - The Sketchware fileName (reverse of ProjectFileBean.getActivityName())
 */
public class SourceClassifier {

    private static final String TAG = "SourceClassifier";

    // ── Superclass keyword sets ───────────────────────────────────────────────

    private static final String[] ACTIVITY_SUPERS = {
            "AppCompatActivity", "Activity", "FragmentActivity",
            "ComponentActivity", "BaseActivity", "ActionBarActivity"
    };

    private static final String[] FRAGMENT_SUPERS = {
            "Fragment", "DialogFragment", "BottomSheetDialogFragment",
            "BaseFragment"
    };

    private static final String[] CUSTOM_VIEW_SUPERS = {
            "View", "LinearLayout", "RelativeLayout", "FrameLayout",
            "ConstraintLayout", "CoordinatorLayout", "CardView",
            "RecyclerView", "ViewGroup", "AbsoluteLayout"
    };

    private static final String[] SERVICE_SUPERS = {
            "Service", "IntentService", "JobIntentService", "JobService"
    };

    private static final String[] RECEIVER_SUPERS = {
            "BroadcastReceiver"
    };

    private static final String[] PROVIDER_SUPERS = {
            "ContentProvider"
    };

    private static final String[] APPLICATION_SUPERS = {
            "Application", "MultiDexApplication"
    };

    // ── Java class declaration pattern ────────────────────────────────────────
    // Handles: "public class Foo extends Bar" and "class Foo : Bar()"
    private static final Pattern JAVA_CLASS = Pattern.compile(
            "(?:public\\s+|private\\s+|protected\\s+|abstract\\s+|final\\s+)*" +
            "class\\s+(\\w+)\\s*(?:extends|:)\\s*([\\w.]+)");

    // Kotlin: "class Foo : Bar()" or "class Foo : Bar<T>()"
    private static final Pattern KT_CLASS = Pattern.compile(
            "(?:open\\s+|abstract\\s+|data\\s+)?class\\s+(\\w+)\\s*(?:[^:]*)?:\\s*([\\w.]+)");

    // setContentView(R.layout.xxx)
    private static final Pattern SET_CONTENT_VIEW = Pattern.compile(
            "setContentView\\s*\\(\\s*R\\.layout\\.(\\w+)\\s*\\)");

    // ActivityXxxBinding.inflate → activity_xxx
    private static final Pattern VIEW_BINDING = Pattern.compile(
            "(\\w+Binding)\\.(?:inflate|bind)");

    // R.layout.xxx used anywhere in file
    private static final Pattern R_LAYOUT = Pattern.compile(
            "R\\.layout\\.(\\w+)");

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Scans all .java and .kt files under the given source root and returns
     * a ClassifiedSource for each one.
     *
     * @param srcRoot  The java/ or kotlin/ folder under src/main.
     */
    public List<ClassifiedSource> classify(File srcRoot) {
        List<ClassifiedSource> results = new ArrayList<>();
        if (srcRoot == null || !srcRoot.exists()) return results;
        scanDir(srcRoot, results);
        return results;
    }

    // ── Directory walk ────────────────────────────────────────────────────────

    private void scanDir(File dir, List<ClassifiedSource> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(f, out);
            } else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                ClassifiedSource cs = classifyFile(f);
                if (cs != null) out.add(cs);
            }
        }
    }

    // ── Single file classification ────────────────────────────────────────────

    private ClassifiedSource classifyFile(File f) {
        boolean isKotlin = f.getName().endsWith(".kt");
        List<String> lines = readLines(f);
        if (lines == null) return null;

        String packageName    = "";
        String simpleClass    = null;
        String superClass     = null;
        String layoutFromSCV  = null;   // from setContentView(R.layout.xxx)
        String layoutFromVB   = null;   // from ViewBinding convention
        String layoutFromRLayout = null; // any R.layout.xxx reference

        for (String raw : lines) {
            String line = raw.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) {
                continue;
            }

            // Package declaration
            if (line.startsWith("package ") && packageName.isEmpty()) {
                packageName = line.replace("package", "").replace(";", "").trim();
                // Kotlin package can have no semicolon — that's fine
            }

            // Class declaration (only take the first one per file)
            if (simpleClass == null) {
                Matcher m = isKotlin ? KT_CLASS.matcher(line) : JAVA_CLASS.matcher(line);
                if (m.find()) {
                    simpleClass = m.group(1);
                    superClass  = simplifyClassName(m.group(2));
                }
            }

            // setContentView(R.layout.xxx)
            if (layoutFromSCV == null) {
                Matcher m = SET_CONTENT_VIEW.matcher(line);
                if (m.find()) layoutFromSCV = m.group(1);
            }

            // ViewBinding: XxxBinding.inflate → strip "Binding", convert CamelCase → snake
            if (layoutFromVB == null) {
                Matcher m = VIEW_BINDING.matcher(line);
                if (m.find()) {
                    String bindingName = m.group(1); // e.g. "ActivityMainBinding"
                    layoutFromVB = bindingNameToLayout(bindingName);
                }
            }

            // Any R.layout.xxx reference (fallback)
            if (layoutFromRLayout == null) {
                Matcher m = R_LAYOUT.matcher(line);
                if (m.find()) layoutFromRLayout = m.group(1);
            }
        }

        if (simpleClass == null) {
            Log.d(TAG, "No class declaration found in: " + f.getName());
            return null;
        }

        // ── Classify based on superclass ──────────────────────────────────────
        ClassifiedSource.Kind kind = classifyBySuperClass(superClass);

        ClassifiedSource cs = new ClassifiedSource(f, packageName, simpleClass, kind);

        // ── Assign associated layout ──────────────────────────────────────────
        // Priority: setContentView > ViewBinding > fallback by convention > any R.layout
        if (layoutFromSCV != null) {
            cs.associatedLayout = layoutFromSCV;
        } else if (layoutFromVB != null) {
            cs.associatedLayout = layoutFromVB;
        } else if (kind == ClassifiedSource.Kind.ACTIVITY) {
            // Convention fallback: MainActivity → activity_main or main
            cs.associatedLayout = conventionLayout(simpleClass);
        } else if (layoutFromRLayout != null) {
            cs.associatedLayout = layoutFromRLayout;
        }

        // ── Derive Sketchware fileName ────────────────────────────────────────
        cs.sketchwareFileName = classNameToSketchwareFileName(simpleClass);

        Log.d(TAG, String.format("Classified %s → kind=%s, layout=%s, swName=%s",
                simpleClass, kind, cs.associatedLayout, cs.sketchwareFileName));

        return cs;
    }

    // ── Superclass → Kind mapping ─────────────────────────────────────────────

    private ClassifiedSource.Kind classifyBySuperClass(String superClass) {
        if (superClass == null) return ClassifiedSource.Kind.OTHER;
        for (String s : ACTIVITY_SUPERS)    if (superClass.equals(s)) return ClassifiedSource.Kind.ACTIVITY;
        for (String s : FRAGMENT_SUPERS)    if (superClass.equals(s)) return ClassifiedSource.Kind.FRAGMENT;
        for (String s : CUSTOM_VIEW_SUPERS) if (superClass.equals(s)) return ClassifiedSource.Kind.CUSTOM_VIEW;
        for (String s : SERVICE_SUPERS)     if (superClass.equals(s)) return ClassifiedSource.Kind.SERVICE;
        for (String s : RECEIVER_SUPERS)    if (superClass.equals(s)) return ClassifiedSource.Kind.RECEIVER;
        for (String s : PROVIDER_SUPERS)    if (superClass.equals(s)) return ClassifiedSource.Kind.PROVIDER;
        for (String s : APPLICATION_SUPERS) if (superClass.equals(s)) return ClassifiedSource.Kind.APPLICATION;
        return ClassifiedSource.Kind.OTHER;
    }

    // ── className → Sketchware fileName ──────────────────────────────────────

    /**
     * Reverse of ProjectFileBean.getActivityName(fileName):
     *   "main"     → getActivityName → "MainActivity"
     *   "my_login" → getActivityName → "MyLoginActivity"
     *
     * So reverse:
     *   "MainActivity"    → strip "Activity" → "Main"   → snake_case → "main"
     *   "MyLoginActivity" → strip "Activity" → "MyLogin" → snake_case → "my_login"
     *   "HomeFragment"    → no suffix strip  → "HomeFragment" → "home_fragment"
     */
    public static String classNameToSketchwareFileName(String className) {
        // Strip known suffixes so round-trip works with getActivityName()
        String stripped = className;
        for (String suffix : new String[]{"Activity", "Fragment", "View", "Screen"}) {
            if (stripped.endsWith(suffix) && stripped.length() > suffix.length()) {
                stripped = stripped.substring(0, stripped.length() - suffix.length());
                break; // only strip one suffix
            }
        }

        // CamelCase → snake_case
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ── ViewBinding class name → layout name ─────────────────────────────────

    /**
     * "ActivityMainBinding"   → "activity_main"
     * "FragmentHomeBinding"   → "fragment_home"
     * "ItemUserBinding"       → "item_user"
     */
    private String bindingNameToLayout(String bindingClassName) {
        // Remove "Binding" suffix
        if (bindingClassName.endsWith("Binding")) {
            bindingClassName = bindingClassName.substring(
                    0, bindingClassName.length() - "Binding".length());
        }
        // CamelCase → snake_case
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bindingClassName.length(); i++) {
            char c = bindingClassName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ── Convention-based layout name derivation for Activities ───────────────

    /**
     * "MainActivity"      → try "activity_main" (common convention)
     * "SplashActivity"    → "activity_splash"
     */
    private String conventionLayout(String activityClassName) {
        String stripped = activityClassName;
        if (stripped.endsWith("Activity")) {
            stripped = stripped.substring(0, stripped.length() - 8);
        }
        // CamelCase → snake_case then prepend "activity_"
        StringBuilder sb = new StringBuilder("activity_");
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString(); // may not exist — caller must verify
    }

    // ── Strip fully-qualified class names to simple names ────────────────────

    private String simplifyClassName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        return (dot >= 0) ? name.substring(dot + 1) : name;
    }

    // ── Line reader ───────────────────────────────────────────────────────────

    private List<String> readLines(File f) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
            return lines;
        } catch (IOException e) {
            Log.e(TAG, "Failed to read " + f.getAbsolutePath(), e);
            return null;
        }
    }
}
