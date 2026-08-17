package neo.sketchware.ai;

import java.util.ArrayList;
import java.util.List;

public class ErrorFixResult {
    public String summary;
    public String explanation;
    public String filePath;
    public List<Patch> patches = new ArrayList<>();
    public boolean needsManualEvent;
    public String manualEventHint;
    public String createActivityEventName;
    public String createViewEventTargetId;
    public String createViewEventName;
    public boolean patchable;

    public static class Patch {
        public String originalSnippet;
        public String fixedSnippet;
        public boolean applicable;
    }
}
