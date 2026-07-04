package pro.sketchware.analysis.model;

public final class Issue {

    private final String id;            // stable id, e.g. "UNUSED_IMAGE", "DUP_ACTIVITY"
    private final String category;      // matches AnalysisCheck#category(), e.g. "resource", "manifest"
    private final Severity severity;
    private final String message;       // human-readable description
    private final String filePath;      // nullable — absolute path when the issue maps to one file
    private final String fixId;         // nullable — null means no Auto Fix available yet

    public Issue(String id, String category, Severity severity, String message,
                 String filePath, String fixId) {
        this.id = id;
        this.category = category;
        this.severity = severity;
        this.message = message;
        this.filePath = filePath;
        this.fixId = fixId;
    }
    public String getId() { return id; }
    public String getCategory() { return category; }
    public Severity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getFilePath() { return filePath; }
    public String getFixId() { return fixId; }
    public boolean hasAutoFix() { return fixId != null; }

    @Override
    public String toString() {
        return "[" + severity + "] " + id + " (" + category + "): " + message
                + (filePath != null ? " @ " + filePath : "");
    }
}
