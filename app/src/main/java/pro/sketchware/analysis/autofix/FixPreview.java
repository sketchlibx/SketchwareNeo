package pro.sketchware.analysis.autofix;

import java.util.List;

public final class FixPreview {

    private final String description;
    private final List<String> affectedFiles;

    public FixPreview(String description, List<String> affectedFiles) {
        this.description = description;
        this.affectedFiles = affectedFiles;
    }

    public String getDescription() { return description; }
    public List<String> getAffectedFiles() { return affectedFiles; }
}
