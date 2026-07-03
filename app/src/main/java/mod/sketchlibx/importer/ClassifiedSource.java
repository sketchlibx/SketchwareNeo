package mod.sketchlibx.importer;

import java.io.File;

/**
 * Represents one .java / .kt file after classification.
 * Immutable once built by SourceClassifier.
 */
public class ClassifiedSource {

    public enum Kind {
        /**
         * Extends AppCompatActivity / Activity / FragmentActivity / ComponentActivity.
         * → Becomes ProjectFileBean fileType=0 in Sketchware.
         */
        ACTIVITY,

        /**
         * Extends Fragment / DialogFragment / BottomSheetDialogFragment.
         * → Becomes ProjectFileBean fileType=3 (or 4 for BottomSheet) in Sketchware.
         */
        FRAGMENT,

        /**
         * Extends View / LinearLayout / RelativeLayout / FrameLayout / ConstraintLayout
         * (i.e. a fully custom widget class).
         * → Becomes ProjectFileBean fileType=1 in Sketchware.
         */
        CUSTOM_VIEW,

        /**
         * RecyclerView.Adapter / BaseAdapter / Service / BroadcastReceiver / etc.
         * → Copied as raw Java only.  No ProjectFileBean entry.
         */
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
