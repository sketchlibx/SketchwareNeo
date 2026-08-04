package mod.sketchlibx.importer;

import java.io.File;

/**
 * Represents one .java / .kt file after classification.
 * Immutable once built by SourceClassifier.
 */
public class ClassifiedSource {

    public enum Kind {
        ACTIVITY,
        FRAGMENT,
        CUSTOM_VIEW,

        /** Extends Service / IntentService / JobIntentService / JobService. */
        SERVICE,

        /** Extends BroadcastReceiver. */
        RECEIVER,

        /** Extends ContentProvider. */
        PROVIDER,

        /** Extends android.app.Application. */
        APPLICATION,

        /** RecyclerView.Adapter / BaseAdapter / models / utility classes / etc. */
        OTHER
    }

    /** The physical source file on disk (inside the extracted ZIP temp dir). */
    public final File file;

    /** Package declaration from the source file (may be empty if default package). */
    public final String packageName;

    /** Simple class name, e.g. "MainActivity" or "HomeFragment". */
    public final String simpleClassName;

    /** Classification result. */
    public final Kind kind;

    /**
     * Name of the layout file (without .xml extension) that this class uses.
     * Determined by:
     *   1. setContentView(R.layout.xxx)  → xxx
     *   2. ActivityXxxBinding.inflate()  → activity_xxx  (ViewBinding convention)
     *   3. null if neither found
     */
    public String associatedLayout;

    /**
     * Sketchware fileName for this screen — derived from simpleClassName.
     * Follows ProjectFileBean.getActivityName() reverse algorithm:
     *   "MainActivity"      → "main"
     *   "MyLoginActivity"   → "my_login"
     *   "HomeFragment"      → "home_fragment"
     *
     * Populated by SourceClassifier after classification.
     */
    public String sketchwareFileName;

    /**
     * Sketchware fileType integer matching the Kind:
     *   ACTIVITY    → 0
     *   CUSTOM_VIEW → 1
     *   FRAGMENT    → 3
     *   OTHER       → -1 (do not register)
     */
    public int sketchwareFileType;

    public ClassifiedSource(
            File file,
            String packageName,
            String simpleClassName,
            Kind kind) {
        this.file            = file;
        this.packageName     = packageName;
        this.simpleClassName = simpleClassName;
        this.kind            = kind;

        // Derive Sketchware fileType from Kind
        switch (kind) {
            case ACTIVITY:    this.sketchwareFileType = 0; break;
            case CUSTOM_VIEW: this.sketchwareFileType = 1; break;
            case FRAGMENT:    this.sketchwareFileType = 3; break;
            default:          this.sketchwareFileType = -1; break;
        }
    }

    /** True if this source file should be registered as a Sketchware screen. */
    public boolean isSketchwareScreen() {
        return sketchwareFileType >= 0;
    }
}
